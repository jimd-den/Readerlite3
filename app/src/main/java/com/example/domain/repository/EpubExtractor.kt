package com.example.domain.repository

import com.example.domain.model.EpubStructureDomainModel
import java.io.InputStream

interface EpubExtractor {
    fun parseEpub(inputStream: InputStream): EpubStructureDomainModel
    fun parseTxtOrPdf(content: String, title: String): EpubStructureDomainModel
}
