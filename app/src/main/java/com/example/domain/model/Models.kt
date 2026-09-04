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

data class StorageStats(
    val totalBytesStored: Long = 0L,
    val fileCount: Int = 0,
    val folderCount: Int = 0
)

data class TransferProgress(
    val fileId: String,
    val fileName: String,
    val isUpload: Boolean,
    val currentChunk: Int,
    val totalChunks: Int,
    val progressFraction: Float,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val status: FileStatus,
    val errorMessage: String? = null
)
