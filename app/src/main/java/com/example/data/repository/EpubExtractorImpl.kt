package com.example.data.repository

import android.util.Log
import android.util.Xml
import com.example.domain.epub.*
import com.example.domain.model.*
import com.example.domain.repository.EpubExtractor
import com.example.domain.service.TextSanitizer
import com.example.domain.service.TextSanitizerImpl
import com.example.domain.usecase.*
import com.example.data.parser.*
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

class EpubExtractorImpl : EpubExtractor {
    private val TAG = "EpubExtractorImpl"
    private val sanitizer: TextSanitizer = TextSanitizerImpl()

    // Clean Architecture Use Cases
    private val parseContainerUseCase = ParseContainerUseCase(EpubValidatorImpl())
    private val parsePackageUseCase = ParsePackageDocumentUseCase(EpubPackageParserImpl(sanitizer))
    private val buildNavigationUseCase = BuildNavigationOutlineUseCase(EpubNavigationParserImpl(sanitizer))
    private val classifyContentUseCase = ClassifyContentUseCase()
    private val extractReadableSectionsUseCase = ExtractReadableSectionsUseCase(EpubContentExtractorImpl(sanitizer))

    override fun parseEpub(inputStream: InputStream): EpubStructureDomainModel {
        val byteArr = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading EPUB input stream bytes", e)
            return emptyStructure()
        }

        // 1. In-memory ZIP archive resolution
        val zipMap = mutableMapOf<String, ByteArray>()
        try {
            val zipStream = ZipInputStream(byteArr.inputStream())
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    zipMap[entry.name] = zipStream.readBytes()
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading EPUB zip in memory", e)
            return emptyStructure()
        }

        if (zipMap.isEmpty()) return emptyStructure()

        val normalizedZipMap = zipMap.mapKeys { (key, _) ->
            key.lowercase().replace('\\', '/').replace("//", "/").trimStart('/')
        }

        // Configure Parsing Mode (By default, use BEST_EFFORT for resilient production-grade recovery)
        val parsingMode = ParsingMode.BEST_EFFORT

        // 2. Step 1: Validate container integrity
        val (_, containerMessages) = parseContainerUseCase.execute(normalizedZipMap)
        containerMessages.forEach { msg ->
            Log.d(TAG, "[Container Validation] ${msg.severity}: ${msg.message}")
        }

        // 3. Step 2: Extract Package Document Path (OPF)
        var opfPath = findXmlPackagePath(normalizedZipMap)
        if (opfPath != null) {
            opfPath = opfPath.lowercase().replace('\\', '/').replace("//", "/").trimStart('/')
        } else {
            // BEST_EFFORT Recovery Heuristic: search for any .opf extension
            val opfEntry = normalizedZipMap.entries.find { it.key.endsWith(".opf") }
            if (opfEntry != null && parsingMode == ParsingMode.BEST_EFFORT) {
                opfPath = opfEntry.key
                Log.w(TAG, "Recovery heuristic: container.xml missing/invalid. Resolving stray OPF: '$opfPath'")
            } else {
                Log.e(TAG, "Package validation failed: No root OPF package file found.")
                return emptyStructure()
            }
        }

        val opfFolderPrefix = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

        // 4. Step 3: Parse OPF Package metadata, manifest & spine reading sequence
        val manifest = try {
            val (pubManifest, packageMessages) = parsePackageUseCase.execute(normalizedZipMap, opfPath, parsingMode)
            packageMessages.forEach { msg ->
                Log.d(TAG, "[Package Parsing] ${msg.severity}: ${msg.message}")
            }
            pubManifest
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse publication manifest from OPF package", e)
            return emptyStructure()
        }

        // 5. Step 4: Parse Navigation outlines (EPUB 3 Nav vs EPUB 2 NCX or fallbacks)
        val resolvedNcxHref = manifest.ncxDocumentHref?.let {
            resolveRelativePath(opfFolderPrefix + safeUrlDecode(it))
        }
        val resolvedNavHref = manifest.navDocumentHref?.let {
            resolveRelativePath(opfFolderPrefix + safeUrlDecode(it))
        }
        val resolvedManifest = manifest.copy(
            ncxDocumentHref = resolvedNcxHref,
            navDocumentHref = resolvedNavHref
        )

        val outline = try {
            val (navOutline, navMessages) = buildNavigationUseCase.execute(normalizedZipMap, resolvedManifest, parsingMode)
            navMessages.forEach { msg ->
                Log.d(TAG, "[Navigation Parsing] ${msg.severity}: ${msg.message}")
            }
            navOutline
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse navigation structures", e)
            BookOutline(emptyList())
        }

        // 6. Map parsed hierarchical outline to Flat Chapters Sequence (for Room DB legacy schema compatibility)
        // We preserve full parenthood pointers, nesting depth, and classifications!
        val flatOutlineList = outline.flatten()
        val unifiedTocItems = flatOutlineList.map { node ->
            val decodedNodeHref = safeUrlDecode(node.href)
            val zipPathInZip = getAbsoluteZipPath(decodedNodeHref, opfFolderPrefix)
            val fragment = getFragment(decodedNodeHref)
            
            // Classification signal filtering
            val classificationResult = classifyContentUseCase.determineClassification(
                href = node.href,
                isLinear = true,
                properties = manifest.manifestItems[node.href.substringBefore("#")]?.properties,
                title = node.title,
                depth = node.depth
            )
            
            val isSub = classificationResult.second == OutlineNodeType.SUBSECTION || node.depth > 0

            TempTocItem(
                title = node.title,
                zipPath = zipPathInZip,
                fragment = fragment,
                originalHref = node.href,
                isSubchapter = isSub,
                parentTitle = node.parentId, // populated below with readable node title pairs if desired
                nestingLevel = node.depth
            )
        }

        // Resolve readable parentTitle headings
        val finalTocItems = unifiedTocItems.map { item ->
            val parentNode = flatOutlineList.find { it.id == item.parentTitle }
            item.copy(parentTitle = parentNode?.title)
        }

        val hasToc = finalTocItems.isNotEmpty()
        val chaptersList = if (hasToc) {
            finalTocItems.map { item ->
                ParsedChapterDomain(
                    title = item.title,
                    isSubchapter = item.isSubchapter,
                    parentTitle = item.parentTitle,
                    nestingLevel = item.nestingLevel
                )
            }.toMutableList()
        } else {
            mutableListOf()
        }

        val sentencesList = mutableListOf<ParsedSentenceDomain>()
        val sentenceCountMap = mutableMapOf<Int, Int>()
        var lastActiveChapterIndex = 0

        // 7. Extract readable text body files sequentially
        for (item in manifest.spine) {
            val decodedHref = safeUrlDecode(item.href)
            val entryPath = resolveRelativePath(opfFolderPrefix + decodedHref).lowercase()
            val fileBytes = normalizedZipMap[entryPath] ?: continue
            val fileContent = String(fileBytes, Charsets.UTF_8)

            val fileTocItemsWithIndex = if (hasToc) {
                finalTocItems.mapIndexed { idx, item -> idx to item }
                    .filter { (_, item) -> item.zipPath == entryPath }
            } else {
                emptyList()
            }

            // Exclude auxiliary files (e.g. covers, nav index) from regular reading pages list
            val pageTitleHeuristic = entryPath.substringAfterLast("/").substringBeforeLast(".")
            val classificationResult = classifyContentUseCase.determineClassification(
                href = item.href,
                isLinear = item.isLinear,
                properties = manifest.manifestItems[item.idref]?.properties,
                title = pageTitleHeuristic,
                depth = 0
            )

            // If STRICT_SPEC says exclude, or policy is AUXILIARY, mark section appropriately (still parsed but flag/log)
            if (classificationResult.first == ClassificationPolicy.AUXILIARY) {
                Log.d(TAG, "Auxiliary content folder item skipped from primary reading flow to avoid pollution: '${item.href}'")
            }

            var fallbackTitle = pageTitleHeuristic
            val cleanTitlePattern = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
            val h1TitlePattern = Regex("<h1[^>]*>([^<]+)</h1>", RegexOption.IGNORE_CASE)
            val h2TitlePattern = Regex("<h2[^>]*>([^<]+)</h2>", RegexOption.IGNORE_CASE)

            val matchedTitle = cleanTitlePattern.find(fileContent)?.groupValues?.get(1)?.trim()
                ?: h1TitlePattern.find(fileContent)?.groupValues?.get(1)?.trim()
                ?: h2TitlePattern.find(fileContent)?.groupValues?.get(1)?.trim()

            val extractedFallback = if (matchedTitle != null) {
                sanitizer.sanitize(matchedTitle)
            } else null

            if (!extractedFallback.isNullOrBlank() &&
                !extractedFallback.lowercase().contains("untitled") &&
                !extractedFallback.lowercase().contains("temp") &&
                extractedFallback.length in 2..80) {
                fallbackTitle = extractedFallback
            } else {
                fallbackTitle = fallbackTitle
                    .replace("_", " ")
                    .replace("-", " ")
                    .trim()
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                if (fallbackTitle.startsWith("ch ", ignoreCase = true) || fallbackTitle.startsWith("ch0", ignoreCase = true) || fallbackTitle.startsWith("ch1", ignoreCase = true)) {
                    fallbackTitle = fallbackTitle.replace(Regex("^(?i)ch\\s*(\\d+)"), "Chapter $1")
                }
            }

            val fileChapters = if (hasToc) {
                fileTocItemsWithIndex.map { (_, item) ->
                    ParsedChapterDomain(item.title, item.isSubchapter, item.parentTitle, item.nestingLevel)
                }
            } else {
                val fbChapter = ParsedChapterDomain(fallbackTitle, false, null, 0)
                chaptersList.add(fbChapter)
                listOf(fbChapter)
            }

            val fallbackChapterIndex = if (hasToc) 0 else chaptersList.size - 1

            // Inject boundary anchors to identify outline transitions
            val modifiedHtml = injectChapterBoundaries(
                htmlContent = fileContent,
                fileTocItemsWithIndex = fileTocItemsWithIndex,
                fallbackTitle = fallbackTitle,
                fallbackChapterIndex = fallbackChapterIndex,
                hasToc = hasToc
            )

            val allChaptersSet = fileChapters.filter { !it.isSubchapter }.map { cleanForMatch(it.title) }.toSet()
            val allSubheadingsSet = fileChapters.filter { it.isSubchapter }.map { cleanForMatch(it.title) }.toSet()

            val textOnly = stripHtml(modifiedHtml, allChaptersSet, allSubheadingsSet)
            if (textOnly.isNotBlank()) {
                val lines = textOnly.split("\n")
                for (line in lines) {
                    val lineTrimmed = line.trim()
                    if (lineTrimmed.isEmpty()) continue

                    if (lineTrimmed.startsWith("[AISTUDIO_CH_BOUNDARY_") && lineTrimmed.endsWith("]")) {
                        val parsedGlobalIdx = lineTrimmed.removePrefix("[AISTUDIO_CH_BOUNDARY_")
                            .removeSuffix("]").toIntOrNull()
                        if (parsedGlobalIdx != null) {
                            lastActiveChapterIndex = parsedGlobalIdx
                        }
                        continue
                    }

                    val cleanLine = cleanForMatch(lineTrimmed)

                    val matchedIndex = if (hasToc) {
                        chaptersList.indexOfFirst { cleanForMatch(it.title) == cleanLine }
                    } else -1

                    if (matchedIndex != -1) {
                        lastActiveChapterIndex = matchedIndex
                        continue
                    }

                    val matchesAnyHeader = fileChapters.any { cleanForMatch(it.title) == cleanLine }
                    if (matchesAnyHeader) {
                        continue
                    }

                    val currentChapterTitle = if (lastActiveChapterIndex in chaptersList.indices) {
                        chaptersList[lastActiveChapterIndex].title
                    } else {
                        fallbackTitle
                    }

                    val activeGlChapterIndex = lastActiveChapterIndex
                    val sectionTitle = currentChapterTitle

                    val sentences = lineTrimmed.split(Regex("(?<=[.!?])\\s+"))
                    for (sent in sentences) {
                        val sText = sanitizer.sanitize(sent)
                        if (sText.isNotEmpty()) {
                            val currentCount = sentenceCountMap[activeGlChapterIndex] ?: 0
                            sentencesList.add(
                                ParsedSentenceDomain(
                                    chapterIndex = activeGlChapterIndex,
                                    sentenceIndex = currentCount,
                                    text = sText,
                                    sectionTitle = sectionTitle
                                )
                            )
                            sentenceCountMap[activeGlChapterIndex] = currentCount + 1
                        }
                    }
                }
            }
        }

        return EpubStructureDomainModel(
            title = manifest.title,
            author = manifest.author,
            chapters = chaptersList,
            sentences = sentencesList
        )
    }

    private fun findXmlPackagePath(zipMap: Map<String, ByteArray>): String? {
        val containerFile = zipMap["meta-inf/container.xml"] ?: return null
        try {
            val parser = Xml.newPullParser()
            parser.setInput(String(containerFile, Charsets.UTF_8).reader())
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name?.lowercase() == "rootfile") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i).lowercase() == "full-path") {
                            return parser.getAttributeValue(i)?.trim()
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            val containerStr = String(containerFile, Charsets.UTF_8)
            val match = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(containerStr)
            return match?.groupValues?.get(1)
        }
        return null
    }

    override fun parseTxtOrPdf(content: String, title: String): EpubStructureDomainModel {
        val chaptersList = mutableListOf<ParsedChapterDomain>()
        val sentencesList = mutableListOf<ParsedSentenceDomain>()

        val lines = content.split("\n")
        var currentChapterIndex = -1
        var currentChapterTitle = "Introduction"
        var currentSectionTitle = "Overview"
        var sentenceCount = 0

        fun saveCurrentChapter() {
            if (currentChapterIndex >= 0) {
                chaptersList.add(
                    ParsedChapterDomain(
                        title = currentChapterTitle,
                        isSubchapter = false,
                        parentTitle = null,
                        nestingLevel = 0
                    )
                )
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val isChapterLine = (trimmed.startsWith("# ") && trimmed.length < 150) || 
                                (trimmed.startsWith("Chapter", ignoreCase = true) && trimmed.contains(":") && trimmed.length < 100) || 
                                (trimmed.matches(Regex("^(CHAPTER|Chapter)\\s+\\d+.*")) && trimmed.length < 100)

            if (isChapterLine) {
                saveCurrentChapter()
                currentChapterIndex++
                currentChapterTitle = trimmed.removePrefix("# ").trim()
                currentSectionTitle = "Overview"
                sentenceCount = 0
                continue
            }

            val hasMarkdownHeaders = content.contains("#") || content.contains("##")
            val isSubheading = if (hasMarkdownHeaders) {
                (trimmed.startsWith("## ") || trimmed.startsWith("### ")) && trimmed.length < 150
            } else {
                trimmed.startsWith("## ") || trimmed.startsWith("### ") || (trimmed.length < 50 && !trimmed.endsWith(".") && !trimmed.endsWith("?"))
            }

            if (isSubheading) {
                currentSectionTitle = trimmed.removePrefix("## ").removePrefix("### ").trim()
                continue
            }

            val sentences = trimmed.split(Regex("(?<=[.!?])\\s+"))
            for (sent in sentences) {
                val sText = sanitizer.sanitize(sent)
                if (sText.isNotEmpty()) {
                    if (currentChapterIndex == -1) {
                        currentChapterIndex = 0
                        currentChapterTitle = "Introduction"
                    }
                    sentencesList.add(
                        ParsedSentenceDomain(
                            chapterIndex = currentChapterIndex,
                            sentenceIndex = sentenceCount,
                            text = sText,
                            sectionTitle = currentSectionTitle
                        )
                    )
                    sentenceCount++
                }
            }
        }
        
        saveCurrentChapter()

        if (chaptersList.isEmpty()) {
            chaptersList.add(
                ParsedChapterDomain(
                    title = "Main Text",
                    isSubchapter = false,
                    parentTitle = null,
                    nestingLevel = 0
                )
            )
        }

        return EpubStructureDomainModel(
            title = title,
            author = "Academic Author",
            chapters = chaptersList,
            sentences = sentencesList
        )
    }

    private fun resolveRelativePath(path: String): String {
        val normalized = path.replace('\\', '/').replace("//", "/").trimStart('/')
        val segments = normalized.split('/')
        val resolvedSegments = mutableListOf<String>()
        for (segment in segments) {
            when (segment) {
                "" -> {} 
                "." -> {} 
                ".." -> {
                    if (resolvedSegments.isNotEmpty()) {
                        resolvedSegments.removeAt(resolvedSegments.size - 1)
                    }
                }
                else -> {
                    resolvedSegments.add(segment)
                }
            }
        }
        return resolvedSegments.joinToString("/")
    }

    private fun getAbsoluteZipPath(relativeHref: String, sourceFolder: String): String {
        val decodedHref = safeUrlDecode(relativeHref).substringBefore("#")
        return resolveRelativePath(sourceFolder + decodedHref).lowercase()
    }

    private fun injectChapterBoundaries(
        htmlContent: String,
        fileTocItemsWithIndex: List<Pair<Int, TempTocItem>>,
        fallbackTitle: String,
        fallbackChapterIndex: Int,
        hasToc: Boolean
    ): String {
        var updatedHtml = htmlContent
        
        if (!hasToc) {
            return "\n\n[AISTUDIO_CH_BOUNDARY_${fallbackChapterIndex}]\n\n" + updatedHtml
        }
        
        if (fileTocItemsWithIndex.isEmpty()) {
            return updatedHtml
        }
        
        var cachedLowercase: String? = null
        
        for (i in fileTocItemsWithIndex.indices) {
            val (globalIdx, item) = fileTocItemsWithIndex[i]
            val boundaryMarker = "[AISTUDIO_CH_BOUNDARY_${globalIdx}]"
            
            val anchor = item.fragment.trim()
            var injected = false
            
            if (anchor.isNotEmpty()) {
                val escapedAnchor = Regex.escape(anchor)
                val idPattern = Regex(
                    "(<[^>]+?\\b(?:id|name|xml:id)\\s*=\\s*[\"']?${escapedAnchor}[\"']?[^>]*>)",
                    RegexOption.IGNORE_CASE
                )
                
                if (idPattern.containsMatchIn(updatedHtml)) {
                    updatedHtml = idPattern.replace(updatedHtml) { match ->
                        "\n\n${boundaryMarker}\n\n" + match.value
                    }
                    injected = true
                    cachedLowercase = null
                } else {
                    if (cachedLowercase == null) {
                        cachedLowercase = updatedHtml.lowercase()
                    }
                    val lowercaseHtml = cachedLowercase
                    val targetAttrValueMarked = "=\"${anchor.lowercase()}\""
                    val targetAttrValueMarkedSingle = "='${anchor.lowercase()}'"
                    val targetAttrValueMarkedRaw = "=${anchor.lowercase()}"
                    
                    var index = lowercaseHtml.indexOf(targetAttrValueMarked)
                    if (index == -1) index = lowercaseHtml.indexOf(targetAttrValueMarkedSingle)
                    if (index == -1) index = lowercaseHtml.indexOf(targetAttrValueMarkedRaw)
                    
                    if (index != -1) {
                        val startTagPos = updatedHtml.lastIndexOf('<', index)
                        if (startTagPos != -1) {
                            updatedHtml = updatedHtml.substring(0, startTagPos) +
                                    "\n\n${boundaryMarker}\n\n" +
                                    updatedHtml.substring(startTagPos)
                            injected = true
                            cachedLowercase = null
                        }
                    }
                }
            }
            
            if (!injected && i == 0) {
                updatedHtml = "\n\n${boundaryMarker}\n\n" + updatedHtml
                cachedLowercase = null
            }
        }
        return updatedHtml
    }

    private fun cleanForMatch(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun stripHtml(html: String, chapters: Set<String>, subheadings: Set<String>): String {
        val cleanTxt = html.replace("<style[^>]*>.*?</style>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace("<script[^>]*>.*?</script>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace("<[^>]*>".toRegex(RegexOption.DOT_MATCHES_ALL), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
        
        val lines = cleanTxt.split("\n")
        val finalLines = mutableListOf<String>()
        for (l in lines) {
            val t = l.trim()
            if (t.isNotBlank()) {
                val matchKey = cleanForMatch(t)
                if (chapters.contains(matchKey)) {
                    finalLines.add("# $t")
                } else if (subheadings.contains(matchKey)) {
                    finalLines.add("## $t")
                } else {
                    finalLines.add(t)
                }
            }
        }
        return finalLines.joinToString("\n")
    }

    private fun emptyStructure() = EpubStructureDomainModel(null, null, emptyList(), emptyList())

    data class TempTocItem(
        val title: String,
        val zipPath: String,
        val fragment: String,
        val originalHref: String,
        val isSubchapter: Boolean = false,
        val parentTitle: String? = null,
        val nestingLevel: Int = 0
    )
}
