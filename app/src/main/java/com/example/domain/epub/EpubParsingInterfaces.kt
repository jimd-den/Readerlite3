package com.example.domain.epub

import com.example.domain.model.*

/**
 * Validates basic OCF container layout.
 */
interface EpubValidator {
    fun validateContainer(zipFiles: Map<String, ByteArray>): List<ParsingMessage>
}

/**
 * Parses OPF metadata, manifest list, and spine reading order.
 */
interface EpubPackageParser {
    fun parsePackage(
        zipFiles: Map<String, ByteArray>,
        packagePath: String,
        mode: ParsingMode
    ): Pair<PublicationManifest, List<ParsingMessage>>
}

/**
 * Parses EPUB 2 NCX or EPUB 3 Navigation Document files to build outline trees.
 */
interface EpubNavigationParser {
    fun parseNavigation(
        zipFiles: Map<String, ByteArray>,
        manifest: PublicationManifest,
        mode: ParsingMode
    ): Pair<BookOutline, List<ParsingMessage>>
}

/**
 * Extracts, sanitizes, and segments XHTML files within the reading order.
 */
interface EpubContentExtractor {
    fun extractContent(
        zipFiles: Map<String, ByteArray>,
        manifest: PublicationManifest,
        outline: BookOutline,
        mode: ParsingMode
    ): List<ExtractedSection>
}
