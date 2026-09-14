package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a standby bot registered in advance for multi-bot resilience and recovery.
 *
 * Security: The botToken is NEVER stored in plaintext in the database.
 * It is encrypted using AES-256-GCM via EncryptedCredentialsManager before storage.
 *
 * Operation: These bots do NOT upload anything during normal operation.
 * They exist purely as passive channel members, registered in advance so they can
 * recover chunk access via copyMessage if the primary bot is banned or inaccessible.
 */
@Entity(tableName = "standby_bots")
data class StandbyBotEntity(
    @PrimaryKey val id: String, // Unique identifier (e.g. UUID)
    val encryptedToken: String, // Encrypted via EncryptedCredentialsManager (AES-256-GCM)
    val label: String, // User-facing name (e.g. "Backup Bot 1", "@my_standby_bot")
    val username: String? = null, // Telegram username (e.g. "my_backup_bot")
    val addedDate: Long = System.currentTimeMillis(),
    val isVerifiedMember: Boolean = false, // Confirmed member of the channel via getChat/getChatMember
    val lastVerifiedDate: Long? = null,
    val channelId: String? = null // Channel ID for which membership is verified
)
