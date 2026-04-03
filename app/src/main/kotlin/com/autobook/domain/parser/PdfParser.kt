package com.autobook.domain.parser

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfParser(private val context: Context) {

    companion object {
        private const val TAG = "PdfParser"
    }

    init {
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Extract text page-by-page with progress callback.
     * onProgress receives a value from 0.0 to 1.0.
     */
    suspend fun extractText(
        filePath: String,
        onProgress: ((Float) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }

        var document: PDDocument? = null
        try {
            document = PDDocument.load(file)
            val totalPages = document.numberOfPages

            if (totalPages <= 0) return@withContext ""

            val stripper = PDFTextStripper()
            val sb = StringBuilder()

            val chunkSize = 20
            var startPage = 1
            while (startPage <= totalPages) {
                val endPage = minOf(startPage + chunkSize - 1, totalPages)
                stripper.startPage = startPage
                stripper.endPage = endPage
                sb.append(stripper.getText(document))

                onProgress?.invoke(endPage.toFloat() / totalPages)
                startPage = endPage + 1
            }

            sb.toString()
        } finally {
            document?.close()
        }
    }

    /**
     * Extract metadata: title, author, language.
     * If PDF metadata title is missing, tries to detect from first page content.
     */
    suspend fun extractMetadata(filePath: String): Triple<String?, String?, String?> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext Triple(null, null, null)

        var document: PDDocument? = null
        try {
            document = PDDocument.load(file)
            val info = document.documentInformation

            var title = info?.title?.takeIf { it.isNotBlank() }
            val author = info?.author?.takeIf { it.isNotBlank() }
            val pdfLang = document.documentCatalog?.language

            // If no title in metadata, try to extract from first page
            if (title == null) {
                title = extractTitleFromFirstPage(document)
            }

            // Detect language from content if not in metadata
            val language = pdfLang?.takeIf { it.isNotBlank() }
                ?: detectLanguage(document)

            Log.d(TAG, "Metadata: title='$title', author='$author', lang='$language'")
            Triple(title, author, language)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata", e)
            Triple(null, null, null)
        } finally {
            document?.close()
        }
    }

    /**
     * Try to extract the book title from the first page.
     * Heuristic: the first non-empty, non-trivial line that looks like a title.
     */
    private fun extractTitleFromFirstPage(document: PDDocument): String? {
        return try {
            val stripper = PDFTextStripper()
            stripper.startPage = 1
            stripper.endPage = minOf(3, document.numberOfPages) // Check first 3 pages
            val text = stripper.getText(document)

            val lines = text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            // Look for title patterns:
            // 1. A line that's mostly uppercase or title case, 3-80 chars
            // 2. Skip "copyright", "published by", page numbers, etc.
            val skipPatterns = listOf(
                "copyright", "published", "isbn", "all rights", "creative commons",
                "license", "www.", "http", "edition", "printed", "library of congress"
            )

            for (line in lines.take(20)) {
                val lower = line.lowercase()
                if (skipPatterns.any { lower.contains(it) }) continue
                if (line.matches(Regex("^\\d+$"))) continue // page number
                if (line.length < 3 || line.length > 80) continue

                // Likely a title if it's short-ish and doesn't look like body text
                val wordCount = line.split("\\s+".toRegex()).size
                if (wordCount in 1..8) {
                    // Check it's not just a "by Author" line
                    if (!lower.startsWith("by ") && !lower.startsWith("a novel")) {
                        Log.d(TAG, "Detected title from first page: '$line'")
                        return line
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting title from first page", e)
            null
        }
    }

    /**
     * Detect language by analyzing character frequency in first few pages.
     * Returns ISO 639-1 code (en, he, fr, de, es, etc.)
     */
    private fun detectLanguage(document: PDDocument): String? {
        return try {
            val stripper = PDFTextStripper()
            stripper.startPage = 1
            stripper.endPage = minOf(5, document.numberOfPages)
            val sample = stripper.getText(document).take(5000)

            // Count character ranges
            var hebrew = 0; var arabic = 0; var cjk = 0; var cyrillic = 0; var latin = 0

            for (c in sample) {
                when {
                    c.code in 0x0590..0x05FF -> hebrew++
                    c.code in 0x0600..0x06FF -> arabic++
                    c.code in 0x4E00..0x9FFF || c.code in 0x3040..0x30FF -> cjk++
                    c.code in 0x0400..0x04FF -> cyrillic++
                    c.code in 0x0041..0x007A -> latin++
                }
            }

            val total = hebrew + arabic + cjk + cyrillic + latin
            if (total == 0) return null

            when {
                hebrew.toFloat() / total > 0.3 -> "he"
                arabic.toFloat() / total > 0.3 -> "ar"
                cjk.toFloat() / total > 0.3 -> "zh"
                cyrillic.toFloat() / total > 0.3 -> "ru"
                else -> {
                    // For Latin scripts, use common word detection
                    val lower = sample.lowercase()
                    when {
                        // Check for common words in various languages
                        countOccurrences(lower, listOf(" the ", " and ", " of ", " is ", " was ", " to ")) > 20 -> "en"
                        countOccurrences(lower, listOf(" le ", " la ", " les ", " des ", " est ", " que ")) > 20 -> "fr"
                        countOccurrences(lower, listOf(" der ", " die ", " das ", " und ", " ist ", " ein ")) > 20 -> "de"
                        countOccurrences(lower, listOf(" el ", " la ", " los ", " las ", " que ", " del ")) > 20 -> "es"
                        countOccurrences(lower, listOf(" il ", " la ", " che ", " non ", " con ", " una ")) > 20 -> "it"
                        else -> "en" // Default to English
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting language", e)
            null
        }
    }

    private fun countOccurrences(text: String, words: List<String>): Int {
        return words.sumOf { word ->
            var count = 0
            var index = text.indexOf(word)
            while (index >= 0) {
                count++
                index = text.indexOf(word, index + 1)
            }
            count
        }
    }
}
