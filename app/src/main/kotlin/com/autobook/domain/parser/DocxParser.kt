package com.autobook.domain.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * DOCX parser - extracts text from DOCX files (which are ZIP archives containing word/document.xml)
 */
class DocxParser : BookParser {

    companion object {
        private const val TAG = "DocxParser"
        private const val DOCUMENT_XML_PATH = "word/document.xml"
        private const val CORE_PROPS_PATH = "docProps/core.xml"
    }

    override suspend fun parse(
        inputStream: InputStream,
        fileName: String,
        onProgress: ((Float) -> Unit)?
    ): ParsedBook = withContext(Dispatchers.IO) {
        try {
            val zipInputStream = ZipInputStream(inputStream)
            var documentXml: String? = null
            var corePropsXml: String? = null

            // Extract document.xml and core.xml from the ZIP
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                when (entry.name) {
                    DOCUMENT_XML_PATH -> {
                        documentXml = zipInputStream.bufferedReader().readText()
                    }
                    CORE_PROPS_PATH -> {
                        corePropsXml = zipInputStream.bufferedReader().readText()
                    }
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            if (documentXml == null) {
                Log.e(TAG, "document.xml not found in DOCX")
                return@withContext ParsedBook(
                    title = fileName.removeSuffix(".docx"),
                    author = null,
                    chapters = emptyList()
                )
            }

            // Extract metadata
            val (title, author) = extractMetadata(corePropsXml, fileName)

            // Parse document.xml for text and structure
            val chapters = parseDocumentXml(documentXml, onProgress)

            ParsedBook(
                title = title,
                author = author,
                chapters = chapters
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DOCX", e)
            ParsedBook(
                title = fileName.removeSuffix(".docx"),
                author = null,
                chapters = emptyList()
            )
        }
    }

    private fun extractMetadata(corePropsXml: String?, fileName: String): Pair<String?, String?> {
        if (corePropsXml == null) {
            return Pair(fileName.removeSuffix(".docx"), null)
        }

        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(corePropsXml.byteInputStream())

            var title: String? = null
            var author: String? = null

            // Try various metadata tags
            val titleNodes = doc.getElementsByTagName("dc:title")
            if (titleNodes.length > 0) {
                title = titleNodes.item(0).textContent?.trim()
            }

            val creatorNodes = doc.getElementsByTagName("dc:creator")
            if (creatorNodes.length > 0) {
                author = creatorNodes.item(0).textContent?.trim()
            }

            Pair(
                title?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".docx"),
                author?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing core properties", e)
            Pair(fileName.removeSuffix(".docx"), null)
        }
    }

    private fun parseDocumentXml(documentXml: String, onProgress: ((Float) -> Unit)?): List<Chapter> {
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(documentXml.byteInputStream())

            val paragraphs = doc.getElementsByTagName("w:p")
            val chapters = mutableListOf<Chapter>()
            val currentChapterText = StringBuilder()
            var currentChapterTitle = "Chapter 1"
            var chapterIndex = 0

            onProgress?.invoke(0.3f)

            for (i in 0 until paragraphs.length) {
                val paragraph = paragraphs.item(i) as Element
                val text = extractParagraphText(paragraph)

                if (text.isBlank()) continue

                // Check if this is a heading (potential chapter boundary)
                val isHeading = isHeading(paragraph)

                if (isHeading && currentChapterText.isNotEmpty()) {
                    // Save current chapter
                    chapters.add(
                        Chapter(
                            index = chapterIndex,
                            title = currentChapterTitle,
                            textContent = currentChapterText.toString().trim()
                        )
                    )
                    chapterIndex++
                    currentChapterText.clear()
                    currentChapterTitle = text
                } else if (isHeading) {
                    // First heading
                    currentChapterTitle = text
                } else {
                    // Regular paragraph
                    currentChapterText.append(text)
                    currentChapterText.append("\n\n")
                }

                if (i % 100 == 0) {
                    onProgress?.invoke(0.3f + (i.toFloat() / paragraphs.length) * 0.7f)
                }
            }

            // Add final chapter
            if (currentChapterText.isNotEmpty()) {
                chapters.add(
                    Chapter(
                        index = chapterIndex,
                        title = currentChapterTitle,
                        textContent = currentChapterText.toString().trim()
                    )
                )
            }

            onProgress?.invoke(1.0f)

            // If no chapters detected, create one chapter with all content
            if (chapters.isEmpty()) {
                val allText = buildString {
                    for (i in 0 until paragraphs.length) {
                        val text = extractParagraphText(paragraphs.item(i) as Element)
                        if (text.isNotBlank()) {
                            append(text)
                            append("\n\n")
                        }
                    }
                }
                listOf(
                    Chapter(
                        index = 0,
                        title = "Full Document",
                        textContent = allText.trim()
                    )
                )
            } else {
                chapters
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing document XML", e)
            emptyList()
        }
    }

    private fun extractParagraphText(paragraph: Element): String {
        val textNodes = paragraph.getElementsByTagName("w:t")
        val sb = StringBuilder()
        for (i in 0 until textNodes.length) {
            sb.append(textNodes.item(i).textContent)
        }
        return sb.toString()
    }

    private fun isHeading(paragraph: Element): Boolean {
        // Check for heading styles (Heading 1, Heading 2, etc.)
        val styleNodes = paragraph.getElementsByTagName("w:pStyle")
        if (styleNodes.length > 0) {
            val style = (styleNodes.item(0) as Element).getAttribute("w:val")
            if (style.contains("Heading", ignoreCase = true)) {
                return true
            }
        }

        // Check for bold text that looks like a heading
        val text = extractParagraphText(paragraph).trim()
        if (text.length < 100 && text.matches(Regex("^(Chapter|CHAPTER|Part|PART).*"))) {
            return true
        }

        return false
    }
}
