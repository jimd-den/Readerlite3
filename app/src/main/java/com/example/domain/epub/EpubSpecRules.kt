package com.example.domain.epub

import com.example.domain.model.EpubVersion

enum class NavigationSource {
    NCX,
    NAV_DOCUMENT,
    NONE
}

interface EpubSpecRules {
    val version: EpubVersion
    val requiredFiles: List<String>
    val primaryNavigationSource: NavigationSource
    val fallbackNavigationSource: NavigationSource
    val allowedRecoveryHeuristics: List<String>
    val frontMatterHandlingExpectations: List<String>
}

class Epub2Rules : EpubSpecRules {
    override val version = EpubVersion.EPUB_2
    override val requiredFiles = listOf("mimetype", "META-INF/container.xml")
    override val primaryNavigationSource = NavigationSource.NCX
    override val fallbackNavigationSource = NavigationSource.NONE
    override val allowedRecoveryHeuristics = listOf(
        "TOC_FALLBACK_TO_FILE_LIST",
        "XML_PARSING_STRICT_FALLBACK_TO_REGEX",
        "LOOSE_OPF_EXTENSION_LOOKUP"
    )
    override val frontMatterHandlingExpectations = listOf(
        "GUIDE_ELEMENTS",
        "SPINE_LINEAR_NO",
        "FILENAME_HEURISTICS"
    )
}

class Epub3Rules : EpubSpecRules {
    override val version = EpubVersion.EPUB_3_0
    override val requiredFiles = listOf("mimetype", "META-INF/container.xml")
    override val primaryNavigationSource = NavigationSource.NAV_DOCUMENT
    override val fallbackNavigationSource = NavigationSource.NCX
    override val allowedRecoveryHeuristics = listOf(
        "TOC_FALLBACK_TO_NCX",
        "TOC_FALLBACK_TO_FILE_LIST",
        "XML_PARSING_STRICT_FALLBACK_TO_REGEX",
        "LOOSE_OPF_EXTENSION_LOOKUP"
    )
    override val frontMatterHandlingExpectations = listOf(
        "NAV_LANDMARKS",
        "SPINE_LINEAR_NO",
        "MANIFEST_PROPERTIES_NAV",
        "FILENAME_HEURISTICS",
        "SEMANTIC_EPUB_TYPE"
    )
}

object EpubSpecRegistry {
    private val rulesMap = mapOf(
        EpubVersion.EPUB_2 to Epub2Rules(),
        EpubVersion.EPUB_3_0 to Epub3Rules(),
        EpubVersion.EPUB_3_1 to Epub3Rules(), // share the same core ruleset with 3.0 modifications
        EpubVersion.EPUB_3_2 to Epub3Rules()
    )

    fun getRulesForVersion(version: EpubVersion): EpubSpecRules {
        return rulesMap[version] ?: Epub2Rules() // Default to Epub2Rules or standard fallback
    }
}
