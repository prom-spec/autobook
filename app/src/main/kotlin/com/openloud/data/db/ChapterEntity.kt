package com.openloud.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class ChapterEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val index: Int,
    val title: String,
    val textContent: String,
    val startOffset: Int,
    val estimatedDuration: Long
)
