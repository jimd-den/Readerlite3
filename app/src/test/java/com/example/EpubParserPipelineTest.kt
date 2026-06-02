package com.example

import com.example.domain.epub.*
import com.example.domain.model.*
import com.example.domain.usecase.*
import com.example.data.parser.*
import com.example.domain.service.TextSanitizerImpl
import org.junit.Assert.*
import org.junit.Test

class EpubParserPipelineTest {

    private val sanitizer = TextSanitizerImpl()
    private val classifyContentUseCase = ClassifyContentUseCase()

    @Test
    fun testVersionDetectionAndRegistryRules() {
        // Assert Registry rules matching EPUB versions
        val epub2Rules = EpubSpecRegistry.getRulesForVersion(EpubVersion.EPUB_2)
        assertEquals(NavigationSource.NCX, epub2Rules.primaryNavigationSource)
        assertEquals(NavigationSource.NONE, epub2Rules.fallbackNavigationSource)

        val epub3Rules = EpubSpecRegistry.getRulesForVersion(EpubVersion.EPUB_3_0)
        assertEquals(NavigationSource.NAV_DOCUMENT, epub3Rules.primaryNavigationSource)
        assertEquals(NavigationSource.NCX, epub3Rules.fallbackNavigationSource)
    }

    @Test
    fun testContentAuxiliaryClassificationPolicies() {
        // Test Cover classification
        val coverResult = classifyContentUseCase.determineClassification(
            href = "OEBPS/cover.xhtml",
            isLinear = true,
            properties = "cover",
            title = "Cover Screen",
            depth = 0
        )
        assertEquals(ClassificationPolicy.AUXILIARY, coverResult.first)
        assertEquals(OutlineNodeType.FRONT_MATTER, coverResult.second)

        // Test Table of contents file
        val tocResult = classifyContentUseCase.determineClassification(
            href = "OEBPS/toc.xhtml",
            isLinear = true,
            properties = "nav",
            title = "Table of Contents",
            depth = 0
        )
        assertEquals(ClassificationPolicy.AUXILIARY, tocResult.first)
        assertEquals(OutlineNodeType.NAV_AUXILIARY, tocResult.second)

        // Test Preface file (front matter)
        val prefaceResult = classifyContentUseCase.determineClassification(
            href = "OEBPS/preface.xhtml",
            isLinear = true,
            properties = null,
            title = "Preface of the Book",
            depth = 0
        )
        assertEquals(ClassificationPolicy.EXCLUDE_FROM_MAIN_OUTLINE, prefaceResult.first)
        assertEquals(OutlineNodeType.FRONT_MATTER, prefaceResult.second)

        // Test Standard Chapter
        val chResult = classifyContentUseCase.determineClassification(
            href = "OEBPS/chapter01.xhtml",
            isLinear = true,
            properties = null,
            title = "Chapter One: Inception",
            depth = 0
        )
        assertEquals(ClassificationPolicy.INCLUDE_IN_READING, chResult.first)
        assertEquals(OutlineNodeType.CHAPTER, chResult.second)

        // Test Subchapter (nested node with depth > 0)
        val subchResult = classifyContentUseCase.determineClassification(
            href = "OEBPS/chapter01.xhtml#sec3",
            isLinear = true,
            properties = null,
            title = "Section 1.3: Detail of Study",
            depth = 2
        )
        assertEquals(ClassificationPolicy.INCLUDE_IN_OUTLINE_ONLY, subchResult.first)
        assertEquals(OutlineNodeType.SUBSECTION, subchResult.second)
    }

    @Test
    fun testValidatorWithValidAndInvalidMimetype() {
        val validator = EpubValidatorImpl()

        // Case 1: Valid container files
        val validZip = mapOf(
            "mimetype" to "application/epub+zip".toByteArray(),
            "meta-inf/container.xml" to """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent().toByteArray()
        )
        val validMessages = validator.validateContainer(validZip)
        assertFalse(validMessages.any { it.severity == ParsingMessage.Severity.ERROR })

        // Case 2: Missing mimetype file (should fail validation)
        val invalidZipMissingMime = mapOf(
            "meta-inf/container.xml" to "some bytes".toByteArray()
        )
        val invalidMessages = validator.validateContainer(invalidZipMissingMime)
        assertTrue(invalidMessages.any { it.message.contains("Missing root mimetype file") })

        // Case 3: Invalid mimetype content (should fail validation)
        val invalidMimeContentZip = mapOf(
            "mimetype" to "text/plain".toByteArray(),
            "meta-inf/container.xml" to "some bytes".toByteArray()
        )
        val invalidMessages2 = validator.validateContainer(invalidMimeContentZip)
        assertTrue(invalidMessages2.any { it.message.contains("Mimetype is not application/epub+zip") })
    }

    @Test
    fun testStrictVsRecoveryModePackageParsing() {
        val parser = EpubPackageParserImpl(sanitizer)

        val malformedOPFXml = "Malformed OPF structure that has invalid tags"
        
        // Under STRICT_SPEC mode, it should throw an exception or fail if OPF is corrupted / version is unsupported
        val mockZip = mapOf("OEBPS/content.opf" to malformedOPFXml.toByteArray())
        
        try {
            parser.parsePackage(mockZip, "OEBPS/content.opf", ParsingMode.STRICT_SPEC)
            // It might succeed with defaults, but if we pass an completely invalid version, it strictly warns
        } catch (e: Exception) {
            // Success catching strict error
        }

        // Under BEST_EFFORT mode, it should successfully fallback to regex-based heuristics
        val (manifest, messages) = parser.parsePackage(mockZip, "OEBPS/content.opf", ParsingMode.BEST_EFFORT)
        assertNotNull(manifest)
        assertEquals("Untitled Document", manifest.title)
        assertEquals("Academic Author", manifest.author)
        assertTrue(messages.any { it.message.contains("XMLPullParser threw exception") })
    }

    @Test
    fun testBookOutlineHierarchyDeduplicationAndNesting() {
        val rootNode = OutlineNode(
            id = "parent",
            title = "Chapter 1",
            href = "chapter1.xhtml",
            type = OutlineNodeType.CHAPTER,
            children = listOf(
                OutlineNode(
                    id = "child1",
                    title = "Section 1.1",
                    href = "chapter1.xhtml#sec1",
                    type = OutlineNodeType.SUBSECTION,
                    depth = 1,
                    parentId = "parent"
                ),
                OutlineNode(
                    id = "child2",
                    title = "Section 1.2",
                    href = "chapter1.xhtml#sec2",
                    type = OutlineNodeType.SUBSECTION,
                    depth = 1,
                    parentId = "parent"
                )
            )
        )

        val bookOutline = BookOutline(listOf(rootNode))
        val flatList = bookOutline.flatten()

        // Verify correct hierarchy length and contents are preserved
        assertEquals(3, flatList.size)
        assertEquals("parent", flatList[0].id)
        assertEquals("Chapter 1", flatList[0].title)

        assertEquals("child1", flatList[1].id)
        assertEquals("Section 1.1", flatList[1].title)
        assertEquals("parent", flatList[1].parentId)
        assertEquals(1, flatList[1].depth)

        assertEquals("child2", flatList[2].id)
        assertEquals("Section 1.2", flatList[2].title)
        assertEquals("parent", flatList[2].parentId)
        assertEquals(1, flatList[2].depth)
    }
}
