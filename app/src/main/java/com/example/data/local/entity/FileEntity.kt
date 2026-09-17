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
        Index(value = ["name"]),
        Index(value = ["deletedAt"])
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
    val channelId: String? = null, // Storage channel ID where file/chunks live
    val manifestMessageId: Long? = null, // Telegram message ID of manifest
    val localPath: String? = null, // Local cached path if downloaded
    val localUri: String? = null, // MediaStore or local content URI
    val errorMessage: String? = null,
    val deletedAt: Long? = null, // Timestamp when file was moved to Trash; null = active file
    val thumbnailFileId: String? = null, // Telegram document file_id for thumbnail chunk
    val thumbnailMessageId: Long? = null, // Telegram message_id for thumbnail chunk
    val thumbnailLocalPath: String? = null // Local cached path of generated thumbnail
)
