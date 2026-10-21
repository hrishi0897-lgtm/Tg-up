package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_folders")
data class SyncFolderEntity(
    @PrimaryKey
    val id: String,
    val localTreeUri: String,
    val localDisplayName: String,
    val targetVaultFolderId: String? = null,
    val targetVaultFolderName: String = "Vault Root",
    val lastSyncedAt: Long = 0L,
    val autoSyncEnabled: Boolean = true,
    val encryptOnSync: Boolean = true,
    val defaultTags: String = "Synced",
    val syncedFilesCount: Int = 0,
    val syncedSignaturesJson: String = "{}" // JSON map of relativeName+size -> fileId
)
