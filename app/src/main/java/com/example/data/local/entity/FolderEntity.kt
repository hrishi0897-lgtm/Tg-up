package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["parentFolderId"])
    ]
)
data class FolderEntity(
    @PrimaryKey
    val id: String, // UUID
    val name: String,
    val parentFolderId: String? = null, // null represents root
    val createdDate: Long = System.currentTimeMillis()
)
