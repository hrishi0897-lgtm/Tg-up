package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "chunks",
    primaryKeys = ["fileId", "chunkIndex"],
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["fileId"]),
        Index(value = ["telegramMessageId"])
    ]
)
data class ChunkEntity(
    val fileId: String,
    val chunkIndex: Int,
    val telegramMessageId: Long? = null,
    val telegramFileId: String? = null,
    val checksum: String, // SHA-256 of the chunk
    val size: Long = 0L,
    val isUploaded: Boolean = false,
    val isDownloaded: Boolean = false
)
