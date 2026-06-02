package com.example.data.parser

import android.util.Log
import android.util.Xml
import com.example.domain.epub.*
import com.example.domain.model.*
import com.example.domain.service.TextSanitizer
import com.example.domain.service.TextSanitizerImpl
import com.example.domain.usecase.ClassifyContentUseCase
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder

class EpubValidatorImpl : EpubValidator {
    override fun validateContainer(zipFiles: Map<String, ByteArray>): List<ParsingMessage> {
        val messages = mutableListOf<ParsingMessage>()
        
        // 1. Check mimetype file
        val mimetypeBytes = zipFiles["mimetype"]
        if (mimetypeBytes == null) {
            messages.add(
                ParsingMessage(
                    ParsingMessage.Severity.ERROR,
                    "Missing root mimetype file.",
                    "CONTAINER_VALIDATION"
                )
            )
        } else {
            val mimetypeContent = String(mimetypeBytes, Charsets.UTF_8).trim()
            if (!mimetypeContent.startsWith("application/epub+zip")) {
                messages.add(
                    ParsingMessage(
                        ParsingMessage.Severity.ERROR,
                        "Mimetype is not application/epub+zip. Found: '$mimetypeContent'",
                        "CONTAINER_VALIDATION"
                    )
                )
            }
        }

        // 2. Check container.xml
        val containerBytes = zipFiles["meta-inf/container.xml"]
        if (containerBytes == null) {
            messages.add(
                ParsingMessage(
                    ParsingMessage.Severity.ERROR,
                    "Missing META-INF/container.xml in the package.",
                    "CONTAINER_VALIDATION"
                )
            )
        } else {
            val opfPath = findXmlPackagePath(containerBytes)
            if (opfPath.isNullOrBlank()) {
                messages.add(
                    ParsingMessage(
                        ParsingMessage.Severity.ERROR,
                        "container.xml does not resolve any rootfile with full-path.",
                        "CONTAINER_VALIDATION"
                    )
                )
            } else {
                val opfNormalized = opfPath.lowercase().replace('\\', '/').trimStart('/')
                if (!zipFiles.containsKey(opfNormalized)) {
                    messages.add(
                        ParsingMessage(
                            ParsingMessage.Severity.WARNING,
                            "OPF rootfile referenced in container.xml ('$opfPath') not found in archive directly. Attempting loose case-insensitive resolution.",
                            "CONTAINER_VALIDATION"
                        )
                    )
                }
            }
        }

        return messages
    }

    private fun findXmlPackagePath(containerBytes: ByteArray): String? {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(containerBytes).reader())
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
            // Regex fallback for container.xml
            val containerStr = String(containerBytes, Charsets.UTF_8)
            val match = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(containerStr)
            return match?.groupValues?.get(1)
        }
        return null
    }
}

class EpubPackageParserImpl(private val sanitizer: TextSanitizer = TextSanitizerImpl()) : EpubPackageParser {
    override fun parsePackage(
        zipFiles: Map<String, ByteArray>,
        packagePath: String,
        mode: ParsingMode
    ): Pair<PublicationManifest, List<ParsingMessage>> {
        val messages = mutableListOf<ParsingMessage>()
        val fileBytes = zipFiles[packagePath]
        
        if (fileBytes == null) {
            val errorMsg = "OPF package file not found at: '$packagePath'"
            messages.add(ParsingMessage(ParsingMessage.Severity.ERROR, errorMsg, "PACKAGE_PARSING"))
            throw IllegalArgumentException(errorMsg)
        }

        val opfXmlStr = String(fileBytes, Charsets.UTF_8)
        var title: String? = null
        var author: String? = null
        var rawVersionString = "2.0"
        val manifestItems = mutableMapOf<String, EpubManifestItem>()
        val spine = mutableListOf<EpubSpineItem>()
        var navDocumentHref: String? = null
        var ncxDocumentHref: String? = null

        try {
            val parser = Xml.newPullParser()
            parser.setInput(opfXmlStr.reader())
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name?.lowercase() ?: ""
                if (eventType == XmlPullParser.START_TAG) {
                    when {
                        name == "package" || name.endsWith(":package") -> {
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "version") {
                                    rawVersionString = parser.getAttributeValue(i) ?: "2.0"
                                }
                            }
                        }
                        name == "title" || name.endsWith(":title") -> {
                            title = parser.nextText()
                        }
                        name == "creator" || name.endsWith(":creator") -> {
                            author = parser.nextText()
                        }
                        name == "item" || name.endsWith(":item") -> {
                            var id = ""
                            var href = ""
                            var mediaType = ""
                            var properties = ""
                            for (i in 0 until parser.attributeCount) {
                                val attr = parser.getAttributeName(i).lowercase()
                                when (attr) {
                                    "id" -> id = parser.getAttributeValue(i) ?: ""
                                    "href" -> href = parser.getAttributeValue(i) ?: ""
                                    "media-type" -> mediaType = parser.getAttributeValue(i) ?: ""
                                    "properties" -> properties = parser.getAttributeValue(i) ?: ""
                                }
                            }
                            if (id.isNotEmpty() && href.isNotEmpty()) {
                                val cleanId = id.trim()
                                val cleanHref = href.trim()
                                val manifestItem = EpubManifestItem(cleanId, cleanHref, mediaType, properties)
                                manifestItems[cleanId] = manifestItem
                                
                                if (properties == "nav" || properties.contains("nav")) {
                                    navDocumentHref = cleanHref
                                }
                                if (mediaType == "application/x-dtbncx+xml" || cleanHref.endsWith(".ncx")) {
                                    ncxDocumentHref = cleanHref
                                }
                            }
                        }
                        name == "itemref" || name.endsWith(":itemref") -> {
                            var idref = ""
                            var linear = "yes"
                            var properties = ""
                            for (i in 0 until parser.attributeCount) {
                                val attr = parser.getAttributeName(i).lowercase()
                                when (attr) {
                                    "idref" -> idref = parser.getAttributeValue(i) ?: ""
                                    "linear" -> linear = parser.getAttributeValue(i) ?: "yes"
                                    "properties" -> properties = parser.getAttributeValue(i) ?: ""
                                }
                            }
                            if (idref.isNotEmpty()) {
                                spine.add(
                                    EpubSpineItem(
                                        idref = idref.trim(),
                                        href = "", // to resolve next
                                        isLinear = linear.lowercase() != "no",
                                        properties = properties
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            messages.add(
                ParsingMessage(
                    ParsingMessage.Severity.WARNING,
                    "XMLPullParser threw exception parsing OPF. Falling back to regular expression parsing. Details: ${e.message}",
                    "PACKAGE_PARSING"
                )
            )

            // Regular expression fallback
            val titleMatch = Regex("<(?:dc:)?title[^>]*>([^<]+)</(?:dc:)?title>", RegexOption.IGNORE_CASE).find(opfXmlStr)
            title = title ?: titleMatch?.groupValues?.get(1)?.trim()

            val creatorMatch = Regex("<(?:dc:)?creator[^>]*>([^<]+)</(?:dc:)?creator>", RegexOption.IGNORE_CASE).find(opfXmlStr)
            author = author ?: creatorMatch?.groupValues?.get(1)?.trim()

            val versionMatch = Regex("<package[^>]*version\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(opfXmlStr)
            rawVersionString = versionMatch?.groupValues?.get(1) ?: "2.0"

            val itemMatchRegex = Regex("<item\\s+([^>]+)>", RegexOption.IGNORE_CASE)
            itemMatchRegex.findAll(opfXmlStr).forEach { match ->
                val attributesStr = match.groupValues[1]
                val id = Regex("id\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attributesStr)?.groupValues?.get(1) ?: ""
                val href = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attributesStr)?.groupValues?.get(1) ?: ""
                val mediaType = Regex("media-type\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attributesStr)?.groupValues?.get(1) ?: ""
                val properties = Regex("properties\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attributesStr)?.groupValues?.get(1) ?: ""

                if (id.isNotEmpty() && href.isNotEmpty()) {
                    manifestItems[id] = EpubManifestItem(id, href, mediaType, properties)
                    if (properties.contains("nav")) navDocumentHref = href
                    if (mediaType.contains("ncx") || href.endsWith(".ncx")) ncxDocumentHref = href
                }
            }

            val itemrefMatchRegex = Regex("<itemref\\s+([^>]+)>", RegexOption.IGNORE_CASE)
            itemrefMatchRegex.findAll(opfXmlStr).forEach { match ->
                val attributesStr = match.groupValues[1]
                val idref = Regex("idref\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attributesStr)?.groupValues?.get(1) ?: ""
                val linear = Regex("linear\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attributesStr)?.groupValues?.get(1) ?: "yes"
                val properties = Regex("properties\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attributesStr)?.groupValues?.get(1) ?: ""

                if (idref.isNotEmpty()) {
                    spine.add(
                        EpubSpineItem(
                            idref = idref,
                            href = "",
                            isLinear = linear.lowercase() != "no",
                            properties = properties
                        )
                    )
                }
            }
        }

        // Map spine items' actual hrefs from manifest
        val resolvedSpine = spine.map { item ->
            val manifestItem = manifestItems[item.idref]
            if (manifestItem == null) {
                messages.add(
                    ParsingMessage(
                        ParsingMessage.Severity.WARNING,
                        "Spine itemref referencing missing manifest id: '${item.idref}'",
                        "PACKAGE_PARSING"
                    )
                )
                item.copy(href = "")
            } else {
                item.copy(href = manifestItem.href)
            }
        }.filter { it.href.isNotEmpty() }

        title = sanitizer.sanitize(title ?: "Untitled Document")
        author = sanitizer.sanitize(author ?: "Academic Author")

        val version = when {
            rawVersionString.startsWith("3.") -> EpubVersion.EPUB_3_0
            rawVersionString.startsWith("2.") -> EpubVersion.EPUB_2
            else -> EpubVersion.UNKNOWN
        }

        if (version == EpubVersion.UNKNOWN && mode == ParsingMode.STRICT_SPEC) {
            val errMsg = "Strict mode violation: Unsupported EPUB version '$rawVersionString'."
            messages.add(ParsingMessage(ParsingMessage.Severity.ERROR, errMsg, "PACKAGE_PARSING"))
            throw IllegalStateException(errMsg)
        }

        val manifest = PublicationManifest(
            version = version,
            rawVersionString = rawVersionString,
            title = title,
            author = author,
            manifestItems = manifestItems,
            spine = resolvedSpine,
            navDocumentHref = navDocumentHref,
            ncxDocumentHref = ncxDocumentHref
        )

        return manifest to messages
    }
}

class EpubNavigationParserImpl(
    private val sanitizer: TextSanitizer = TextSanitizerImpl()
) : EpubNavigationParser {

    data class RawNavNode(
        val title: String,
        val href: String,
        val depth: Int,
        val children: MutableList<RawNavNode> = mutableListOf()
    )

    override fun parseNavigation(
        zipFiles: Map<String, ByteArray>,
        manifest: PublicationManifest,
        mode: ParsingMode
    ): Pair<BookOutline, List<ParsingMessage>> {
        val messages = mutableListOf<ParsingMessage>()
        val rules = EpubSpecRegistry.getRulesForVersion(manifest.version)
        var outline: BookOutline? = null

        // 1. Try primary navigation source
        if (rules.primaryNavigationSource == NavigationSource.NAV_DOCUMENT && manifest.navDocumentHref != null) {
            try {
                outline = parseNavDocument(zipFiles, manifest.navDocumentHref, messages)
            } catch (e: Exception) {
                messages.add(
                    ParsingMessage(
                        ParsingMessage.Severity.WARNING,
                        "Primary EPUB3 nav parsing failed. Falling back to NCX. Details: ${e.message}",
                        "NAVIGATION_PARSING"
                    )
                )
            }
        }

        // 2. If PRIMARY failed or is NCX, try NCX-based navigation source
        if (outline == null && (rules.primaryNavigationSource == NavigationSource.NCX || rules.fallbackNavigationSource == NavigationSource.NCX) && manifest.ncxDocumentHref != null) {
            try {
                outline = parseNcxDocument(zipFiles, manifest.ncxDocumentHref, messages)
            } catch (e: Exception) {
                messages.add(
                    ParsingMessage(
                        ParsingMessage.Severity.WARNING,
                        "NCX navigation document parsing failed: ${e.message}",
                        "NAVIGATION_PARSING"
                    )
                )
            }
        }

        // 3. Fallback recovery checks in BEST_EFFORT mode
        if (outline == null && mode == ParsingMode.BEST_EFFORT) {
            messages.add(
                ParsingMessage(
                    ParsingMessage.Severity.WARNING,
                    "No explicit navigation document found. Applying 'TOC_FALLBACK_TO_FILE_LIST' recovery helper strategy.",
                    "NAVIGATION_PARSING"
                )
            )
            outline = buildFallbackOutline(manifest)
        }

        if (outline == null && mode == ParsingMode.STRICT_SPEC) {
            val errMsg = "Strict mode violation: No nav / NCX parsed successfully."
            messages.add(ParsingMessage(ParsingMessage.Severity.ERROR, errMsg, "NAVIGATION_PARSING"))
            throw IllegalStateException(errMsg)
        }

        return (outline ?: BookOutline(emptyList())) to messages
    }

    private class MutableNavNode(
        val id: String,
        var title: String = "",
        var href: String = "",
        val children: MutableList<MutableNavNode> = mutableListOf()
    )

    private fun mapToOutlineNode(node: MutableNavNode, parentId: String?, depth: Int): OutlineNode {
        val selfId = node.id
        val mappedChildren = node.children.map { mapToOutlineNode(it, selfId, depth + 1) }
        return OutlineNode(
            id = selfId,
            title = sanitizer.sanitize(node.title),
            href = node.href,
            type = if (depth == 0) OutlineNodeType.CHAPTER else OutlineNodeType.SUBSECTION,
            children = mappedChildren,
            parentId = parentId,
            depth = depth
        )
    }

    private fun parseNavDocument(
        zipFiles: Map<String, ByteArray>,
        navHref: String,
        messages: List<ParsingMessage>
    ): BookOutline {
        val decodedNavHref = safeUrlDecode(navHref)
        // Locate in zip Map (case-insensitive find as fallback helper)
        val entryKey = findCaseInsensitiveFileKey(zipFiles, decodedNavHref)
            ?: throw java.io.FileNotFoundException("Could not find Nav Doc: $navHref")
        
        val contentBytes = zipFiles[entryKey] ?: throw IllegalStateException("Could not read Nav Doc bytes")
        val navXmlStr = String(contentBytes, Charsets.UTF_8)
        
        // Find TOC nav section
        val navRegex = Regex("<nav[^>]*epub:type\\s*=\\s*[\"']toc[\"'][^>]*>(.*?)</nav>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        var navMatch = navRegex.find(navXmlStr)
        if (navMatch == null) {
            val looseNavRegex = Regex("<nav[^>]*>(.*?)</nav>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            navMatch = looseNavRegex.find(navXmlStr)
        }
        val contentToParse = if (navMatch != null) navMatch.groupValues[1] else navXmlStr

        val listItems = mutableListOf<OutlineNode>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(contentToParse.reader())
            var eventType = parser.eventType
            
            val stack = java.util.Stack<MutableNavNode>()
            val roots = mutableListOf<MutableNavNode>()
            var inAnchor = false
            val textBuilder = StringBuilder()
            var currentHref = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "li") {
                            val newLi = MutableNavNode(id = "nav_${System.currentTimeMillis()}_${(0..1000000).random()}")
                            if (stack.isNotEmpty()) {
                                stack.peek().children.add(newLi)
                            }
                            stack.push(newLi)
                        } else if (name == "a") {
                            inAnchor = true
                            textBuilder.setLength(0)
                            currentHref = ""
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "href") {
                                    currentHref = parser.getAttributeValue(i) ?: ""
                                    break
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inAnchor) {
                            textBuilder.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "a") {
                            inAnchor = false
                            if (stack.isNotEmpty()) {
                                stack.peek().title = textBuilder.toString()
                                stack.peek().href = currentHref
                            }
                        } else if (name == "li") {
                            if (stack.isNotEmpty()) {
                                val popped = stack.pop()
                                if (stack.isEmpty()) {
                                    roots.add(popped)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            listItems.addAll(roots.map { mapToOutlineNode(it, null, 0) })
        } catch (e: Exception) {
            // Regex fallback for items in Nav Doc
            val aTagRegex = Regex("<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val matches = aTagRegex.findAll(contentToParse)
            matches.forEachIndexed { i, match ->
                val href = match.groupValues[1]
                val rawTitle = match.groupValues[2].replace("<[^>]*>".toRegex(), "").trim()
                listItems.add(
                    OutlineNode(
                        id = "regex_nav_$i",
                        title = sanitizer.sanitize(rawTitle),
                        href = href,
                        type = OutlineNodeType.CHAPTER,
                        depth = 0
                    )
                )
            }
        }

        return BookOutline(listItems)
    }

    private fun parseNcxDocument(
        zipFiles: Map<String, ByteArray>,
        ncxHref: String,
        messages: List<ParsingMessage>
    ): BookOutline {
        val decodedNcxHref = safeUrlDecode(ncxHref)
        val entryKey = findCaseInsensitiveFileKey(zipFiles, decodedNcxHref)
            ?: throw java.io.FileNotFoundException("Could not find NCX Doc: $ncxHref")

        val contentBytes = zipFiles[entryKey] ?: throw IllegalStateException("Could not read NCX Doc bytes")
        val ncxXmlStr = String(contentBytes, Charsets.UTF_8)

        val listItems = mutableListOf<OutlineNode>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ncxXmlStr.reader())
            var eventType = parser.eventType

            val stack = java.util.Stack<MutableNavNode>()
            val roots = mutableListOf<MutableNavNode>()
            var inLabel = false
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = name
                        if (name == "navpoint" || name.endsWith(":navpoint")) {
                            var id = ""
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "id") {
                                    id = parser.getAttributeValue(i) ?: ""
                                    break
                                }
                            }
                            val newPoint = MutableNavNode(id = id.ifEmpty { "ncx_${System.currentTimeMillis()}_${(0..1000000).random()}" })
                            if (stack.isNotEmpty()) {
                                stack.peek().children.add(newPoint)
                            }
                            stack.push(newPoint)
                        } else if (name == "navlabel" || name.endsWith(":navlabel")) {
                            inLabel = true
                        } else if (name == "content" || name.endsWith(":content")) {
                            var src = ""
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "src") {
                                    src = parser.getAttributeValue(i) ?: ""
                                    break
                                }
                            }
                            if (stack.isNotEmpty()) {
                                stack.peek().href = src
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty() && stack.isNotEmpty()) {
                            if (currentTag == "text" && inLabel) {
                                stack.peek().title = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "navpoint" || name.endsWith(":navpoint")) {
                            if (stack.isNotEmpty()) {
                                val completed = stack.pop()
                                if (stack.isEmpty()) {
                                    roots.add(completed)
                                }
                            }
                        } else if (name == "navlabel" || name.endsWith(":navlabel")) {
                            inLabel = false
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
            listItems.addAll(roots.map { mapToOutlineNode(it, null, 0) })
        } catch (e: Exception) {
            // Regex fallback for simple NCX parsing
            val contentRegex = Regex("<navPoint[^>]*id\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>\\s*<navLabel>\\s*<text>([^<]+)</text>\\s*</navLabel>\\s*<content\\s+src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            val matches = contentRegex.findAll(ncxXmlStr)
            matches.forEachIndexed { i, match ->
                val id = match.groupValues[1]
                val title = match.groupValues[2]
                val src = match.groupValues[3]
                listItems.add(
                    OutlineNode(
                        id = id.ifEmpty { "regex_ncx_$i" },
                        title = sanitizer.sanitize(title),
                        href = src,
                        type = OutlineNodeType.CHAPTER,
                        depth = 0
                    )
                )
            }
        }

        return BookOutline(listItems)
    }

    private fun buildFallbackOutline(manifest: PublicationManifest): BookOutline {
        val listItems = manifest.spine.mapIndexed { idx, item ->
            val fileName = item.href.substringAfterLast("/").substringBeforeLast(".")
            val title = fileName.replace("-", " ").replace("_", " ")
                .split(" ")
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

            OutlineNode(
                id = "fallback_ch_$idx",
                title = title,
                href = item.href,
                type = OutlineNodeType.CHAPTER,
                depth = 0
            )
        }
        return BookOutline(listItems)
    }

    private fun findCaseInsensitiveFileKey(zipFiles: Map<String, ByteArray>, path: String): String? {
        val lower = path.lowercase().replace('\\', '/').trimStart('/')
        return zipFiles.keys.find { it.lowercase().replace('\\', '/').trimStart('/') == lower }
    }
}

class EpubContentExtractorImpl(
    private val sanitizer: TextSanitizer = TextSanitizerImpl()
) : EpubContentExtractor {

    private val classifyContentUseCase = ClassifyContentUseCase()

    override fun extractContent(
        zipFiles: Map<String, ByteArray>,
        manifest: PublicationManifest,
        outline: BookOutline,
        mode: ParsingMode
    ): List<ExtractedSection> {
        val flatOutline = outline.flatten()
        val extracted = mutableListOf<ExtractedSection>()
        var counter = 0

        for (item in manifest.spine) {
            val decodedHref = safeUrlDecode(item.href)
            val pathInZip = findCaseInsensitiveFileKey(zipFiles, decodedHref) ?: continue
            val fileBytes = zipFiles[pathInZip] ?: continue
            val fileContent = String(fileBytes, Charsets.UTF_8)

            // See if any outline nodes match this zipPath or specific fragment
            val matchingNavNodes = flatOutline.filter { node ->
                val nodeCleanHref = safeUrlDecode(node.href).substringBefore("#")
                nodeCleanHref == decodedHref || item.href.contains(nodeCleanHref)
            }

            // Read clean actual XHTML title or layout header as fallback
            val cleanTitlePattern = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
            val h1TitlePattern = Regex("<h1[^>]*>([^<]+)</h1>", RegexOption.IGNORE_CASE)
            val h2TitlePattern = Regex("<h2[^>]*>([^<]+)</h2>", RegexOption.IGNORE_CASE)
            
            val matchedTitle = cleanTitlePattern.find(fileContent)?.groupValues?.get(1)?.trim()
                ?: h1TitlePattern.find(fileContent)?.groupValues?.get(1)?.trim()
                ?: h2TitlePattern.find(fileContent)?.groupValues?.get(1)?.trim()
                ?: item.href.substringAfterLast("/").substringBeforeLast(".")

            val sectionTitle = sanitizer.sanitize(matchedTitle)

            // Determine classification policy based on several combined signals
            val classificationResult = classifyContentUseCase.determineClassification(
                href = item.href,
                isLinear = item.isLinear,
                properties = manifest.manifestItems[item.idref]?.properties,
                title = sectionTitle,
                depth = matchingNavNodes.firstOrNull()?.depth ?: 0
            )
            val policy = classificationResult.first

            // Split fileContent into subchapters if Outline tree declared multiple anchors within this document
            val anchors = matchingNavNodes.map { getFragment(it.href) }.filter { it.isNotEmpty() }.distinct()

            if (anchors.size > 1) {
                // Cut fileContent up by fragments or preserve as distinct section documents
                // For simplicity, map each subsection anchor as an ExtractedSection
                anchors.forEachIndexed { sIdx, anchor ->
                    val segmentTitle = matchingNavNodes.find { getFragment(it.href) == anchor }?.title ?: sectionTitle
                    extracted.add(
                        ExtractedSection(
                            title = segmentTitle,
                            href = "${item.href}#$anchor",
                            contentHtml = selectContentByAnchor(fileContent, anchor),
                            classification = policy,
                            orderIndex = counter++
                        )
                    )
                }
            } else {
                extracted.add(
                    ExtractedSection(
                        title = matchingNavNodes.firstOrNull()?.title ?: sectionTitle,
                        href = item.href,
                        contentHtml = fileContent,
                        classification = policy,
                        orderIndex = counter++
                    )
                )
            }
        }
        return extracted
    }

    private fun findCaseInsensitiveFileKey(zipFiles: Map<String, ByteArray>, path: String): String? {
        val lower = path.lowercase().replace('\\', '/').trimStart('/')
        return zipFiles.keys.find { it.lowercase().replace('\\', '/').trimStart('/') == lower }
    }

    private fun selectContentByAnchor(html: String, anchor: String): String {
        // Safe regex extraction around anchor elements like <div id="anchor"> or <a name="anchor">
        val startRegex = Regex("<[^>]+(?:id|name)\\s*=\\s*[\"']$anchor[\"'][^>]*>", RegexOption.IGNORE_CASE)
        val match = startRegex.find(html) ?: return html
        return html.substring(match.range.first)
    }
}

// Utility methods shared with EpubExtractorImpl and refactored pipeline

fun safeUrlDecode(url: String): String {
    return try {
        URLDecoder.decode(url, "UTF-8")
    } catch (e: Exception) {
        url
    }
}

fun getFragment(href: String): String {
    return if (href.contains("#")) href.substringAfter("#") else ""
}
