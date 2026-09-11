package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey
    val channelId: String,
    val displayName: String = "My Vault",
    val addedDate: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
