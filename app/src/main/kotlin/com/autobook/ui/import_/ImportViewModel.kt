package com.autobook.ui.import_

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.data.db.BookEntity
import com.autobook.data.db.BookFormat
import com.autobook.data.db.ChapterEntity
import com.autobook.data.repository.BookRepository
import com.autobook.domain.chapter.ChapterDetector
import com.autobook.domain.chapter.ContentCleaner
import com.autobook.domain.cover.CoverArtFetcher
import com.autobook.domain.parser.EpubParser
import com.autobook.domain.parser.PdfParser
import com.autobook.domain.parser.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ImportViewModel(
    private val repository: BookRepository,
    private val context: Context
) : ViewModel() {

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    private val pdfParser = PdfParser(context)
    private val txtParser = TxtParser()
    private val epubParser = EpubParser()
    private val chapterDetector = ChapterDetector()
    private val contentCleaner = ContentCleaner()
    private val coverArtFetcher = CoverArtFetcher(context)

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Processing(0, "Copying file...")

                // Copy file to app storage
                val file = copyFileToAppStorage(uri)
                _importState.value = ImportState.Processing(20, "Reading file...")

                // Determine format
                val format = detectFormat(file.extension)
                _importState.value = ImportState.Processing(30, "Extracting text...")

                // Extract text and metadata
                data class BookMeta(val text: String, val title: String, val author: String?, val language: String?)
                val meta = when (format) {
                    BookFormat.PDF -> {
                        val text = pdfParser.extractText(file.absolutePath) { pct ->
                            val progressInt = 30 + (pct * 20).toInt() // 30-50%
                            _importState.value = ImportState.Processing(progressInt, "Extracting text... ${(pct * 100).toInt()}%")
                        }
                        val (pdfTitle, pdfAuthor, pdfLang) = pdfParser.extractMetadata(file.absolutePath)
                        val cleanTitle = cleanFileName(pdfTitle ?: file.nameWithoutExtension)
                        BookMeta(text, cleanTitle, pdfAuthor, pdfLang)
                    }
                    BookFormat.TXT -> {
                        val text = txtParser.extractText(file.absolutePath)
                        val (txtTitle, txtAuthor) = txtParser.extractMetadata(file.absolutePath)
                        val cleanTitle = cleanFileName(txtTitle ?: file.nameWithoutExtension)
                        BookMeta(text, cleanTitle, txtAuthor, null)
                    }
                    BookFormat.EPUB -> {
                        val text = epubParser.extractText(file.absolutePath) { pct ->
                            val progressInt = 30 + (pct * 20).toInt()
                            _importState.value = ImportState.Processing(progressInt, "Extracting text... ${(pct * 100).toInt()}%")
                        }
                        val (epubTitle, epubAuthor, epubLang) = epubParser.extractMetadata(file.absolutePath)
                        val cleanTitle = cleanFileName(epubTitle ?: file.nameWithoutExtension)
                        BookMeta(text, cleanTitle, epubAuthor, epubLang)
                    }
                    else -> throw IllegalArgumentException("Unsupported format: $format")
                }
                val text = meta.text
                val title = meta.title
                val author = meta.author
                val language = meta.language

                // Check for duplicates
                if (repository.bookExistsByTitle(title)) {
                    _importState.value = ImportState.Error("\"$title\" is already in your library")
                    return@launch
                }

                _importState.value = ImportState.Processing(50, "Cleaning content...")

                // Clean text
                val cleanedText = contentCleaner.cleanText(text)

                _importState.value = ImportState.Processing(60, "Detecting chapters...")

                // Detect chapters
                val chapterBreaks = chapterDetector.detectChapters(cleanedText)
                val chapters = chapterDetector.createChapters(cleanedText, chapterBreaks)

                _importState.value = ImportState.Processing(75, "Fetching cover art...")

                // Create book entity
                val bookId = UUID.randomUUID().toString()

                // Fetch cover art
                val coverPath = try {
                    coverArtFetcher.fetchAndSaveCover(title, author, bookId)
                } catch (e: Exception) {
                    // If fetching fails, generate placeholder
                    null
                }

                _importState.value = ImportState.Processing(80, "Saving book...")
                val book = BookEntity(
                    id = bookId,
                    title = title,
                    author = author,
                    language = language,
                    coverPath = coverPath,
                    filePath = file.absolutePath,
                    format = format,
                    totalChapters = chapters.size,
                    totalDuration = estimateDuration(cleanedText)
                )

                // Create chapter entities
                val chapterEntities = chapters.mapIndexed { index, (chapterTitle, content) ->
                    ChapterEntity(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        index = index,
                        title = chapterTitle,
                        textContent = content,
                        startOffset = 0, // Could calculate actual offset if needed
                        estimatedDuration = estimateDuration(content)
                    )
                }

                // Save to database
                repository.insertBook(book)
                repository.insertChapters(chapterEntities)

                _importState.value = ImportState.Success(bookId, chapters.size)

            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun copyFileToAppStorage(uri: Uri): File = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open file")

        val fileName = "book_${System.currentTimeMillis()}.${getFileExtension(uri)}"
        val file = File(context.filesDir, fileName)

        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        file
    }

    private fun getFileExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("pdf") == true -> "pdf"
            mimeType?.contains("epub") == true -> "epub"
            mimeType?.contains("text") == true -> "txt"
            else -> {
                // Try to get from URI path
                val ext = uri.path?.substringAfterLast('.', "")?.lowercase() ?: ""
                if (ext in listOf("pdf", "epub", "txt")) ext else "txt"
            }
        }
    }

    private fun detectFormat(extension: String): BookFormat {
        return when (extension.lowercase()) {
            "pdf" -> BookFormat.PDF
            "txt" -> BookFormat.TXT
            "epub" -> BookFormat.EPUB
            else -> BookFormat.TXT
        }
    }

    private fun estimateDuration(text: String): Long {
        // Estimate: ~250 words per minute, ~5 chars per word
        val words = text.length / 5
        val minutes = words / 250
        return minutes * 60 * 1000L // milliseconds
    }

    /**
     * Clean up a filename into a proper book title.
     * "Stross/Accelerando" → "Accelerando"
     * "book_1234567890" → "Book"
     */
    private fun cleanFileName(name: String): String {
        var clean = name
        // Handle "Author/Title" patterns — take the last part as title
        if (clean.contains("/") || clean.contains("\\")) {
            clean = clean.split("/", "\\").last().trim()
        }
        return clean
            .replace(Regex("_\\d{5,}"), "")  // remove timestamp suffixes
            .replace("_", " ")
            .replace("-", " ")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun resetState() {
        _importState.value = ImportState.Idle
    }
}

sealed class ImportState {
    object Idle : ImportState()
    data class Processing(val progress: Int, val message: String) : ImportState()
    data class Success(val bookId: String, val chapterCount: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}
