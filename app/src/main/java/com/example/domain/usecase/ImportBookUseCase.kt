package com.example.domain.usecase

import com.example.domain.repository.EpubExtractor
import com.example.domain.repository.StudyRepository
import com.example.domain.model.EpubStructureDomainModel
import java.io.InputStream

class ImportBookUseCase(
    private val studyRepository: StudyRepository,
    private val epubExtractor: EpubExtractor
) {
    suspend fun execute(
        classId: String,
        title: String,
        author: String,
        fileType: String,
        filePath: String,
        rawContent: String,
        inputStreamProvider: () -> InputStream?
    ): String {
        val structure = if (fileType == "EPUB") {
            val stream = inputStreamProvider()
            if (stream != null) {
                try {
                    epubExtractor.parseEpub(stream)
                } catch (e: Exception) {
                    e.printStackTrace()
                    epubExtractor.parseTxtOrPdf(rawContent, title)
                }
            } else {
                epubExtractor.parseTxtOrPdf(rawContent, title)
            }
        } else {
            epubExtractor.parseTxtOrPdf(rawContent, title)
        }

        val finalTitle = if (fileType == "EPUB" && !structure.title.isNullOrBlank()) {
            val simpleFileName = try {
                java.io.File(filePath).name.substringBeforeLast(".")
            } catch (e: Exception) {
                ""
            }
            val isAutoTitle = title.isBlank() || title == "Imported Document" || 
                title.replace(" ", "").lowercase() == simpleFileName.replace("-", "").replace("_", "").lowercase()
            if (isAutoTitle) structure.title else title
        } else if (!title.isNullOrBlank() && title != "Imported Document") {
            title
        } else {
            if (!structure.title.isNullOrBlank()) structure.title else "Untitled Book"
        }

        val finalAuthor = if (fileType == "EPUB" && !structure.author.isNullOrBlank()) {
            val isAutoAuthor = author.isBlank() || author == "Local Importer" || author == "Academic Author"
            if (isAutoAuthor) structure.author else author
        } else if (!author.isNullOrBlank() && author != "Local Importer") {
            author
        } else {
            if (!structure.author.isNullOrBlank()) structure.author else "Academic Author"
        }

        return studyRepository.importBook(
            classId = classId,
            title = finalTitle,
            author = finalAuthor,
            fileType = fileType,
            filePath = filePath,
            structure = structure
        )
    }
}
