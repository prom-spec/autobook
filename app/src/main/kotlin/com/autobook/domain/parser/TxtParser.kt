package com.autobook.domain.parser

import java.io.File

class TxtParser {

    suspend fun extractText(filePath: String): String {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }
        return file.readText()
    }

    suspend fun extractMetadata(filePath: String): Pair<String?, String?> {
        // For TXT files, derive title from filename
        val file = File(filePath)
        val title = file.nameWithoutExtension
        return title to null // No author info in plain text
    }
}
