package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TelegramResponse<T>(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "result") val result: T? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "error_code") val errorCode: Int? = null,
    @Json(name = "parameters") val parameters: ResponseParameters? = null
)

@JsonClass(generateAdapter = true)
data class ResponseParameters(
    @Json(name = "retry_after") val retryAfter: Int? = null,
    @Json(name = "migrate_to_chat_id") val migrateToChatId: Long? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUser(
    @Json(name = "id") val id: Long,
    @Json(name = "is_bot") val isBot: Boolean,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "username") val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramChat(
    @Json(name = "id") val id: Long,
    @Json(name = "type") val type: String,
    @Json(name = "title") val title: String? = null,
    @Json(name = "username") val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramDocument(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null
)

@JsonClass(generateAdapter = true)
data class TelegramMessage(
    @Json(name = "message_id") val messageId: Long,
    @Json(name = "date") val date: Long,
    @Json(name = "chat") val chat: TelegramChat? = null,
    @Json(name = "text") val text: String? = null,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "document") val document: TelegramDocument? = null
)

@JsonClass(generateAdapter = true)
data class TelegramRemoteFile(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "file_size") val fileSize: Long? = null,
    @Json(name = "file_path") val filePath: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUpdate(
    @Json(name = "update_id") val updateId: Long,
    @Json(name = "message") val message: TelegramMessage? = null,
    @Json(name = "channel_post") val channelPost: TelegramMessage? = null
)

/**
 * Manifest payload embedded in Telegram messages for reassembly and sync.
 */
@JsonClass(generateAdapter = true)
data class FileManifest(
    @Json(name = "fileId") val fileId: String,
    @Json(name = "name") val name: String,
    @Json(name = "size") val size: Long,
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "overallSha256") val overallSha256: String,
    @Json(name = "folderId") val folderId: String? = null,
    @Json(name = "uploadDate") val uploadDate: Long = System.currentTimeMillis(),
    @Json(name = "chunks") val chunks: List<ManifestChunk>
)

@JsonClass(generateAdapter = true)
data class ManifestChunk(
    @Json(name = "index") val index: Int,
    @Json(name = "messageId") val messageId: Long,
    @Json(name = "telegramFileId") val telegramFileId: String? = null,
    @Json(name = "sha256") val sha256: String,
    @Json(name = "size") val size: Long
)

/**
 * Metadata stored in chunk captions
 */
@JsonClass(generateAdapter = true)
data class ChunkCaptionMeta(
    @Json(name = "fileId") val fileId: String,
    @Json(name = "name") val name: String,
    @Json(name = "chunkIndex") val chunkIndex: Int,
    @Json(name = "totalChunks") val totalChunks: Int,
    @Json(name = "sha256") val sha256: String
)
