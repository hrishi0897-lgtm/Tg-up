package com.example.domain.model

import com.example.data.local.entity.FileStatus

data class FileItem(
    val id: String,
    val name: String,
    val folderId: String?,
    val size: Long,
    val mimeType: String,
    val uploadDate: Long,
    val status: FileStatus,
    val checksum: String,
    val totalChunks: Int,
    val completedChunks: Int,
    val manifestMessageId: Long?,
    val localPath: String?,
    val errorMessage: String?
)

data class FolderItem(
    val id: String,
    val name: String,
    val parentFolderId: String?,
    val createdDate: Long,
    val itemCount: Int = 0
)

data class BreadcrumbItem(
    val id: String?, // null for Root
    val title: String
)

data class CategoryStorageBreakdown(
    val documentsBytes: Long = 0L,
    val mediaBytes: Long = 0L,
    val otherBytes: Long = 0L
)

enum class StorageCategory {
    DOCUMENTS,
    MEDIA,
    OTHER
}

fun classifyFileCategory(mimeType: String?, fileName: String): StorageCategory {
    val mime = mimeType?.lowercase() ?: ""
    val name = fileName.lowercase()
    val ext = name.substringAfterLast('.', "")

    return when {
        mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/") ||
        ext in listOf("mp4", "mkv", "mov", "avi", "webm", "mp3", "wav", "flac", "m4a", "ogg", "aac", "jpg", "jpeg", "png", "gif", "webp", "heic", "svg") -> {
            StorageCategory.MEDIA
        }
        mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") || mime.contains("sheet") || mime.contains("presentation") ||
        ext in listOf("pdf", "doc", "docx", "txt", "rtf", "ppt", "pptx", "xls", "xlsx", "csv", "epub", "md") -> {
            StorageCategory.DOCUMENTS
        }
        else -> StorageCategory.OTHER
    }
}

data class StorageStats(
    val totalBytesStored: Long = 0L,
    val fileCount: Int = 0,
    val folderCount: Int = 0,
    val breakdown: CategoryStorageBreakdown = CategoryStorageBreakdown()
)

data class TransferProgress(
    val fileId: String,
    val fileName: String,
    val isUpload: Boolean,
    val currentChunk: Int,
    val totalChunks: Int,
    val progressFraction: Float = 0f,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: FileStatus,
    val errorMessage: String? = null,
    val etaSeconds: Long? = null,
    val activeConcurrentChunks: Int = 0,
    val completedChunksCount: Int = 0
)

data class BotHealthInfo(
    val token: String,
    val isChecking: Boolean = false,
    val isHealthy: Boolean? = null,
    val username: String? = null,
    val errorMessage: String? = null,
    val lastChecked: Long = 0L
)
