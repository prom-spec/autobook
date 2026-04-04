package com.openloud.domain.parser

import java.io.InputStream

data class ParsedBook(
    val title: String?,
    val author: String?,
    val language: String? = null,
    val chapters: List<Chapter>
)

data class Chapter(
    val index: Int,
    val title: String,
    val textContent: String
)

interface BookParser {
    /**
     * Parse a book from an InputStream.
     * @param inputStream The input stream of the book file
     * @param fileName Original filename (used for format detection)
     * @param onProgress Optional progress callback (0.0 to 1.0)
     * @return ParsedBook with metadata and chapters
     */
    suspend fun parse(
        inputStream: InputStream,
        fileName: String,
        onProgress: ((Float) -> Unit)? = null
    ): ParsedBook
}
