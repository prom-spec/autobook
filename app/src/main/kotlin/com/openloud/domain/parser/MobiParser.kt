package com.openloud.domain.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Basic Mobipocket/PalmDOC parser for .mobi, .azw, .azw3, .bin files
 * This is a simplified parser that extracts text from MOBI format
 * Note: Full MOBI parsing is complex; this handles basic PalmDOC compression
 */
class MobiParser : BookParser {

    companion object {
        private const val TAG = "MobiParser"
        private const val MOBI_HEADER_SIZE = 232
        private const val PALM_DOC_HEADER_SIZE = 16
    }

    override suspend fun parse(
        inputStream: InputStream,
        fileName: String,
        onProgress: ((Float) -> Unit)?
    ): ParsedBook = withContext(Dispatchers.IO) {
        try {
            val bytes = inputStream.readBytes()
            inputStream.close()

            onProgress?.invoke(0.2f)

            // Check if this is a MOBI/PalmDOC file
            if (bytes.size < 78) {
                Log.e(TAG, "File too small to be a valid MOBI file")
                return@withContext ParsedBook(
                    title = fileName,
                    author = null,
                    chapters = emptyList()
                )
            }

            // Parse PDB header
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            // Skip database name (32 bytes)
            buffer.position(32)

            // Skip attributes and version (4 bytes)
            buffer.position(36)

            // Skip creation/modification/backup time (12 bytes)
            buffer.position(48)

            // Skip modification number, app info, sort info (12 bytes)
            buffer.position(60)

            // Get type and creator (8 bytes)
            val type = String(bytes, 60, 4)
            val creator = String(bytes, 64, 4)

            // Check if it's a MOBI file
            if (type != "BOOK" && creator != "MOBI") {
                Log.w(TAG, "Not a standard MOBI file, attempting best-effort parsing")
            }

            onProgress?.invoke(0.3f)

            // Extract metadata and text
            val (title, author) = extractMetadata(bytes, fileName)
            onProgress?.invoke(0.4f)

            val text = extractText(bytes, onProgress)
            onProgress?.invoke(0.9f)

            // Simple chapter detection (look for "Chapter" markers)
            val chapters = detectChapters(text)

            onProgress?.invoke(1.0f)

            ParsedBook(
                title = title,
                author = author,
                chapters = chapters
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MOBI", e)
            ParsedBook(
                title = fileName.removeSuffix(".mobi").removeSuffix(".azw").removeSuffix(".azw3").removeSuffix(".bin"),
                author = null,
                chapters = emptyList()
            )
        }
    }

    private fun extractMetadata(bytes: ByteArray, fileName: String): Pair<String?, String?> {
        return try {
            // MOBI metadata is complex; for now, use heuristics
            // Look for EXTH header (extended header with metadata)
            val mobiHeader = findMobiHeader(bytes)
            if (mobiHeader < 0) {
                return Pair(fileName.removeSuffix(".mobi").removeSuffix(".azw").removeSuffix(".azw3").removeSuffix(".bin"), null)
            }

            var title: String? = null
            var author: String? = null

            // Try to find EXTH header
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            buffer.position(mobiHeader + 0x80) // EXTH flag location

            val exthFlag = buffer.int
            if (exthFlag and 0x40 != 0) {
                // EXTH exists
                val exthStart = mobiHeader + buffer.getInt(mobiHeader + 0x14) + 0x10
                if (exthStart + 12 < bytes.size) {
                    buffer.position(exthStart)
                    val exthIdentifier = buffer.int
                    if (exthIdentifier == 0x45585448) { // "EXTH"
                        // Parse EXTH records
                        val recordCount = buffer.int
                        buffer.position(exthStart + 12)

                        for (i in 0 until minOf(recordCount, 100)) {
                            if (buffer.position() + 8 >= bytes.size) break

                            val recordType = buffer.int
                            val recordLength = buffer.int

                            if (recordLength < 8 || buffer.position() + recordLength - 8 >= bytes.size) break

                            val recordData = ByteArray(recordLength - 8)
                            buffer.get(recordData)

                            when (recordType) {
                                100 -> author = String(recordData, Charsets.UTF_8).trim()
                                503 -> title = String(recordData, Charsets.UTF_8).trim()
                            }
                        }
                    }
                }
            }

            Pair(
                title?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".mobi").removeSuffix(".azw").removeSuffix(".azw3").removeSuffix(".bin"),
                author?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting MOBI metadata", e)
            Pair(fileName.removeSuffix(".mobi").removeSuffix(".azw").removeSuffix(".azw3").removeSuffix(".bin"), null)
        }
    }

    private fun findMobiHeader(bytes: ByteArray): Int {
        // Search for "MOBI" identifier
        for (i in 0 until minOf(bytes.size - 4, 4096)) {
            if (bytes[i] == 'M'.code.toByte() &&
                bytes[i + 1] == 'O'.code.toByte() &&
                bytes[i + 2] == 'B'.code.toByte() &&
                bytes[i + 3] == 'I'.code.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun extractText(bytes: ByteArray, onProgress: ((Float) -> Unit)?): String {
        return try {
            // Find text records (usually start after header)
            // For PalmDOC format, text is in record 1 onwards
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            // Get number of records from PDB header
            buffer.position(76)
            val numRecords = buffer.short.toInt() and 0xFFFF

            if (numRecords <= 0) return ""

            // Read record list
            val recordOffsets = mutableListOf<Int>()
            buffer.position(78)

            for (i in 0 until minOf(numRecords, 1000)) {
                if (buffer.position() + 8 > bytes.size) break
                val offset = buffer.int
                recordOffsets.add(offset)
                buffer.position(buffer.position() + 4) // Skip attributes
            }

            // Extract text from records (skip record 0 which is header)
            val textBuilder = StringBuilder()
            for (i in 1 until minOf(recordOffsets.size, 1000)) {
                val start = recordOffsets[i]
                val end = if (i + 1 < recordOffsets.size) recordOffsets[i + 1] else bytes.size

                if (start >= bytes.size || end > bytes.size || start >= end) continue

                val recordData = bytes.copyOfRange(start, end)
                val text = decompressPalmDOC(recordData)
                textBuilder.append(text)

                if (i % 10 == 0) {
                    onProgress?.invoke(0.4f + (i.toFloat() / recordOffsets.size) * 0.5f)
                }
            }

            textBuilder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting MOBI text", e)
            ""
        }
    }

    private fun decompressPalmDOC(data: ByteArray): String {
        return try {
            // PalmDOC compression (compression type 2)
            val output = StringBuilder()
            var i = 0

            while (i < data.size) {
                val byte = data[i].toInt() and 0xFF

                when {
                    byte == 0 -> {
                        // Literal 0
                        output.append('\u0000')
                        i++
                    }
                    byte in 1..8 -> {
                        // Literal bytes
                        for (j in 0 until byte) {
                            if (i + 1 + j < data.size) {
                                output.append((data[i + 1 + j].toInt() and 0xFF).toChar())
                            }
                        }
                        i += byte + 1
                    }
                    byte in 0x80..0xBF -> {
                        // Compressed sequence
                        if (i + 1 < data.size) {
                            val nextByte = data[i + 1].toInt() and 0xFF
                            val distance = ((byte shl 8) or nextByte) and 0x3FFF
                            val length = (nextByte and 0x07) + 3

                            val startPos = maxOf(0, output.length - distance)
                            for (j in 0 until length) {
                                if (startPos + j < output.length) {
                                    output.append(output[startPos + j])
                                }
                            }
                            i += 2
                        } else {
                            i++
                        }
                    }
                    else -> {
                        // Regular character
                        output.append(byte.toChar())
                        i++
                    }
                }
            }

            output.toString()
        } catch (e: Exception) {
            // If decompression fails, try raw text
            Log.w(TAG, "PalmDOC decompression failed, using raw text", e)
            String(data, Charsets.ISO_8859_1)
        }
    }

    private fun detectChapters(text: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val chapterPattern = Regex("(?m)^\\s*(CHAPTER|Chapter)\\s+([IVXLCDM0-9]+)[.:]?\\s*(.*?)$")

        val matches = chapterPattern.findAll(text).toList()

        if (matches.isEmpty()) {
            // No chapters detected, return full text as single chapter
            return listOf(
                Chapter(
                    index = 0,
                    title = "Full Book",
                    textContent = text.trim()
                )
            )
        }

        for (i in matches.indices) {
            val match = matches[i]
            val start = match.range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length

            val chapterTitle = match.value.trim()
            val chapterText = text.substring(start, end).trim()

            chapters.add(
                Chapter(
                    index = i,
                    title = chapterTitle,
                    textContent = chapterText
                )
            )
        }

        return chapters
    }
}
