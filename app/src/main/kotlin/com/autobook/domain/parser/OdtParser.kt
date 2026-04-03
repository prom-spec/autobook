package com.autobook.domain.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * OpenDocument Text (.odt) parser
 * ODT files are ZIP archives containing content.xml
 */
class OdtParser : BookParser {

    companion object {
        private const val TAG = "OdtParser"
        private const val CONTENT_XML_PATH = "content.xml"
        private const val META_XML_PATH = "meta.xml"
    }

    override suspend fun parse(
        inputStream: InputStream,
        fileName: String,
        onProgress: ((Float) -> Unit)?
    ): ParsedBook = withContext(Dispatchers.IO) {
        try {
            val zipInputStream = ZipInputStream(inputStream)
            var contentXml: String? = null
            var metaXml: String? = null

            // Extract content.xml and meta.xml from the ZIP
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                when (entry.name) {
                    CONTENT_XML_PATH -> {
                        contentXml = zipInputStream.bufferedReader().readText()
                    }
                    META_XML_PATH -> {
                        metaXml = zipInputStream.bufferedReader().readText()
                    }
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            if (contentXml == null) {
                Log.e(TAG, "content.xml not found in ODT")
                return@withContext ParsedBook(
                    title = fileName.removeSuffix(".odt"),
                    author = null,
                    chapters = emptyList()
                )
            }

            onProgress?.invoke(0.3f)

            // Extract metadata
            val (title, author) = extractMetadata(metaXml, fileName)

            onProgress?.invoke(0.4f)

            // Parse content.xml for text and structure
            val chapters = parseContentXml(contentXml, onProgress)

            onProgress?.invoke(1.0f)

            ParsedBook(
                title = title,
                author = author,
                chapters = chapters
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ODT", e)
            ParsedBook(
                title = fileName.removeSuffix(".odt"),
                author = null,
                chapters = emptyList()
            )
        }
    }

    private fun extractMetadata(metaXml: String?, fileName: String): Pair<String?, String?> {
        if (metaXml == null) {
            return Pair(fileName.removeSuffix(".odt"), null)
        }

        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(metaXml.byteInputStream())

            var title: String? = null
            var author: String? = null

            // Try dc:title
            val titleNodes = doc.getElementsByTagName("dc:title")
            if (titleNodes.length > 0) {
                title = titleNodes.item(0).textContent?.trim()
            }

            // Try dc:creator or meta:initial-creator
            val creatorNodes = doc.getElementsByTagName("dc:creator")
            if (creatorNodes.length > 0) {
                author = creatorNodes.item(0).textContent?.trim()
            } else {
                val initialCreatorNodes = doc.getElementsByTagName("meta:initial-creator")
                if (initialCreatorNodes.length > 0) {
                    author = initialCreatorNodes.item(0).textContent?.trim()
                }
            }

            Pair(
                title?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".odt"),
                author?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ODT metadata", e)
            Pair(fileName.removeSuffix(".odt"), null)
        }
    }

    private fun parseContentXml(contentXml: String, onProgress: ((Float) -> Unit)?): List<Chapter> {
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(contentXml.byteInputStream())

            // Get all paragraphs (text:p) and headings (text:h)
            val bodyNode = doc.getElementsByTagName("office:text").item(0) ?: return emptyList()

            val chapters = mutableListOf<Chapter>()
            val currentChapterText = StringBuilder()
            var currentChapterTitle = "Chapter 1"
            var chapterIndex = 0

            val childNodes = bodyNode.childNodes
            var processedNodes = 0

            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue

                val element = node as Element
                val text = extractElementText(element)

                if (text.isBlank()) continue

                when (element.tagName) {
                    "text:h" -> {
                        // Heading - chapter boundary
                        if (currentChapterText.isNotEmpty()) {
                            chapters.add(
                                Chapter(
                                    index = chapterIndex,
                                    title = currentChapterTitle,
                                    textContent = currentChapterText.toString().trim()
                                )
                            )
                            chapterIndex++
                            currentChapterText.clear()
                        }
                        currentChapterTitle = text
                    }
                    "text:p" -> {
                        // Regular paragraph
                        currentChapterText.append(text)
                        currentChapterText.append("\n\n")
                    }
                }

                processedNodes++
                if (processedNodes % 100 == 0) {
                    onProgress?.invoke(0.4f + (processedNodes.toFloat() / childNodes.length) * 0.6f)
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

            // If no chapters detected, create one chapter with all content
            if (chapters.isEmpty()) {
                val allText = buildString {
                    for (i in 0 until childNodes.length) {
                        val node = childNodes.item(i)
                        if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                            val text = extractElementText(node as Element)
                            if (text.isNotBlank()) {
                                append(text)
                                append("\n\n")
                            }
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
            Log.e(TAG, "Error parsing ODT content", e)
            emptyList()
        }
    }

    private fun extractElementText(element: Element): String {
        return buildString {
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                when (node.nodeType) {
                    org.w3c.dom.Node.TEXT_NODE -> append(node.textContent)
                    org.w3c.dom.Node.ELEMENT_NODE -> {
                        // Handle spans and other inline elements
                        append(extractElementText(node as Element))
                    }
                }
            }
        }.trim()
    }
}
