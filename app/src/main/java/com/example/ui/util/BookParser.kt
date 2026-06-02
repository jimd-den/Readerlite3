package com.example.ui.util

import android.content.Context
import android.util.Log
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

object BookParser {
    private const val TAG = "BookParser"

    data class TocItem(
        val title: String,
        val zipPath: String,
        val fragment: String,
        val originalHref: String,
        val isSubchapter: Boolean = false,
        val parentTitle: String? = null
    )

    private fun getAbsoluteZipPath(relativeHref: String, sourceFilePath: String): String {
        val folder = if (sourceFilePath.contains("/")) sourceFilePath.substringBeforeLast("/") + "/" else ""
        val combinedAndNormalized = (folder + relativeHref).lowercase()
            .replace('\\', '/').replace("//", "/").trimStart('/')
        return combinedAndNormalized.substringBefore("#")
    }

    private fun getFragment(href: String): String {
        return href.substringAfter("#", "")
    }

    private fun cleanForMatch(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun parseEpub3Nav(navXml: String, navPath: String): List<TocItem> {
        val items = mutableListOf<TocItem>()
        
        class NavPointNode(
            var title: String = "",
            var href: String = "",
            val children: MutableList<NavPointNode> = mutableListOf(),
            var parent: NavPointNode? = null
        )

        val rootNode = NavPointNode()
        var contentToParse = navXml
        
        try {
            val navRegex = Regex("<nav[^>]*epub:type=[\"']toc[\"'][^>]*>(.*?)</nav>", RegexOption.DOT_MATCHES_ALL)
            var navMatch = navRegex.find(navXml)
            if (navMatch == null) {
                val looseNavRegex = Regex("<nav[^>]*>(.*?)</nav>", RegexOption.DOT_MATCHES_ALL)
                navMatch = looseNavRegex.find(navXml)
            }
            contentToParse = navMatch?.value ?: navXml

            val parser = Xml.newPullParser()
            parser.setInput(contentToParse.reader())
            var eventType = parser.eventType

            var currentOlDepth = 0
            val activeNodesByDepth = Array<NavPointNode?>(32) { null }
            var inAnchorText = false
            val textBuilder = StringBuilder()
            var currentHref = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val rawName = parser.name
                val name = rawName?.lowercase()
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "ol") {
                            currentOlDepth++
                        } else if (name == "li") {
                            val parentNode = if (currentOlDepth <= 1) {
                                rootNode
                            } else {
                                if (currentOlDepth - 1 < 32) {
                                    activeNodesByDepth[currentOlDepth - 1] ?: rootNode
                                } else {
                                    rootNode
                                }
                            }
                            val newNode = NavPointNode(parent = parentNode)
                            parentNode.children.add(newNode)
                            if (currentOlDepth < 32) {
                                activeNodesByDepth[currentOlDepth] = newNode
                            }
                        } else if (name == "a") {
                            inAnchorText = true
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
                        if (inAnchorText) {
                            textBuilder.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "ol") {
                            currentOlDepth = java.lang.Math.max(0, currentOlDepth - 1)
                        } else if (name == "a") {
                            inAnchorText = false
                            if (currentOlDepth < 32) {
                                val currentNode = activeNodesByDepth[currentOlDepth]
                                if (currentNode != null) {
                                    currentNode.title = textBuilder.toString().trim()
                                    currentNode.href = currentHref
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            fun traverse(node: NavPointNode, depth: Int, parentTitle: String?) {
                if (node != rootNode) {
                    val title = node.title
                    val src = node.href
                    if (title.isNotEmpty()) {
                        val zipPath = getAbsoluteZipPath(src, navPath)
                        val fragment = getFragment(src)
                        val isSubch = depth > 0
                        items.add(TocItem(title, zipPath, fragment, src, isSubch, parentTitle))
                    }
                }
                for (child in node.children) {
                    traverse(child, depth + if (node == rootNode) 0 else 1, if (node == rootNode) null else node.title)
                }
            }

            traverse(rootNode, 0, null)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing EPUB 3 Nav file", e)
        }

        if (items.isEmpty()) {
            try {
                val aTagRegex = Regex("<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                val matches = aTagRegex.findAll(contentToParse)
                for (match in matches) {
                    val href = match.groups[1]?.value ?: continue
                    val rawTitle = match.groups[2]?.value ?: ""
                    val cleanTitle = rawTitle.replace("<[^>]*>".toRegex(), "").trim()
                    
                    val zipPath = getAbsoluteZipPath(href, navPath)
                    val fragment = getFragment(href)
                    items.add(TocItem(cleanTitle, zipPath, fragment, href))
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error in loose EPUB3 nav parsing fallback", ex)
            }
        }

        return items
    }

    private fun parseEpub2Ncx(ncxXml: String, ncxPath: String): List<TocItem> {
        val items = mutableListOf<TocItem>()
        
        class NavPointNode(
            var id: String = "",
            var title: String = "",
            var href: String = "",
            val children: MutableList<NavPointNode> = mutableListOf(),
            var parent: NavPointNode? = null
        )

        val rootNode = NavPointNode(id = "root")
        var currentNode: NavPointNode = rootNode

        try {
            val parser = Xml.newPullParser()
            parser.setInput(ncxXml.reader())
            var eventType = parser.eventType
            
            var inLabelText = false
            val textBuilder = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val rawName = parser.name
                val name = rawName?.lowercase()
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "navpoint") {
                            var navId = ""
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "id") {
                                    navId = parser.getAttributeValue(i) ?: ""
                                    break
                                }
                            }
                            val newNode = NavPointNode(id = navId, parent = currentNode)
                            currentNode.children.add(newNode)
                            currentNode = newNode
                        } else if (name == "text") {
                            inLabelText = true
                            textBuilder.setLength(0)
                        } else if (name == "content") {
                            var srcVal = ""
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "src") {
                                    srcVal = parser.getAttributeValue(i) ?: ""
                                    break
                                }
                            }
                            currentNode.href = srcVal
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inLabelText) {
                            textBuilder.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "text") {
                            inLabelText = false
                            currentNode.title = textBuilder.toString().trim()
                        } else if (name == "navpoint") {
                            currentNode = currentNode.parent ?: rootNode
                        }
                    }
                }
                eventType = parser.next()
            }
            
            fun traverse(node: NavPointNode, depth: Int, parentTitle: String?) {
                if (node != rootNode) {
                    val title = node.title
                    val src = node.href
                    if (title.isNotEmpty()) {
                        val zipPath = getAbsoluteZipPath(src, ncxPath)
                        val fragment = getFragment(src)
                        val isSubch = depth > 0
                        items.add(TocItem(title, zipPath, fragment, src, isSubch, parentTitle))
                    }
                }
                for (child in node.children) {
                    traverse(child, depth + if (node == rootNode) 0 else 1, if (node == rootNode) null else node.title)
                }
            }
            
            traverse(rootNode, 0, null)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing NCX", e)
        }
        return items
    }

    data class ParsedChapter(
        val title: String,
        val isSubchapter: Boolean,
        val parentTitle: String?
    )

    data class ParsedEpubResult(
        val title: String?,
        val author: String?,
        val chapters: List<ParsedChapter>,
        val sentences: List<com.example.data.db.SentenceEntity>
    )

    fun parseEpubStructured(inputStream: InputStream, bookId: String): ParsedEpubResult {
        val byteArr = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            e.printStackTrace()
            return ParsedEpubResult(null, null, emptyList(), emptyList())
        }

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
            return ParsedEpubResult(null, null, emptyList(), emptyList())
        }

        if (zipMap.isEmpty()) {
            return ParsedEpubResult(null, null, emptyList(), emptyList())
        }

        val normalizedZipMap = zipMap.mapKeys { (key, _) ->
            key.lowercase().replace('\\', '/').replace("//", "/").trimStart('/')
        }

        val opfEntry = normalizedZipMap.entries.find { it.key.endsWith(".opf") }
        
        var epubVersion = "2.0"
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()
        var navHref: String? = null
        var tocNcxHref: String? = null
        var opfFolderPrefix = ""
        var extractedTitle: String? = null
        var extractedAuthor: String? = null

        if (opfEntry != null) {
            val opfPath = opfEntry.key
            opfFolderPrefix = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
            val opfXmlStr = String(opfEntry.value, Charsets.UTF_8)

            try {
                val parser = Xml.newPullParser()
                parser.setInput(opfXmlStr.reader())
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val rawName = parser.name
                    val name = rawName?.lowercase()
                    if (eventType == XmlPullParser.START_TAG) {
                        if (name == "package" || name?.endsWith(":package") == true) {
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "version") {
                                    epubVersion = parser.getAttributeValue(i) ?: "2.0"
                                    break
                                }
                            }
                        } else if (name == "title" || name?.endsWith(":title") == true) {
                            extractedTitle = parser.nextText()
                        } else if (name == "creator" || name?.endsWith(":creator") == true) {
                            extractedAuthor = parser.nextText()
                        } else if (name == "item" || name?.endsWith(":item") == true) {
                            var id = ""
                            var href = ""
                            var properties = ""
                            for (i in 0 until parser.attributeCount) {
                                val attrName = parser.getAttributeName(i).lowercase()
                                if (attrName == "id") {
                                    id = parser.getAttributeValue(i) ?: ""
                                } else if (attrName == "href") {
                                    href = parser.getAttributeValue(i) ?: ""
                                } else if (attrName == "properties") {
                                    properties = parser.getAttributeValue(i) ?: ""
                                }
                            }
                            if (id.isNotEmpty() && href.isNotEmpty()) {
                                manifest[id.lowercase().trim()] = href.trim()
                                if (properties == "nav" || properties.contains("nav")) {
                                    navHref = href
                                }
                                if (href.endsWith(".ncx")) {
                                    tocNcxHref = href
                                }
                            }
                        } else if (name == "itemref" || name?.endsWith(":itemref") == true) {
                            var idref = ""
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "idref") {
                                    idref = parser.getAttributeValue(i) ?: ""
                                    break
                                }
                            }
                            if (idref.isNotEmpty()) {
                                spine.add(idref.lowercase().trim())
                            }
                        }
                    }
                    eventType = parser.next()
                }
                Log.d(TAG, "Parsed OPF xml in parseEpubStructured. Title: $extractedTitle, Creator: $extractedAuthor, Version: $epubVersion, Spine: ${spine.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing OPF metadata", e)
            }
        }

        val unifiedTocList = mutableListOf<TocItem>()

        var parsedViaEpub3Nav = false
        val resolvedNavHref = navHref ?: manifest.values.find { it.contains("nav.xhtml") || it.contains("toc.xhtml") }
        if (opfEntry != null && resolvedNavHref != null) {
            val navPathInZip = (opfFolderPrefix + resolvedNavHref).lowercase()
                .replace('\\', '/').replace("//", "/").trimStart('/')
            var navBytes = normalizedZipMap[navPathInZip]
            if (navBytes == null) {
                val navSuffix = if (resolvedNavHref.contains("/")) resolvedNavHref.substringAfterLast("/") else resolvedNavHref
                val navSuffixKey = normalizedZipMap.keys.find { it == navSuffix.lowercase() || it.endsWith("/" + navSuffix.lowercase()) }
                if (navSuffixKey != null) {
                    navBytes = normalizedZipMap[navSuffixKey]
                }
            }
            if (navBytes != null) {
                val navXmlStr = String(navBytes, Charsets.UTF_8)
                val items = parseEpub3Nav(navXmlStr, navPathInZip)
                if (items.isNotEmpty()) {
                    unifiedTocList.addAll(items)
                    parsedViaEpub3Nav = true
                    Log.d(TAG, "Loaded TOC from EPUB3 Nav document in parseEpubStructured: ${items.size} items")
                }
            }
        }

        if (opfEntry != null && !parsedViaEpub3Nav) {
            val resolvedNcxHref = tocNcxHref ?: manifest.values.find { it.endsWith(".ncx") }
            if (resolvedNcxHref != null) {
                val ncxPathInZip = (opfFolderPrefix + resolvedNcxHref).lowercase()
                    .replace('\\', '/').replace("//", "/").trimStart('/')
                var ncxBytes = normalizedZipMap[ncxPathInZip]
                if (ncxBytes == null) {
                    val ncxSuffix = if (resolvedNcxHref.contains("/")) resolvedNcxHref.substringAfterLast("/") else resolvedNcxHref
                    val ncxSuffixKey = normalizedZipMap.keys.find { it == ncxSuffix.lowercase() || it.endsWith("/" + ncxSuffix.lowercase()) }
                    if (ncxSuffixKey != null) {
                        ncxBytes = normalizedZipMap[ncxSuffixKey]
                    }
                }
                if (ncxBytes != null) {
                    val ncxXmlStr = String(ncxBytes, Charsets.UTF_8)
                    val items = parseEpub2Ncx(ncxXmlStr, ncxPathInZip)
                    if (items.isNotEmpty()) {
                        unifiedTocList.addAll(items)
                        Log.d(TAG, "Loaded TOC from EPUB2 NCX in parseEpubStructured: ${items.size} items")
                    }
                }
            }
        }

        if (unifiedTocList.isEmpty()) {
            val fallbackNcxEntry = normalizedZipMap.entries.find { it.key.endsWith(".ncx") }
            if (fallbackNcxEntry != null) {
                try {
                    val ncxXmlStr = String(fallbackNcxEntry.value, Charsets.UTF_8)
                    val items = parseEpub2Ncx(ncxXmlStr, fallbackNcxEntry.key)
                    unifiedTocList.addAll(items)
                    Log.d(TAG, "Loaded fallback NCX file from ${fallbackNcxEntry.key} in parseEpubStructured: ${items.size} items")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in nested NCX fallback parsing", e)
                }
            }
        }

        val chaptersList = mutableListOf<ParsedChapter>()
        val sentencesList = mutableListOf<com.example.data.db.SentenceEntity>()

        for (idref in spine) {
            val relativeHref = manifest[idref] ?: continue
            val entryPath = (opfFolderPrefix + relativeHref).lowercase()
                .replace('\\', '/').replace("//", "/").trimStart('/')
            
            val fileBytes = normalizedZipMap[entryPath]
            if (fileBytes != null) {
                val fileContent = String(fileBytes, Charsets.UTF_8)
                val fileTocItems = unifiedTocList.filter { it.zipPath == entryPath }
                
                val fileChapters = if (fileTocItems.isNotEmpty()) {
                    fileTocItems.map { item ->
                        ParsedChapter(item.title, item.isSubchapter, item.parentTitle)
                    }
                } else {
                    val fallbackTitle = entryPath.substringAfterLast("/").substringBeforeLast(".")
                        .replace("_", " ")
                        .replace("-", " ")
                        .trim()
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    listOf(ParsedChapter(fallbackTitle, false, null))
                }

                val allChaptersSet = fileChapters.filter { !it.isSubchapter }.map { cleanForMatch(it.title) }.toSet()
                val allSubheadingsSet = fileChapters.filter { it.isSubchapter }.map { cleanForMatch(it.title) }.toSet()

                val textOnly = stripHtml(fileContent, allChaptersSet, allSubheadingsSet)
                if (textOnly.isNotBlank()) {
                    val activeChapterIndexStart = chaptersList.size
                    chaptersList.addAll(fileChapters)

                    val lines = textOnly.split("\n")
                    var currentActiveSubIndex = 0
                    var sentenceCount = 0

                    for (line in lines) {
                        val lineTrimmed = line.trim()
                        if (lineTrimmed.isEmpty()) continue

                        val cleanLine = cleanForMatch(lineTrimmed)
                        var shifted = false
                        for (nextIdx in (currentActiveSubIndex + 1) until fileChapters.size) {
                            if (cleanForMatch(fileChapters[nextIdx].title) == cleanLine) {
                                currentActiveSubIndex = nextIdx
                                shifted = true
                                break
                            }
                        }

                        if (shifted || cleanLine == cleanForMatch(fileChapters[currentActiveSubIndex].title)) {
                            continue
                        }

                        val sentences = lineTrimmed.split(Regex("(?<=[.!?])\\s+"))
                        val activeGlChapterIndex = activeChapterIndexStart + currentActiveSubIndex
                        val sectionTitle = fileChapters[currentActiveSubIndex].title

                        for (sent in sentences) {
                            val sText = sent.trim()
                            if (sText.isNotEmpty()) {
                                sentencesList.add(
                                    com.example.data.db.SentenceEntity(
                                        id = "${bookId}_ch_${activeGlChapterIndex}_s_${sentenceCount}",
                                        bookId = bookId,
                                        chapterIndex = activeGlChapterIndex,
                                        sentenceIndex = sentenceCount,
                                        text = sText,
                                        sectionTitle = sectionTitle
                                    )
                                )
                                sentenceCount++
                            }
                        }
                    }
                }
            }
        }

        return ParsedEpubResult(
            title = extractedTitle,
            author = extractedAuthor,
            chapters = chaptersList,
            sentences = sentencesList
        )
    }

    private fun injectTocHeadings(htmlContent: String, chapters: List<TocItem>, subheadings: List<TocItem>): String {
        var updatedHtml = htmlContent
        
        // 1. Process Chapter TOC items
        for (item in chapters) {
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
                        "\n\n# ${item.title}\n\n" + match.value
                    }
                    injected = true
                } else {
                    // Fallback search
                    val lowercaseHtml = updatedHtml.lowercase()
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
                                    "\n\n# ${item.title}\n\n" +
                                    updatedHtml.substring(startTagPos)
                            injected = true
                        }
                    }
                }
            }
            if (!injected) {
                updatedHtml = "\n\n# ${item.title}\n\n" + updatedHtml
            }
        }

        // 2. Process Subheading TOC items
        for (item in subheadings) {
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
                        "\n\n## ${item.title}\n\n" + match.value
                    }
                    injected = true
                } else {
                    // Fallback search
                    val lowercaseHtml = updatedHtml.lowercase()
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
                                    "\n\n## ${item.title}\n\n" +
                                    updatedHtml.substring(startTagPos)
                            injected = true
                        }
                    }
                }
            }
            if (!injected) {
                updatedHtml = updatedHtml + "\n\n## ${item.title}\n\n"
            }
        }
        
        return updatedHtml
    }

    fun parseEpub(inputStream: InputStream): String {
        val byteArr = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error reading EPUB bytes"
        }

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
            return "Error reading EPUB contents"
        }

        if (zipMap.isEmpty()) {
            return "Empty EPUB file (no contents)"
        }

        // Normalize zip map keys to allow easy lookup
        val normalizedZipMap = zipMap.mapKeys { (key, _) ->
            key.lowercase().replace('\\', '/').replace("//", "/").trimStart('/')
        }

        // Find OPF file
        val opfEntry = normalizedZipMap.entries.find { it.key.endsWith(".opf") }
        
        // Parse OPF structure
        var epubVersion = "2.0"
        val manifest = mutableMapOf<String, String>() // id -> href xml ref
        val spine = mutableListOf<String>() // idref ordered list
        var navHref: String? = null
        var tocNcxHref: String? = null
        var opfFolderPrefix = ""

        if (opfEntry != null) {
            val opfPath = opfEntry.key
            opfFolderPrefix = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
            val opfXmlStr = String(opfEntry.value, Charsets.UTF_8)

            try {
                val parser = Xml.newPullParser()
                parser.setInput(opfXmlStr.reader())
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val rawName = parser.name
                    val name = rawName?.lowercase()
                    if (eventType == XmlPullParser.START_TAG) {
                        if (name == "package" || name?.endsWith(":package") == true) {
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "version") {
                                    epubVersion = parser.getAttributeValue(i) ?: "2.0"
                                    break
                                }
                            }
                        } else if (name == "item" || name?.endsWith(":item") == true) {
                            var id = ""
                            var href = ""
                            var properties = ""
                            for (i in 0 until parser.attributeCount) {
                                val attrName = parser.getAttributeName(i).lowercase()
                                if (attrName == "id") {
                                    id = parser.getAttributeValue(i) ?: ""
                                } else if (attrName == "href") {
                                    href = parser.getAttributeValue(i) ?: ""
                                } else if (attrName == "properties") {
                                    properties = parser.getAttributeValue(i) ?: ""
                                }
                            }
                            if (id.isNotEmpty() && href.isNotEmpty()) {
                                manifest[id.lowercase().trim()] = href.trim()
                                if (properties == "nav" || properties.contains("nav")) {
                                    navHref = href
                                }
                                if (href.endsWith(".ncx")) {
                                    tocNcxHref = href
                                }
                            }
                        } else if (name == "itemref" || name?.endsWith(":itemref") == true) {
                            var idref = ""
                            for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).lowercase() == "idref") {
                                    idref = parser.getAttributeValue(i) ?: ""
                                    break
                                }
                            }
                            if (idref.isNotEmpty()) {
                                spine.add(idref.lowercase().trim())
                            }
                        }
                    }
                    eventType = parser.next()
                }
                Log.d(TAG, "Parsed OPF xml. Version: $epubVersion, Spine elements: ${spine.size}, navHref: $navHref")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing OPF metadata", e)
            }
        }

        // Unified TOC Items List
        val unifiedTocList = mutableListOf<TocItem>()

        // 1. Try parsing EPUB 3 Navigation Document first
        var parsedViaEpub3Nav = false
        val resolvedNavHref = navHref ?: manifest.values.find { it.contains("nav.xhtml") || it.contains("toc.xhtml") }
        if (opfEntry != null && resolvedNavHref != null) {
            val navPathInZip = (opfFolderPrefix + resolvedNavHref).lowercase()
                .replace('\\', '/').replace("//", "/").trimStart('/')
            var navBytes = normalizedZipMap[navPathInZip]
            if (navBytes == null) {
                val navSuffix = if (resolvedNavHref.contains("/")) resolvedNavHref.substringAfterLast("/") else resolvedNavHref
                val navSuffixKey = normalizedZipMap.keys.find { it == navSuffix.lowercase() || it.endsWith("/" + navSuffix.lowercase()) }
                if (navSuffixKey != null) {
                    navBytes = normalizedZipMap[navSuffixKey]
                }
            }
            if (navBytes != null) {
                val navXmlStr = String(navBytes, Charsets.UTF_8)
                val items = parseEpub3Nav(navXmlStr, navPathInZip)
                if (items.isNotEmpty()) {
                    unifiedTocList.addAll(items)
                    parsedViaEpub3Nav = true
                    Log.d(TAG, "Loaded TOC from EPUB3 Nav document: ${items.size} items")
                }
            }
        }

        // 2. Fall back to EPUB 2 NCX file if EPUB 3 parsing wasn't successful
        if (opfEntry != null && !parsedViaEpub3Nav) {
            val resolvedNcxHref = tocNcxHref ?: manifest.values.find { it.endsWith(".ncx") }
            if (resolvedNcxHref != null) {
                val ncxPathInZip = (opfFolderPrefix + resolvedNcxHref).lowercase()
                    .replace('\\', '/').replace("//", "/").trimStart('/')
                var ncxBytes = normalizedZipMap[ncxPathInZip]
                if (ncxBytes == null) {
                    val ncxSuffix = if (resolvedNcxHref.contains("/")) resolvedNcxHref.substringAfterLast("/") else resolvedNcxHref
                    val ncxSuffixKey = normalizedZipMap.keys.find { it == ncxSuffix.lowercase() || it.endsWith("/" + ncxSuffix.lowercase()) }
                    if (ncxSuffixKey != null) {
                        ncxBytes = normalizedZipMap[ncxSuffixKey]
                    }
                }
                if (ncxBytes != null) {
                    val ncxXmlStr = String(ncxBytes, Charsets.UTF_8)
                    val items = parseEpub2Ncx(ncxXmlStr, ncxPathInZip)
                    if (items.isNotEmpty()) {
                        unifiedTocList.addAll(items)
                        Log.d(TAG, "Loaded TOC from EPUB2 NCX: ${items.size} items")
                    }
                }
            }
        }

        // Look for other NCX files if still empty
        if (unifiedTocList.isEmpty()) {
            val fallbackNcxEntry = normalizedZipMap.entries.find { it.key.endsWith(".ncx") }
            if (fallbackNcxEntry != null) {
                try {
                    val ncxXmlStr = String(fallbackNcxEntry.value, Charsets.UTF_8)
                    val items = parseEpub2Ncx(ncxXmlStr, fallbackNcxEntry.key)
                    unifiedTocList.addAll(items)
                    Log.d(TAG, "Loaded fallback NCX file from ${fallbackNcxEntry.key}: ${items.size} items")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in nested NCX fallback parsing", e)
                }
            }
        }

        // Classify TOC items into chapters and subheadings based on the navigation hierarchy depth (isSubchapter):
        val chaptersMap = mutableMapOf<String, MutableList<TocItem>>() // zipPath -> List of Chapter TOC items
        val subheadingsMap = mutableMapOf<String, MutableList<TocItem>>() // zipPath -> List of Subheading TOC items

        for (item in unifiedTocList) {
            val normalizedZipPath = item.zipPath.lowercase().trimStart('/')
            if (!item.isSubchapter) {
                chaptersMap.getOrPut(normalizedZipPath) { mutableListOf() }.add(item)
            } else {
                subheadingsMap.getOrPut(normalizedZipPath) { mutableListOf() }.add(item)
            }
        }

        val allChaptersSet = chaptersMap.values.flatten().map { it.title }.toSet()
        val allSubheadingsSet = subheadingsMap.values.flatten().map { it.title }.toSet()

        val contentBuilder = StringBuilder()

        // Extract each spine resource content file sequentially in linear reading sequence!
        if (spine.isNotEmpty()) {
            for (idref in spine) {
                val relativeHref = manifest[idref] ?: continue
                
                // Construct and normalize absolute path inside zip resource file system
                val entryPath = (opfFolderPrefix + relativeHref).lowercase()
                    .replace('\\', '/').replace("//", "/").trimStart('/')
                
                val fileBytes = normalizedZipMap[entryPath] ?: continue
                val fileContent = String(fileBytes, Charsets.UTF_8)

                val chaptersList = chaptersMap[entryPath] ?: emptyList()
                val subheadingsList = subheadingsMap[entryPath] ?: emptyList()

                // Inject anchors / formatting into HTML directly
                val modifiedHtml = injectTocHeadings(fileContent, chaptersList, subheadingsList)

                val textOnly = stripHtml(modifiedHtml, allChaptersSet, allSubheadingsSet)
                
                if (textOnly.isNotBlank()) {
                    val processedLines = mutableListOf<String>()
                    val lines = textOnly.split("\n")
                    
                    for (line in lines) {
                        val lineTrimmed = line.trim()
                        if (lineTrimmed.isEmpty()) {
                            processedLines.add("")
                            continue
                        }
                        
                        // Smart structure conversion based on matched TOC entries for this file
                        val cleanLine = cleanForMatch(lineTrimmed)
                        val matchedChapter = chaptersList.find { cleanForMatch(it.title) == cleanLine }
                        val matchedSubheading = subheadingsList.find { cleanForMatch(it.title) == cleanLine }
                        
                        when {
                            matchedChapter != null -> {
                                processedLines.add("\n# ${matchedChapter.title}\n")
                            }
                            matchedSubheading != null -> {
                                processedLines.add("\n## ${matchedSubheading.title}\n")
                            }
                            else -> {
                                processedLines.add(line)
                            }
                        }
                    }
                    
                    // Fallback heading block: ensure every file has a chapter header
                    val firstChapterTitle = chaptersList.firstOrNull()?.title ?: entryPath.substringAfterLast("/").substringBeforeLast(".")
                        .replace("_", " ")
                        .replace("-", " ")
                        .trim()
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                    // DEDUPLICATION: Remove any duplicate heading in the first 5 lines of the parsed lines
                    val normalizedFirstChapterTitle = cleanForMatch(firstChapterTitle)
                    var duplicateIndex = -1
                    if (normalizedFirstChapterTitle.length > 3) {
                        var nonSpaceCount = 0
                        for (i in processedLines.indices) {
                            val line = processedLines[i].trim()
                            if (line.isNotEmpty()) {
                                nonSpaceCount++
                                if (nonSpaceCount > 5) break
                                if (line.startsWith("# ") || line.startsWith("## ")) {
                                    continue
                                }
                                val cleanLine = cleanForMatch(line)
                                if (cleanLine == normalizedFirstChapterTitle || 
                                    (cleanLine.length > 5 && normalizedFirstChapterTitle.contains(cleanLine)) || 
                                    (normalizedFirstChapterTitle.length > 5 && cleanLine.contains(normalizedFirstChapterTitle))) {
                                    duplicateIndex = i
                                    break
                                }
                            }
                        }
                    }
                    if (duplicateIndex != -1) {
                        processedLines[duplicateIndex] = ""
                    }

                    var fileText = processedLines.joinToString("\n")
                    
                    val startsWithChapterHeader = fileText.trimStart().startsWith("# ")
                    if (!startsWithChapterHeader) {
                        fileText = "\n# $firstChapterTitle\n\n" + fileText
                    }

                    // Replace multiple blank lines with max 2
                    fileText = fileText.replace("\n\\s*\n\\s*\n+".toRegex(), "\n\n")

                    contentBuilder.append(fileText).append("\n")
                }
            }
        }

        // Alphabetical markup content fallback if OPF parsing failed or spine content is empty
        if (contentBuilder.isEmpty()) {
            Log.w(TAG, "Fallback to alphabetical physical parsing.")
            val textBlocks = normalizedZipMap.entries
                .filter { (key, _) ->
                    (key.endsWith(".xhtml") || key.endsWith(".html") || key.endsWith(".htm") || key.endsWith(".xml")) &&
                    !key.contains("container.xml") && !key.contains("toc.ncx") && !key.contains("content.opf") && !key.contains("style")
                }
                .sortedBy { it.key }

            for ((key, byteData) in textBlocks) {
                val fileContent = String(byteData, Charsets.UTF_8)
                val textOnly = stripHtml(fileContent, emptySet(), emptySet())
                if (textOnly.isNotBlank()) {
                    val cleanChapterTitle = key.substringAfterLast("/").substringBeforeLast(".")
                        .replace("_", " ")
                        .replace("-", " ")
                        .trim()
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                    val lines = textOnly.split("\n")
                    val processedLines = lines.toMutableList()

                    // Deduplicate the title if it repeats in the first 5 lines of plain text
                    val normalizedTitle = cleanForMatch(cleanChapterTitle)
                    var duplicateIndex = -1
                    if (normalizedTitle.length > 3) {
                        var nonSpaceCount = 0
                        for (i in processedLines.indices) {
                            val line = processedLines[i].trim()
                            if (line.isNotEmpty()) {
                                nonSpaceCount++
                                if (nonSpaceCount > 5) break
                                if (line.startsWith("# ") || line.startsWith("## ")) {
                                    continue
                                }
                                val cleanLine = cleanForMatch(line)
                                if (cleanLine == normalizedTitle || 
                                    (cleanLine.length > 5 && normalizedTitle.contains(cleanLine)) || 
                                    (normalizedTitle.length > 5 && cleanLine.contains(normalizedTitle))) {
                                    duplicateIndex = i
                                    break
                                }
                            }
                        }
                    }
                    if (duplicateIndex != -1) {
                        processedLines[duplicateIndex] = ""
                    }

                    var fileText = processedLines.joinToString("\n")

                    if (!fileText.trimStart().startsWith("# ") && !fileText.trimStart().startsWith("## ")) {
                        contentBuilder.append("\n# ").append(cleanChapterTitle).append("\n\n")
                    }
                    contentBuilder.append(fileText).append("\n")
                }
            }
        }

        return contentBuilder.toString()
    }

    fun parsePdf(context: Context, inputStream: InputStream): String {
        return try {
            PDFBoxResourceLoader.init(context)
            PDDocument.load(inputStream).use { document ->
                val pdfChapters = mutableSetOf<String>()
                val pdfSubheadings = mutableSetOf<String>()
                extractPdfOutline(document, pdfChapters, pdfSubheadings)
                Log.d(TAG, "Parsed PDF outline bookmarks. Chapters: ${pdfChapters.size}, Subheadings: ${pdfSubheadings.size}")

                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = document.numberOfPages
                val rawText = stripper.getText(document)

                if (pdfChapters.isEmpty() && pdfSubheadings.isEmpty()) {
                    rawText
                } else {
                    val lines = rawText.split("\n")
                    val formatted = StringBuilder()
                    val chNormalized = pdfChapters.map { it.lowercase().trim() }.toSet()
                    val subNormalized = pdfSubheadings.map { it.lowercase().trim() }.toSet()

                    for (line in lines) {
                        val trimmed = line.trim()
                        val lowerTrimmed = trimmed.lowercase()
                        if (trimmed.isNotEmpty()) {
                            when {
                                chNormalized.contains(lowerTrimmed) -> {
                                    formatted.append("\n# ").append(trimmed).append("\n")
                                }
                                subNormalized.contains(lowerTrimmed) -> {
                                    formatted.append("\n## ").append(trimmed).append("\n")
                                }
                                else -> {
                                    formatted.append(line).append("\n")
                                }
                            }
                        } else {
                            formatted.append("\n")
                        }
                    }
                    formatted.toString()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error extracting text from PDF: ${e.message}"
        }
    }

    private fun extractPdfOutline(document: PDDocument, pdfChapters: MutableSet<String>, pdfSubheadings: MutableSet<String>) {
        try {
            val outline = document.documentCatalog.documentOutline ?: return
            var currentItem = outline.firstChild
            while (currentItem != null) {
                val title = currentItem.title?.trim()
                if (!title.isNullOrEmpty()) {
                    pdfChapters.add(title)
                }
                
                // Nested level bookmarks as subheadings
                var childItem = currentItem.firstChild
                while (childItem != null) {
                    val subTitle = childItem.title?.trim()
                    if (!subTitle.isNullOrEmpty()) {
                        pdfSubheadings.add(subTitle)
                    }
                    childItem = childItem.nextSibling
                }
                currentItem = currentItem.nextSibling
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking PDF outline node hierarchy", e)
        }
    }

    private fun stripHtml(html: String, chapters: Set<String>, subheadings: Set<String>): String {
        // Pattern to match any header tags <h1...6> and parse them structurally
        val headingRegex = Regex("<(h[1-6])\\b[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
        var text = html
        
        text = headingRegex.replace(text) { matchResult ->
            val tag = matchResult.groups[1]?.value?.lowercase() ?: "h1"
            val body = matchResult.groups[2]?.value ?: ""
            val cleanBody = body.replace("<[^>]*>".toRegex(), "").trim()
            
            val level = when {
                chapters.any { it.equals(cleanBody, ignoreCase = true) } -> 1
                subheadings.any { it.equals(cleanBody, ignoreCase = true) } -> 2
                tag == "h1" || tag == "h2" -> 1
                else -> 2
            }
            
            if (level == 1) {
                "\n# $cleanBody\n"
            } else {
                "\n## $cleanBody\n"
            }
        }

        // Replace block-level tags with newlines
        text = text
            .replace("<p[^>]*>".toRegex(), "\n")
            .replace("</p>".toRegex(), "\n")
            .replace("<br[^>]*>".toRegex(), "\n")
            .replace("<div[^>]*>".toRegex(), "\n")
            .replace("</div>".toRegex(), "\n")
            .replace("<li[^>]*>".toRegex(), "\n• ")
            .replace("</li>".toRegex(), "\n")
        
        // Strip style sheets if present
        text = text.replace("<style[^>]*>[\\s\\S]*?</style>".toRegex(), "")
        // Strip head block
        text = text.replace("<head[^>]*>[\\s\\S]*?</head>".toRegex(), "")
        // Strip scripts
        text = text.replace("<script[^>]*>[\\s\\S]*?</script>".toRegex(), "")
        
        // Strip all remaining tags
        text = text.replace("<[^>]*>".toRegex(), "")
        
        // Decode common and numeric HTML entities
        text = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                @Suppress("DEPRECATION")
                android.text.Html.fromHtml(text).toString()
            }
        } catch (e: Exception) {
            text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&#8212;", "—")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&lsquo;", "‘")
                .replace("&rsquo;", "’")
        }
        
        // Clean up soft hyphens and other word-splitting artifacts that fragment individual words
        text = text
            .replace("\u00ad", "")  // Soft hyphen
            .replace("\u200b", "")  // Zero-width space
            .replace("\u200c", "")  // Zero-width non-joiner
            .replace("\u200d", "")  // Zero-width joiner
            .replace("&shy;", "")   // Entity soft-hyphen
            .replace("&#173;", "")  // Numeric soft-hyphen

        // Clean up redundant consecutive newlines keeping max 2
        text = text.replace("\n\\s*\n\\s*\n+".toRegex(), "\n\n")
        
        return text.trim()
    }
}
