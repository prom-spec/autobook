package com.openloud.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String?,
    val language: String? = null,
    val coverPath: String?,
    val filePath: String,
    val format: BookFormat,
    val totalChapters: Int,
    val totalDurationMs: Long? = null,
    val totalDuration: Long? = null,
    val currentChapterIndex: Int = 0,
    val currentCharOffset: Int = 0,
    val currentPositionMs: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null
)

enum class BookFormat {
    PDF, EPUB, TXT, DOCX, FB2, ODT, MOBI, ZIP
}
