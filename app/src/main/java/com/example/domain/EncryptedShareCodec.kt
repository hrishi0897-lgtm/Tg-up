package com.example.domain

import android.net.Uri
import android.util.Base64
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Self-contained Zero-Knowledge Share URI & QR Code Codec (`televault://share?payload=...`).
 *
 * Encodes a file's manifest metadata, chunk message references, and optional AES-256 decryption key
 * into a compressed URL-safe Base64 deep link that can be rendered as a QR code or shared directly.
 */
object EncryptedShareCodec {

    const val SCHEME = "televault"
    const val HOST = "share"

    data class DecodedSharedFile(
        val fileEntity: FileEntity,
        val chunks: List<ChunkEntity>,
        val expiresAt: Long?
    )

    fun encodeShareUri(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        includeEncryptionKey: Boolean = true,
        expireHours: Int = 0
    ): String {
        val root = JSONObject()
        root.put("v", 1)
        root.put("id", file.id)
        root.put("name", file.name)
        root.put("size", file.size)
        root.put("mime", file.mimeType)
        root.put("sha", file.checksum)
        root.put("tc", file.totalChunks)
        root.put("cid", file.channelId ?: "")
        root.put("mid", file.manifestMessageId ?: 0L)
        root.put("enc", file.isEncrypted)
        if (file.isEncrypted && includeEncryptionKey && !file.encryptionKeyBase64.isNullOrBlank()) {
            root.put("key", file.encryptionKeyBase64)
        }
        if (file.tags.isNotBlank()) {
            root.put("tags", file.tags)
        }
        if (expireHours > 0) {
            root.put("exp", System.currentTimeMillis() + expireHours * 3600_000L)
        }

        val chunkArr = JSONArray()
        for (c in chunks.sortedBy { it.chunkIndex }) {
            val cj = JSONObject()
            cj.put("i", c.chunkIndex)
            cj.put("m", c.telegramMessageId ?: 0L)
            cj.put("s", c.size)
            cj.put("h", c.checksum)
            if (!c.telegramFileId.isNullOrBlank()) {
                cj.put("f", c.telegramFileId)
            }
            chunkArr.put(cj)
        }
        root.put("c", chunkArr)

        val jsonBytes = root.toString().toByteArray(Charsets.UTF_8)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(jsonBytes) }
        val compressed = bos.toByteArray()
        val payload = Base64.encodeToString(
            compressed,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "$SCHEME://$HOST?payload=$payload"
    }

    fun decodeShareUri(rawInput: String, fallbackChannelId: String?): Result<DecodedSharedFile> {
        return try {
            val trimmed = rawInput.trim()
            val payload = when {
                trimmed.startsWith("$SCHEME://", ignoreCase = true) -> {
                    val uri = Uri.parse(trimmed)
                    uri.getQueryParameter("payload")
                        ?: throw IllegalArgumentException("Missing payload parameter in televault:// share link")
                }
                trimmed.contains("payload=") -> {
                    trimmed.substringAfter("payload=").substringBefore("&")
                }
                else -> trimmed
            }

            val compressed = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val jsonStr = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonStr)

            val expiresAt = if (root.has("exp")) root.optLong("exp", 0L).takeIf { it > 0L } else null
            if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                return Result.failure(IllegalStateException("This shared link expired on ${ChecksumUtil.formatDate(expiresAt)}"))
            }

            val fileId = root.getString("id")
            val name = root.getString("name")
            val size = root.getLong("size")
            val mime = root.optString("mime", "application/octet-stream")
            val sha = root.optString("sha", "")
            val totalChunks = root.optInt("tc", 1).coerceAtLeast(1)
            val cid = root.optString("cid", "").ifBlank { fallbackChannelId ?: "" }
            val mid = root.optLong("mid", 0L).takeIf { it > 0L }
            val isEncrypted = root.optBoolean("enc", false)
            val keyBase64 = root.optString("key", "").ifBlank { null }
            val tags = root.optString("tags", "Shared")

            val chunksList = mutableListOf<ChunkEntity>()
            val chunkArr = root.optJSONArray("c")
            if (chunkArr != null) {
                for (idx in 0 until chunkArr.length()) {
                    val cj = chunkArr.getJSONObject(idx)
                    chunksList.add(
                        ChunkEntity(
                            fileId = fileId,
                            chunkIndex = cj.getInt("i"),
                            channelId = cid,
                            telegramMessageId = cj.optLong("m", 0L).takeIf { it > 0L },
                            telegramFileId = cj.optString("f", "").ifBlank { null },
                            checksum = cj.optString("h", ""),
                            size = cj.optLong("s", 0L),
                            isUploaded = true,
                            isDownloaded = false
                        )
                    )
                }
            }

            val fileEntity = FileEntity(
                id = fileId,
                name = name,
                folderId = null,
                size = size,
                mimeType = mime,
                uploadDate = System.currentTimeMillis(),
                status = FileStatus.COMPLETED,
                checksum = sha,
                totalChunks = totalChunks,
                completedChunks = totalChunks,
                channelId = cid.ifBlank { null },
                manifestMessageId = mid,
                isEncrypted = isEncrypted,
                encryptionKeyBase64 = keyBase64,
                tags = if (tags.contains("Shared", ignoreCase = true)) tags else listOf(tags, "Shared").filter { it.isNotBlank() }.joinToString(",")
            )

            Result.success(DecodedSharedFile(fileEntity, chunksList, expiresAt))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid TeleVault share code or link: ${e.message}", e))
        }
    }
}
