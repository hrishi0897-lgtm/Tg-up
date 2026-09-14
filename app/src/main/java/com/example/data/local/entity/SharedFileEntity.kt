package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shared_files",
    indices = [
        Index(value = ["fileId"]),
        Index(value = ["createdDate"]),
        Index(value = ["revoked"])
    ]
)
data class SharedFileEntity(
    @PrimaryKey
    val id: String, // UUID for this share record
    val fileId: String, // Reference to FileEntity.id
    val fileName: String,
    val fileSize: Long,
    val inviteLink: String,
    val createdDate: Long = System.currentTimeMillis(),
    val expiryDate: Long? = null,
    val revoked: Boolean = false,
    val relayChatId: String,
    val copiedMessageIds: String = "" // Comma-separated list of message IDs copied to relay chat
)
