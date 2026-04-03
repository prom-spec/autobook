package com.autobook.domain.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * ZipExtractor - detects ebook files inside .zip archives and routes to the appropriate parser
 */
class ZipExtractor(
    private val pdfParser: BookParser? = null,
    private val epubParser: BookParser? = null,
    private val docxParser: BookParser? = null,
    private val fb2Parser: BookParser? = null,
    private val odtParser: BookParser? = null,
    private val mobiParser: BookParser? = null,
    private val txtParser: BookParser? = null
) : BookParser {

    companion object {
        private const val TAG = "ZipExtractor"

        private val EBOOK_EXTENSIONS = listOf(
            ".pdf", ".epub", ".docx", ".fb2", ".odt", ".mobi", ".azw", ".azw3", ".txt"
        )
    }

    override suspend fun parse(
        inputStream: InputStream,
        fileName: String,
        onProgress: ((Float) -> Unit)?
    ): ParsedBook = withContext(Dispatchers.IO) {
        try {
            // First, check if this is an FB2.zip file (special case)
            if (fileName.endsWith(".fb2.zip", ignoreCase = true)) {
                Log.d(TAG, "Detected FB2.zip file, routing to FB2 parser")
                return@withContext fb2Parser?.parse(inputStream, fileName, onProgress)
                    ?: ParsedBook(
                        title = fileName.removeSuffix(".fb2.zip"),
                        author = null,
                        chapters = emptyList()
                    )
            }

            onProgress?.invoke(0.1f)

            // For regular .zip files, find the ebook inside
            val zipInputStream = ZipInputStream(inputStream)
            var entry = zipInputStream.nextEntry

            while (entry != null) {
                val entryName = entry.name.lowercase()

                // Check if this entry is an ebook file
                val extension = EBOOK_EXTENSIONS.find { entryName.endsWith(it) }

                if (extension != null && !entry.isDirectory) {
                    Log.d(TAG, "Found ebook in ZIP: ${entry.name} (extension: $extension)")

                    // Extract to temporary location and parse
                    val tempFile = File.createTempFile("extracted_ebook", extension)
                    try {
                        tempFile.outputStream().use { output ->
                            zipInputStream.copyTo(output)
                        }

                        onProgress?.invoke(0.3f)

                        // Route to appropriate parser
                        val parser = when (extension) {
                            ".pdf" -> pdfParser
                            ".epub" -> epubParser
                            ".docx" -> docxParser
                            ".fb2" -> fb2Parser
                            ".odt" -> odtParser
                            ".mobi", ".azw", ".azw3" -> mobiParser
                            ".txt" -> txtParser
                            else -> null
                        }

                        if (parser != null) {
                            val result = parser.parse(
                                tempFile.inputStream(),
                                entry.name,
                                onProgress
                            )
                            zipInputStream.close()
                            return@withContext result
                        } else {
                            Log.w(TAG, "No parser available for $extension")
                        }
                    } finally {
                        tempFile.delete()
                    }
                }

                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }

            zipInputStream.close()

            Log.w(TAG, "No ebook found in ZIP archive")
            ParsedBook(
                title = fileName.removeSuffix(".zip"),
                author = null,
                chapters = emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting from ZIP", e)
            ParsedBook(
                title = fileName.removeSuffix(".zip"),
                author = null,
                chapters = emptyList()
            )
        }
    }
}
