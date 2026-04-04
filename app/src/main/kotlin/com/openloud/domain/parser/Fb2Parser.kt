package com.openloud.domain.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * FictionBook2 (.fb2 and .fb2.zip) parser
 * FB2 is an XML-based ebook format popular in Russia
 */
class Fb2Parser : BookParser {

    companion object {
        private const val TAG = "Fb2Parser"
    }

    override suspend fun parse(
        inputStream: InputStream,
        fileName: String,
        onProgress: ((Float) -> Unit)?
    ): ParsedBook = withContext(Dispatchers.IO) {
        try {
            // Handle .fb2.zip files
            val xmlInputStream = if (fileName.endsWith(".fb2.zip", ignoreCase = true)) {
                val zipInputStream = ZipInputStream(inputStream)
                val entry = zipInputStream.nextEntry
                if (entry == null) {
                    Log.e(TAG, "No entry found in FB2 ZIP")
                    return@withContext ParsedBook(
                        title = fileName,
                        author = null,
                        chapters = emptyList()
                    )
                }
                zipInputStream
            } else {
                inputStream
            }

            onProgress?.invoke(0.2f)

            // Parse the XML
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xmlInputStream)

            onProgress?.invoke(0.4f)

            // Extract metadata from <description> section
            val (title, author, language) = extractMetadata(doc, fileName)

            onProgress?.invoke(0.5f)

            // Extract chapters from <body> sections
            val chapters = extractChapters(doc, onProgress)

            onProgress?.invoke(1.0f)

            ParsedBook(
                title = title,
                author = author,
                language = language,
                chapters = chapters
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing FB2", e)
            ParsedBook(
                title = fileName.removeSuffix(".fb2.zip").removeSuffix(".fb2"),
                author = null,
                chapters = emptyList()
            )
        }
    }

    private fun extractMetadata(doc: org.w3c.dom.Document, fileName: String): Triple<String?, String?, String?> {
        return try {
            var title: String? = null
            var author: String? = null
            var language: String? = null

            // Get title from <book-title>
            val titleNodes = doc.getElementsByTagName("book-title")
            if (titleNodes.length > 0) {
                title = titleNodes.item(0).textContent?.trim()
            }

            // Get author from <author><first-name> and <last-name>
            val authorNodes = doc.getElementsByTagName("author")
            if (authorNodes.length > 0) {
                val authorElement = authorNodes.item(0) as Element
                val firstName = authorElement.getElementsByTagName("first-name")
                    .item(0)?.textContent?.trim() ?: ""
                val lastName = authorElement.getElementsByTagName("last-name")
                    .item(0)?.textContent?.trim() ?: ""
                author = "$firstName $lastName".trim().takeIf { it.isNotBlank() }
            }

            // Get language from <lang>
            val langNodes = doc.getElementsByTagName("lang")
            if (langNodes.length > 0) {
                language = langNodes.item(0).textContent?.trim()
            }

            Triple(
                title?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".fb2.zip").removeSuffix(".fb2"),
                author,
                language
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting FB2 metadata", e)
            Triple(fileName.removeSuffix(".fb2.zip").removeSuffix(".fb2"), null, null)
        }
    }

    private fun extractChapters(doc: org.w3c.dom.Document, onProgress: ((Float) -> Unit)?): List<Chapter> {
        return try {
            val chapters = mutableListOf<Chapter>()
            val bodyNodes = doc.getElementsByTagName("body")

            if (bodyNodes.length == 0) {
                return emptyList()
            }

            // Process main body (skip notes body)
            for (bodyIndex in 0 until bodyNodes.length) {
                val body = bodyNodes.item(bodyIndex) as Element
                // Skip notes/comments bodies
                val bodyName = body.getAttribute("name")
                if (bodyName == "notes" || bodyName == "comments") continue

                // Get all sections in the body
                val sections = body.getElementsByTagName("section")

                for (i in 0 until sections.length) {
                    val section = sections.item(i) as Element
                    val (chapterTitle, chapterText) = extractSection(section)

                    if (chapterText.isNotBlank()) {
                        chapters.add(
                            Chapter(
                                index = chapters.size,
                                title = chapterTitle ?: "Chapter ${chapters.size + 1}",
                                textContent = chapterText
                            )
                        )
                    }

                    if (i % 10 == 0) {
                        onProgress?.invoke(0.5f + (i.toFloat() / sections.length) * 0.5f)
                    }
                }
            }

            // If no chapters found, extract all text as single chapter
            if (chapters.isEmpty()) {
                val allText = extractAllText(doc.documentElement)
                if (allText.isNotBlank()) {
                    chapters.add(
                        Chapter(
                            index = 0,
                            title = "Full Book",
                            textContent = allText
                        )
                    )
                }
            }

            chapters
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting FB2 chapters", e)
            emptyList()
        }
    }

    private fun extractSection(section: Element): Pair<String?, String> {
        val titleNodes = section.getElementsByTagName("title")
        val title = if (titleNodes.length > 0) {
            extractTextFromElement(titleNodes.item(0) as Element)
        } else null

        val paragraphNodes = section.getElementsByTagName("p")
        val text = buildString {
            for (i in 0 until paragraphNodes.length) {
                val p = paragraphNodes.item(i) as Element
                val pText = extractTextFromElement(p)
                if (pText.isNotBlank()) {
                    append(pText)
                    append("\n\n")
                }
            }
        }

        return Pair(title, text.trim())
    }

    private fun extractTextFromElement(element: Element): String {
        return buildString {
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                when (node.nodeType) {
                    org.w3c.dom.Node.TEXT_NODE -> append(node.textContent)
                    org.w3c.dom.Node.ELEMENT_NODE -> {
                        // Recursively extract text from child elements
                        append(extractTextFromElement(node as Element))
                    }
                }
            }
        }.trim()
    }

    private fun extractAllText(element: Element): String {
        return buildString {
            val paragraphs = element.getElementsByTagName("p")
            for (i in 0 until paragraphs.length) {
                val text = extractTextFromElement(paragraphs.item(i) as Element)
                if (text.isNotBlank()) {
                    append(text)
                    append("\n\n")
                }
            }
        }.trim()
    }
}
