package com.example.domain.model

data class ParsedChapterDomain(
    val title: String,
    val isSubchapter: Boolean,
    val parentTitle: String?,
    val nestingLevel: Int = 0
)

data class ParsedSentenceDomain(
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val text: String,
    val sectionTitle: String?
)

data class EpubStructureDomainModel(
    val title: String?,
    val author: String?,
    val chapters: List<ParsedChapterDomain>,
    val sentences: List<ParsedSentenceDomain>
)
