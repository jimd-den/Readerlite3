package com.example.domain.usecase

import com.example.domain.epub.*
import com.example.domain.model.*

class ParseContainerUseCase(private val validator: EpubValidator) {
    fun execute(zipFiles: Map<String, ByteArray>): Pair<Boolean, List<ParsingMessage>> {
        val messages = validator.validateContainer(zipFiles)
        val hasErrors = messages.any { it.severity == ParsingMessage.Severity.ERROR }
        return (!hasErrors) to messages
    }
}

class ParsePackageDocumentUseCase(private val packageParser: EpubPackageParser) {
    fun execute(
        zipFiles: Map<String, ByteArray>,
        packagePath: String,
        mode: ParsingMode
    ): Pair<PublicationManifest, List<ParsingMessage>> {
        return packageParser.parsePackage(zipFiles, packagePath, mode)
    }
}

class BuildNavigationOutlineUseCase(private val navigationParser: EpubNavigationParser) {
    fun execute(
        zipFiles: Map<String, ByteArray>,
        manifest: PublicationManifest,
        mode: ParsingMode
    ): Pair<BookOutline, List<ParsingMessage>> {
        return navigationParser.parseNavigation(zipFiles, manifest, mode)
    }
}

class ClassifyContentUseCase {
    fun determineClassification(
        href: String,
        isLinear: Boolean,
        properties: String?,
        title: String,
        depth: Int
    ): Pair<ClassificationPolicy, OutlineNodeType> {
        val lowerHref = href.lowercase()
        val lowerTitle = title.lowercase()

        val isAuxiliaryProperty = properties?.contains("nav") == true || properties?.contains("cover") == true
        val isCoverHref = lowerHref.contains("cover") || lowerHref.contains("jacket")
        val isTocHref = lowerHref.contains("toc") || lowerHref.contains("nav") || lowerHref.contains("contents")
        
        val isFrontMatterMatched = lowerHref.contains("preface") || lowerHref.contains("foreword") ||
                lowerHref.contains("intro") || lowerHref.contains("titlepage") ||
                lowerHref.contains("copyright") || lowerHref.contains("dedication") ||
                lowerHref.contains("colophon") || lowerHref.contains("ack") ||
                lowerTitle.contains("preface") || lowerTitle.contains("foreword") ||
                lowerTitle.contains("dedication") || lowerTitle.contains("acknowledg") ||
                lowerTitle.contains("introduction") || lowerTitle.contains("title page")

        val isBackMatterMatched = lowerHref.contains("appendix") || lowerHref.contains("index") ||
                lowerHref.contains("glossary") || lowerHref.contains("biblio") ||
                lowerTitle.contains("appendix") || lowerTitle.contains("index") ||
                lowerTitle.contains("glossary") || lowerTitle.contains("bibliography")

        return when {
            !isLinear -> {
                ClassificationPolicy.EXCLUDE_FROM_MAIN_OUTLINE to OutlineNodeType.NAV_AUXILIARY
            }
            isAuxiliaryProperty || isCoverHref -> {
                ClassificationPolicy.AUXILIARY to OutlineNodeType.FRONT_MATTER
            }
            isTocHref -> {
                ClassificationPolicy.AUXILIARY to OutlineNodeType.NAV_AUXILIARY
            }
            isFrontMatterMatched -> {
                ClassificationPolicy.EXCLUDE_FROM_MAIN_OUTLINE to OutlineNodeType.FRONT_MATTER
            }
            isBackMatterMatched -> {
                ClassificationPolicy.EXCLUDE_FROM_MAIN_OUTLINE to OutlineNodeType.BACK_MATTER
            }
            depth > 1 -> {
                ClassificationPolicy.INCLUDE_IN_OUTLINE_ONLY to OutlineNodeType.SUBSECTION
            }
            depth == 1 -> {
                ClassificationPolicy.INCLUDE_IN_READING to OutlineNodeType.SECTION
            }
            else -> {
                ClassificationPolicy.INCLUDE_IN_READING to OutlineNodeType.CHAPTER
            }
        }
    }
}

class ExtractReadableSectionsUseCase(private val contentExtractor: EpubContentExtractor) {
    fun execute(
        zipFiles: Map<String, ByteArray>,
        manifest: PublicationManifest,
        outline: BookOutline,
        mode: ParsingMode
    ): List<ExtractedSection> {
        return contentExtractor.extractContent(zipFiles, manifest, outline, mode)
    }
}
