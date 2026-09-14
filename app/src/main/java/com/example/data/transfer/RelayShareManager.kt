package com.example.data.transfer

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.SharedFileEntity
import com.example.data.remote.TelegramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class RelayShareManager(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
    private val creds: EncryptedCredentialsManager = EncryptedCredentialsManager(context),
    private val repo: TelegramRepository = TelegramRepository.getInstance()
) {
    companion object {
        private const val TAG = "RelayShareManager"

        @Volatile
        private var INSTANCE: RelayShareManager? = null

        fun getInstance(context: Context): RelayShareManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RelayShareManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getAllSharesFlow(): Flow<List<SharedFileEntity>> {
        return db.sharedFileDao().getAllSharedFiles()
    }

    fun getSharesForFileFlow(fileId: String): Flow<List<SharedFileEntity>> {
        return db.sharedFileDao().getSharesForFile(fileId)
    }

    suspend fun getActiveShareForFile(fileId: String): SharedFileEntity? {
        return db.sharedFileDao().getActiveShareForFile(fileId)
    }

    /**
     * Creates a temporary, revocable invite link for a specific file.
     * Copies file chunks to a dedicated relay channel (or uses single-use invite link)
     * without exposing the primary bot token or private vault storage chat.
     */
    suspend fun createShare(
        fileId: String,
        expireHours: Int = 24
    ): Result<SharedFileEntity> = withContext(Dispatchers.IO) {
        try {
            val token = creds.getBotToken()
                ?: return@withContext Result.failure(IllegalStateException("No bot token configured"))
            val defaultChatId = creds.getChatId()
                ?: return@withContext Result.failure(IllegalStateException("No storage channel configured"))

            val file = db.fileDao().getById(fileId)
                ?: return@withContext Result.failure(IllegalArgumentException("File not found"))
            val chunks = db.chunkDao().getChunksForFile(fileId)

            // Determine target relay chat: use configured relay channel if present, otherwise default chat
            val relayChatId = creds.getRelayChatId() ?: file.channelId ?: defaultChatId
            val isDedicatedRelay = creds.getRelayChatId() != null && creds.getRelayChatId() != (file.channelId ?: defaultChatId)

            val copiedMessageIds = mutableListOf<Long>()

            // If a separate dedicated relay chat is used, copyMessage all chunks into it
            if (isDedicatedRelay) {
                for (chunk in chunks) {
                    val msgId = chunk.telegramMessageId
                    if (msgId != null && msgId > 0) {
                        val sourceChat = chunk.channelId ?: file.channelId ?: defaultChatId
                        val copyRes = repo.copyMessage(
                            token = token,
                            chatId = relayChatId,
                            fromChatId = sourceChat,
                            messageId = msgId
                        )
                        if (copyRes.isSuccess) {
                            copiedMessageIds.add(copyRes.getOrThrow().messageId)
                        } else {
                            Log.w(TAG, "Failed to copy chunk ${chunk.chunkIndex} to relay chat: ${copyRes.exceptionOrNull()?.message}")
                        }
                    }
                }
            }

            // Create temporary single-recipient invite link (member_limit: 1)
            val nowSec = System.currentTimeMillis() / 1000L
            val expireDateSec = if (expireHours > 0) nowSec + (expireHours * 3600L) else null

            val linkResult = repo.createChatInviteLink(
                token = token,
                chatId = relayChatId,
                name = "Share: ${file.name.take(20)}",
                expireDate = expireDateSec,
                memberLimit = 1
            )

            if (linkResult.isFailure) {
                val ex = linkResult.exceptionOrNull() ?: Exception("Failed to generate chat invite link")
                Log.e(TAG, "createChatInviteLink error: ${ex.message}", ex)
                return@withContext Result.failure(ex)
            }

            val inviteLink = linkResult.getOrThrow().inviteLink

            val shareRecord = SharedFileEntity(
                id = UUID.randomUUID().toString(),
                fileId = file.id,
                fileName = file.name,
                fileSize = file.size,
                inviteLink = inviteLink,
                createdDate = System.currentTimeMillis(),
                expiryDate = expireDateSec?.let { it * 1000L },
                revoked = false,
                relayChatId = relayChatId,
                copiedMessageIds = copiedMessageIds.joinToString(",")
            )

            db.sharedFileDao().insert(shareRecord)
            Log.i(TAG, "Successfully created share link for file ${file.name}: $inviteLink (expires in ${expireHours}h)")
            Result.success(shareRecord)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating share: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Revokes a share link and purges copied chunk messages from the relay chat immediately.
     */
    suspend fun revokeShare(shareId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val share = db.sharedFileDao().getById(shareId)
                ?: return@withContext Result.failure(IllegalArgumentException("Share not found"))

            val token = creds.getBotToken()
            if (token != null) {
                // Revoke invite link on Telegram
                try {
                    repo.revokeChatInviteLink(
                        token = token,
                        chatId = share.relayChatId,
                        inviteLink = share.inviteLink
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Warning: could not revoke Telegram invite link: ${e.message}")
                }

                // Delete copied chunk messages from relay chat
                if (share.copiedMessageIds.isNotBlank()) {
                    val msgIds = share.copiedMessageIds.split(",").mapNotNull { it.trim().toLongOrNull() }
                    for (msgId in msgIds) {
                        try {
                            repo.deleteMessage(token, share.relayChatId, msgId)
                        } catch (delEx: Exception) {
                            Log.w(TAG, "Warning: failed to delete copied message $msgId: ${delEx.message}")
                        }
                    }
                }
            }

            // Mark revoked in Room
            db.sharedFileDao().markRevoked(shareId)
            Log.i(TAG, "Successfully revoked share ${share.id} for file ${share.fileName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error revoking share: ${e.message}", e)
            Result.failure(e)
        }
    }
}
