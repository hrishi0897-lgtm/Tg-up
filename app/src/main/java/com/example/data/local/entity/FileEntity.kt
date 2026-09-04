package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FileStatus {
    PENDING,
    UPLOADING,
    COMPLETED,
    DOWNLOADING,
    FAILED,
    PAUSED
}

@Entity(
    tableName = "files",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["status"]),
        Index(value = ["name"])
    ]
)
data class FileEntity(
    @PrimaryKey
    val id: String, // UUID
    val name: String,
    val folderId: String? = null, // null represents root folder
    val size: Long,
    val mimeType: String,
    val uploadDate: Long = System.currentTimeMillis(),
    val status: FileStatus = FileStatus.PENDING,
    val checksum: String, // SHA-256
    val totalChunks: Int = 1,
    val completedChunks: Int = 0,
    val manifestMessageId: Long? = null, // Telegram message ID of manifest
    val localPath: String? = null, // Local cached path if downloaded
    val errorMessage: String? = null
)
