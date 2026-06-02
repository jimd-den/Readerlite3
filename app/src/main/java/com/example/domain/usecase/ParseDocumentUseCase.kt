package com.example.domain.usecase

import com.example.domain.model.EpubStructureDomainModel
import com.example.domain.repository.EpubExtractor
import java.io.InputStream

/**
 * Clean architecture Use Case to extract document structure without being tied to specific UI flows.
 */
class ParseDocumentUseCase(private val epubExtractor: EpubExtractor) {
    fun parseEpub(inputStream: InputStream): EpubStructureDomainModel {
        return epubExtractor.parseEpub(inputStream)
    }

    fun parseTxtOrPdf(content: String, title: String): EpubStructureDomainModel {
        return epubExtractor.parseTxtOrPdf(content, title)
    }
}
