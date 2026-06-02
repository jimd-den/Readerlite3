package com.example.domain.model

enum class EpubVersion {
    EPUB_2,
    EPUB_3_0,
    EPUB_3_1,
    EPUB_3_2,
    UNKNOWN
}

enum class ParsingMode {
    STRICT_SPEC,
    BEST_EFFORT
}

enum class OutlineNodeType {
    PART,
    CHAPTER,
    SECTION,
    SUBSECTION,
    FRONT_MATTER,
    BACK_MATTER,
    NAV_AUXILIARY,
    PAGE_LIST,
    UNKNOWN
}

enum class ClassificationPolicy {
    INCLUDE_IN_READING,
    INCLUDE_IN_OUTLINE_ONLY,
    EXCLUDE_FROM_MAIN_OUTLINE,
    AUXILIARY
}

data class OutlineNode(
    val id: String,
    val title: String,
    val href: String,
    val type: OutlineNodeType,
    val children: List<OutlineNode> = emptyList(),
    val parentId: String? = null,
    val depth: Int = 0,
    val isLinear: Boolean = true,
    val isBodyMatter: Boolean = true
)

data class BookOutline(
    val roots: List<OutlineNode>
) {
    fun flatten(): List<OutlineNode> {
        val list = mutableListOf<OutlineNode>()
        fun recurse(node: OutlineNode) {
            list.add(node)
            node.children.forEach { recurse(it) }
        }
        roots.forEach { recurse(it) }
        return list
    }
}

data class EpubSpineItem(
    val idref: String,
    val href: String,
    val isLinear: Boolean = true,
    val properties: String? = null
)

data class EpubManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: String? = null
)

data class PublicationManifest(
    val version: EpubVersion,
    val rawVersionString: String,
    val title: String?,
    val author: String?,
    val manifestItems: Map<String, EpubManifestItem>,
    val spine: List<EpubSpineItem>,
    val navDocumentHref: String?,
    val ncxDocumentHref: String?
)

data class ExtractedSection(
    val title: String,
    val href: String,
    val contentHtml: String,
    val classification: ClassificationPolicy,
    val orderIndex: Int
)

data class ParsingMessage(
    val severity: Severity,
    val message: String,
    val phase: String
) {
    enum class Severity {
        INFO, WARNING, ERROR
    }
}

data class EpubParsingReport(
    val isSuccess: Boolean,
    val version: EpubVersion,
    val messages: List<ParsingMessage> = emptyList(),
    val manifest: PublicationManifest?,
    val outline: BookOutline?,
    val extractedSections: List<ExtractedSection> = emptyList()
)
