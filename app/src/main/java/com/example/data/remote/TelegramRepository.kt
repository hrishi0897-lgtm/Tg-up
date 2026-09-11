package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import android.os.SystemClock
import android.util.Log

class TelegramRepository(
    private val okHttpClient: OkHttpClient = createDefaultOkHttpClient(),
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
) {

    private val api: TelegramApi = Retrofit.Builder()
        .baseUrl("https://api.telegram.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TelegramApi::class.java)

    private val manifestAdapter = moshi.adapter(FileManifest::class.java)
    private val vaultIndexAdapter = moshi.adapter(VaultIndex::class.java)
    private val captionMetaAdapter = moshi.adapter(ChunkCaptionMeta::class.java)
    private val errorAdapter = moshi.adapter(TelegramErrorResponse::class.java)

    fun evictConnectionPool(reason: String = "Manual eviction") {
        Companion.evictSharedConnectionPool(reason)
    }

    companion object {
        const val BASE_API_URL = "https://api.telegram.org"
        const val MANIFEST_PREFIX = "TELEVAULT_MANIFEST_V1:"
        const val MANIFEST_CAPTION_PREFIX = "MANIFEST|"
        const val CHUNK_CAPTION_PREFIX = "TELEVAULT_CHUNK:"
        const val VAULT_INDEX_CAPTION = "VAULT_INDEX"
        private const val MAX_RETRIES = 3
        private const val BASE_BACKOFF_MS = 1000L

        /**
         * Shared connection pool with 5 idle connections max and 30-second keep-alive.
         * Shorter keep-alive prevents silently dead TCP sockets from lingering on mobile networks.
         */
        val sharedConnectionPool = ConnectionPool(5, 30, TimeUnit.SECONDS)

        fun evictSharedConnectionPool(reason: String = "Manual eviction") {
            val idle = sharedConnectionPool.idleConnectionCount()
            val total = sharedConnectionPool.connectionCount()
            Log.i("TelegramRepo", ">>> [CONNECTION POOL EVICTION] Evicting connection pool ($reason). Before: total=$total, idle=$idle")
            sharedConnectionPool.evictAll()
            Log.i("TelegramRepo", "<<< [CONNECTION POOL EVICTION] Completed. After: total=${sharedConnectionPool.connectionCount()}")
        }

        fun createDefaultOkHttpClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor { message ->
                Log.d("TelegramHttp", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .connectionPool(sharedConnectionPool)
                .retryOnConnectionFailure(true)
                .pingInterval(20, TimeUnit.SECONDS)
                .eventListenerFactory { TelegramHttpEventListener() }
                .addInterceptor(logging)
                .build()
        }

        internal fun redactToken(url: String): String {
            return url.replace(Regex("/bot[^/]+/"), "/bot<REDACTED>/")
        }

        internal fun botUrl(token: String, method: String): String {
            val cleanToken = token.trim()
            return "$BASE_API_URL/bot$cleanToken/$method"
        }

        internal fun fileDownloadUrl(token: String, filePath: String): String {
            val cleanToken = token.trim()
            val cleanPath = filePath.trim().removePrefix("/")
            return "$BASE_API_URL/file/bot$cleanToken/$cleanPath"
        }
    }

    /**
     * Executes a network call with exponential backoff and rate limit (HTTP 429) handling.
     */
    private suspend fun <T> executeWithRetry(
        actionName: String,
        call: suspend () -> Response<TelegramResponse<T>>
    ): Result<T> {
        var attempt = 0
        var currentDelay = BASE_BACKOFF_MS

        while (attempt < MAX_RETRIES) {
            attempt++
            try {
                Log.i("TelegramRepo", ">>> [HTTP DISPATCH] $actionName (attempt $attempt/$MAX_RETRIES)")
                val response = call()
                Log.i("TelegramRepo", "<<< [HTTP RESPONSE] $actionName: HTTP ${response.code()} ${response.message()}, isSuccessful=${response.isSuccessful}")

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.ok && body.result != null) {
                        Log.i("TelegramRepo", "<<< [HTTP SUCCESS] $actionName: confirmed ok=true")
                        return Result.success(body.result)
                    } else {
                        val errorMsg = body?.description ?: "Telegram API returned ok=false or invalid response"
                        Log.e("TelegramRepo", "<<< [HTTP LOGICAL ERROR] $actionName: ok=false, description='$errorMsg', code=${body?.errorCode}")
                        return Result.failure(TelegramApiException(errorMsg, body?.errorCode))
                    }
                }

                // Parse error body JSON from Telegram API
                val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                Log.e("TelegramRepo", "<<< [HTTP ERROR BODY] $actionName: code=${response.code()}: $errorBody")

                var telegramDescription: String? = null
                var telegramErrorCode: Int? = null
                var retryAfterSeconds: Int? = null

                if (!errorBody.isNullOrBlank()) {
                    try {
                        val parsed = errorAdapter.fromJson(errorBody)
                        telegramDescription = parsed?.description
                        telegramErrorCode = parsed?.errorCode
                        retryAfterSeconds = parsed?.parameters?.retryAfter
                    } catch (e: Exception) {
                        Log.w("TelegramRepo", "Failed to parse error body JSON: ${e.message}")
                    }
                }

                // Handle HTTP 429 (Rate Limit)
                if (response.code() == 429) {
                    val waitSec = retryAfterSeconds ?: 5
                    Log.w("TelegramRepo", "Rate limit (HTTP 429), waiting $waitSec seconds...")
                    if (attempt < MAX_RETRIES) {
                        delay((waitSec * 1000L).coerceAtLeast(1000L))
                        continue
                    }
                    val msg = telegramDescription ?: "Telegram rate limit reached (HTTP 429). Please wait $waitSec seconds."
                    return Result.failure(TelegramApiException(msg, 429))
                }

                // Handle server errors (500, 502, 503, 504) with exponential backoff
                if (response.code() in 500..504) {
                    Log.w("TelegramRepo", "Server error ${response.code()}, backoff $currentDelay ms...")
                    if (attempt < MAX_RETRIES) {
                        delay(currentDelay)
                        currentDelay *= 2
                        continue
                    }
                    val msg = telegramDescription ?: "Telegram server is temporarily unavailable (HTTP ${response.code()})."
                    return Result.failure(TelegramApiException(msg, response.code()))
                }

                // For client errors (400, 401, 403, 404, 413, etc.), fail immediately without retrying
                // Surface the ACTUAL unmodified Telegram API description and raw body without generic masking
                val finalError = if (!telegramDescription.isNullOrBlank()) {
                    telegramDescription
                } else if (!errorBody.isNullOrBlank()) {
                    errorBody
                } else {
                    "HTTP ${response.code()}: ${response.message()}"
                }

                Log.e("TelegramRepo", "<<< [TELEGRAM CLIENT ERROR] $actionName: HTTP ${response.code()} '$finalError' | Raw response body: $errorBody")
                return Result.failure(TelegramApiException(finalError, telegramErrorCode ?: response.code(), errorBody))

            } catch (e: SocketTimeoutException) {
                Log.e("TelegramRepo", "[$actionName] Socket timeout (attempt $attempt/$MAX_RETRIES): ${e.message}. Proactively evicting connection pool to discard dead socket.", e)
                evictConnectionPool("SocketTimeout in $actionName")
                if (attempt < MAX_RETRIES) {
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    return Result.failure(
                        Exception("Network request timed out during $actionName (${e.message ?: "SocketTimeout"}). Please check your connection.")
                    )
                }
            } catch (e: InterruptedIOException) {
                val isCallTimeout = e.message?.contains("timeout", ignoreCase = true) == true
                Log.e("TelegramRepo", "[$actionName] Request timed out or interrupted (attempt $attempt/$MAX_RETRIES, isCallTimeout=$isCallTimeout): ${e.message}. Proactively evicting connection pool.", e)
                evictConnectionPool("InterruptedIOException (isCallTimeout=$isCallTimeout) in $actionName")
                if (attempt < MAX_RETRIES) {
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    return Result.failure(
                        Exception("Network request timed out during $actionName (${e.message ?: "Timeout"}).")
                    )
                }
            } catch (e: IOException) {
                Log.e("TelegramRepo", "[$actionName] Network failure (attempt $attempt/$MAX_RETRIES): ${e.message}. Proactively evicting connection pool.", e)
                evictConnectionPool("IOException in $actionName: ${e.message}")
                if (attempt < MAX_RETRIES) {
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    return Result.failure(
                        Exception("Network connection failed during $actionName: ${e.localizedMessage ?: e.message}")
                    )
                }
            } catch (e: Exception) {
                Log.e("TelegramRepo", "[$actionName] Unexpected exception: ${e.message}", e)
                return Result.failure(e)
            }
        }
        return Result.failure(Exception("$actionName failed after $MAX_RETRIES attempts."))
    }

    /**
     * Validates Bot Token credentials and checks bot account status via getMe.
     */
    suspend fun validateCredentials(token: String, chatId: String): Result<TelegramUser> {
        val userResult = executeWithRetry("Validating Bot Token") {
            api.getMe(botUrl(token, "getMe"))
        }
        if (userResult.isFailure) {
            return userResult
        }

        // Test sending a discreet greeting message to the designated Chat ID to verify permission
        val testSendResult = sendTestMessage(token, chatId)
        if (testSendResult.isFailure) {
            return Result.failure(
                Exception(
                    "Bot token is valid, but failed to communicate with Chat ID '$chatId'.\n" +
                            "Make sure you opened Telegram, searched for your bot, and clicked /start before connecting."
                )
            )
        }

        return userResult
    }

    /**
     * Sends a connection test message.
     */
    suspend fun sendTestMessage(token: String, chatId: String): Result<TelegramMessage> {
        val text = "🔒 *TeleVault Connected*\nYour personal cloud storage is ready. Encrypted chunked transfers will be securely archived in this chat."
        return executeWithRetry("Sending Test Message") {
            api.sendMessage(botUrl(token, "sendMessage"), chatId, text)
        }
    }

    /**
     * Uploads a single binary chunk from a file on disk as a Telegram document with metadata caption.
     * Logs the actual file size in bytes of the chunk file on disk immediately before it is attached to the multipart request.
     */
    suspend fun uploadChunk(
        token: String,
        chatId: String,
        fileId: String,
        fileName: String,
        chunkIndex: Int,
        totalChunks: Int,
        chunkFile: File,
        chunkSha256: String,
        expectedChunkSize: Long? = null,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): Result<TelegramMessage> {
        val targetUrl = botUrl(token, "sendDocument")
        val redactedUrl = redactToken(targetUrl)
        val chunkPartName = "${fileName}.chunk_${chunkIndex}_of_${totalChunks}.tpart"
        val actualFileLength = chunkFile.length()

        val expectedSizeMsg = if (expectedChunkSize != null) {
            " | Expected chunk size (total/chunks): $expectedChunkSize bytes | Difference: ${actualFileLength - expectedChunkSize} bytes"
        } else ""

        Log.i(
            "TelegramRepo",
            ">>> [uploadChunk DISK AUDIT & ATTACH] Target URL: $redactedUrl | File: $fileName | " +
            "Chunk: ${chunkIndex + 1}/$totalChunks | On-disk size: $actualFileLength bytes (${actualFileLength / (1024 * 1024.0)} MB)$expectedSizeMsg | " +
            "Part: $chunkPartName | ChatId: $chatId"
        )

        // Safety verification: abort if chunk exceeds Telegram Bot limit (~50MB = 52,428,800 bytes)
        if (actualFileLength > 50 * 1024 * 1024L) {
            val errMsg = "Chunk ${chunkIndex + 1} size ($actualFileLength bytes) exceeds Telegram Bot 50MB per-file upload limit!"
            Log.e("TelegramRepo", ">>> [uploadChunk ABORTED] $errMsg")
            return Result.failure(IllegalArgumentException(errMsg))
        }

        val captionPayload = ChunkCaptionMeta(
            fileId = fileId,
            name = fileName,
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            sha256 = chunkSha256
        )
        val captionJson = CHUNK_CAPTION_PREFIX + captionMetaAdapter.toJson(captionPayload)

        val rawRequestBody = chunkFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val countingBody = CountingRequestBody(rawRequestBody, onProgress)
        val multipart = MultipartBody.Part.createFormData("document", chunkPartName, countingBody)

        val chatIdBody = chatId.toRequestBody("text/plain".toMediaTypeOrNull())
        val captionBody = captionJson.toRequestBody("text/plain".toMediaTypeOrNull())

        return executeWithRetry("[Upload sendDocument] Chunk ${chunkIndex + 1}/$totalChunks of '$fileName'") {
            Log.i("TelegramRepo", ">>> [sendDocument NETWORK CALL] Requesting POST $redactedUrl with document part size $actualFileLength bytes ($chunkPartName)...")
            try {
                val resp = api.sendDocument(targetUrl, chatIdBody, captionBody, multipart)
                Log.i("TelegramRepo", "<<< [sendDocument NETWORK RESPONSE] HTTP ${resp.code()} ${resp.message()} isSuccessful=${resp.isSuccessful} for $redactedUrl")
                resp
            } catch (e: Exception) {
                Log.e("TelegramRepo", "<<< [sendDocument NETWORK EXCEPTION] ${e::class.java.simpleName}: ${e.message} for $redactedUrl", e)
                throw e
            }
        }
    }

    /**
     * Uploads a single binary chunk from an in-memory byte array as a Telegram document with metadata caption.
     */
    suspend fun uploadChunk(
        token: String,
        chatId: String,
        fileId: String,
        fileName: String,
        chunkIndex: Int,
        totalChunks: Int,
        chunkBytes: ByteArray,
        chunkSha256: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): Result<TelegramMessage> {
        val targetUrl = botUrl(token, "sendDocument")
        val redactedUrl = redactToken(targetUrl)
        val chunkPartName = "${fileName}.chunk_${chunkIndex}_of_${totalChunks}.tpart"
        Log.i("TelegramRepo", ">>> [uploadChunk START] Target URL: $redactedUrl | File: $fileName | Chunk: ${chunkIndex + 1}/$totalChunks (${chunkBytes.size} bytes) | Part: $chunkPartName | ChatId: $chatId")

        val captionPayload = ChunkCaptionMeta(
            fileId = fileId,
            name = fileName,
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            sha256 = chunkSha256
        )
        val captionJson = CHUNK_CAPTION_PREFIX + captionMetaAdapter.toJson(captionPayload)

        val rawRequestBody = chunkBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val countingBody = CountingRequestBody(rawRequestBody, onProgress)
        val multipart = MultipartBody.Part.createFormData("document", chunkPartName, countingBody)

        val chatIdBody = chatId.toRequestBody("text/plain".toMediaTypeOrNull())
        val captionBody = captionJson.toRequestBody("text/plain".toMediaTypeOrNull())

        return executeWithRetry("Uploading chunk ${chunkIndex + 1}/$totalChunks") {
            Log.i("TelegramRepo", ">>> [sendDocument NETWORK CALL] Requesting POST $redactedUrl with document part size ${chunkBytes.size} bytes...")
            try {
                val resp = api.sendDocument(targetUrl, chatIdBody, captionBody, multipart)
                Log.i("TelegramRepo", "<<< [sendDocument NETWORK RESPONSE] HTTP ${resp.code()} ${resp.message()} isSuccessful=${resp.isSuccessful} for $redactedUrl")
                resp
            } catch (e: Exception) {
                Log.e("TelegramRepo", "<<< [sendDocument NETWORK EXCEPTION] ${e::class.java.simpleName}: ${e.message} for $redactedUrl", e)
                throw e
            }
        }
    }

    /**
     * Uploads the final reassembly Manifest as a document attachment (sendDocument)
     * rather than a plain text message. This eliminates Telegram's 4096-character text limit.
     */
    suspend fun uploadManifest(
        token: String,
        chatId: String,
        manifest: FileManifest,
        manifestFile: File
    ): Result<TelegramMessage> {
        val targetUrl = botUrl(token, "sendDocument")
        val captionText = "$MANIFEST_CAPTION_PREFIX${manifest.fileId}"
        val partName = "${manifest.fileId}.manifest.json"

        Log.i(
            "TelegramRepo",
            ">>> [uploadManifest DOCUMENT ATTACH] Target URL: ${redactToken(targetUrl)} | " +
            "Manifest file: $partName | Size: ${manifestFile.length()} bytes | Caption: $captionText"
        )

        val requestBody = manifestFile.asRequestBody("application/json".toMediaTypeOrNull())
        val multipart = MultipartBody.Part.createFormData("document", partName, requestBody)
        val chatIdBody = chatId.toRequestBody("text/plain".toMediaTypeOrNull())
        val captionBody = captionText.toRequestBody("text/plain".toMediaTypeOrNull())

        return executeWithRetry("Uploading file manifest document ($partName)") {
            api.sendDocument(targetUrl, chatIdBody, captionBody, multipart)
        }
    }

    /**
     * Downloads and parses a FileManifest from a Telegram remote document file_id.
     */
    suspend fun downloadManifestDocument(token: String, fileId: String): Result<FileManifest> {
        return try {
            val fileInfoResult = getFileInfo(token, fileId)
            if (fileInfoResult.isFailure) {
                return Result.failure(fileInfoResult.exceptionOrNull() ?: Exception("Failed to get manifest file info"))
            }
            val filePath = fileInfoResult.getOrThrow().filePath
                ?: return Result.failure(Exception("Manifest getFile returned empty file_path"))

            val streamResult = downloadFileStream(token, filePath)
            if (streamResult.isFailure) {
                return Result.failure(streamResult.exceptionOrNull() ?: Exception("Failed to download manifest stream"))
            }

            val body = streamResult.getOrThrow()
            val jsonString = body.string()
            val manifest = manifestAdapter.fromJson(jsonString)
                ?: return Result.failure(Exception("Failed to deserialize manifest JSON from downloaded document"))
            Result.success(manifest)
        } catch (e: Exception) {
            Log.e("TelegramRepo", "downloadManifestDocument error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves remote file path using Telegram file_id.
     */
    suspend fun getFileInfo(token: String, fileId: String): Result<TelegramRemoteFile> {
        return executeWithRetry("[Download getFile] Resolving remote file_id=$fileId") {
            api.getFile(botUrl(token, "getFile"), fileId)
        }
    }

    /**
     * Downloads file binary stream by file path.
     */
    suspend fun downloadFileStream(token: String, filePath: String): Result<ResponseBody> {
        var attempt = 0
        var currentDelay = BASE_BACKOFF_MS

        while (attempt < MAX_RETRIES) {
            attempt++
            try {
                val response = api.downloadFile(fileDownloadUrl(token, filePath))
                if (response.isSuccessful && response.body() != null) {
                    return Result.success(response.body()!!)
                }
                val rawError = try { response.errorBody()?.string() } catch (_: Exception) { null }
                Log.e("TelegramRepo", "<<< [RAW TELEGRAM HTTP RESPONSE BODY] [Download fileStream] code=${response.code()}: $rawError")

                if (response.code() == 429) {
                    delay(3000L)
                    continue
                }
                if (attempt < MAX_RETRIES && response.code() in 500..504) {
                    delay(currentDelay)
                    currentDelay *= 2
                    continue
                }
                val errorMsg = rawError ?: "HTTP ${response.code()}: ${response.message()}"
                return Result.failure(TelegramApiException(errorMsg, response.code(), rawError))
            } catch (e: Exception) {
                Log.e("TelegramRepo", "[Download fileStream] Exception on attempt $attempt/$MAX_RETRIES: ${e.message}. Evicting connection pool.", e)
                evictConnectionPool("downloadFileStream attempt $attempt: ${e.message}")
                if (attempt < MAX_RETRIES) {
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    return Result.failure(Exception("[Download fileStream] Network error: ${e.localizedMessage}", e))
                }
            }
        }
        return Result.failure(Exception("[Download fileStream] Failed after $MAX_RETRIES retries."))
    }

    /**
     * Copies a message within the storage chat/channel or to another chat.
     * Generates a new message ID containing the document copy.
     */
    suspend fun copyMessage(
        token: String,
        chatId: String,
        fromChatId: String,
        messageId: Long
    ): Result<TelegramMessageId> {
        return executeWithRetry("Copying message $messageId") {
            api.copyMessage(botUrl(token, "copyMessage"), chatId, fromChatId, messageId)
        }
    }

    /**
     * Forwards a message from the storage channel/chat to obtain the fresh document and file_id.
     */
    suspend fun forwardMessage(
        token: String,
        chatId: String,
        fromChatId: String,
        messageId: Long
    ): Result<TelegramMessage> {
        return executeWithRetry("Forwarding message $messageId") {
            api.forwardMessage(botUrl(token, "forwardMessage"), chatId, fromChatId, messageId)
        }
    }

    /**
     * Ban-resilient file_id regeneration:
     * Given channel_id and message_id, pulls the file using copyMessage or forwardMessage
     * to obtain a fresh, valid file_id for the current active bot token.
     * If copyMessage is used, forwards or fetches the message to read document.file_id, then cleans up temporary message.
     */
    suspend fun regenerateFileId(
        token: String,
        channelId: String,
        messageId: Long
    ): Result<String> {
        return try {
            Log.i("TelegramRepo", ">>> [regenerateFileId] Regenerating file_id for channel=$channelId, messageId=$messageId...")
            // Method 1: Use forwardMessage to the channel/chat.
            // When forwarded, Telegram returns the full TelegramMessage including document.file_id bound to the active bot!
            val forwardResult = forwardMessage(token, chatId = channelId, fromChatId = channelId, messageId = messageId)
            if (forwardResult.isSuccess) {
                val fwdMsg = forwardResult.getOrThrow()
                val doc = fwdMsg.document
                val newFileId = doc?.fileId
                val tempMsgId = fwdMsg.messageId
                // Clean up the forwarded temporary message in the background
                try {
                    deleteMessage(token, channelId, tempMsgId)
                } catch (delEx: Exception) {
                    Log.w("TelegramRepo", "Non-critical: Failed to delete temporary forwarded message $tempMsgId: ${delEx.message}")
                }
                if (!newFileId.isNullOrBlank()) {
                    Log.i("TelegramRepo", ">>> [regenerateFileId] Successfully regenerated file_id via forwardMessage: $newFileId")
                    return Result.success(newFileId)
                }
            }

            // Method 2: Fallback to copyMessage
            val copyResult = copyMessage(token, chatId = channelId, fromChatId = channelId, messageId = messageId)
            if (copyResult.isSuccess) {
                val copiedMsgId = copyResult.getOrThrow().messageId
                // Clean up the copied temporary message
                try {
                    deleteMessage(token, channelId, copiedMsgId)
                } catch (_: Exception) {}
            }

            val err = forwardResult.exceptionOrNull()?.message ?: "Failed to extract file_id from forwarded/copied message"
            Result.failure(Exception(err))
        } catch (e: Exception) {
            Log.e("TelegramRepo", "regenerateFileId error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Health check to detect whether a specific bot token is working, banned, or has chat access.
     */
    suspend fun checkBotHealth(token: String, chatId: String? = null): Result<BotHealthStatus> {
        return try {
            val meResult = executeWithRetry("Checking bot status via getMe") {
                api.getMe(botUrl(token, "getMe"))
            }
            if (meResult.isFailure) {
                val ex = meResult.exceptionOrNull()
                val isBanned = ex is TelegramApiException && (ex.errorCode == 401 || ex.errorCode == 403 || ex.message?.contains("Unauthorized", ignoreCase = true) == true)
                return Result.success(
                    BotHealthStatus(
                        token = token,
                        isWorking = false,
                        isBanned = isBanned,
                        botUser = null,
                        error = ex?.message ?: "Unknown error"
                    )
                )
            }
            val user = meResult.getOrThrow()

            // If chatId is provided, verify admin or member permissions in the channel
            var hasChatAccess = true
            var chatMemberStatus: String? = null
            if (!chatId.isNullOrBlank()) {
                try {
                    val memberResponse = api.getChatMember(botUrl(token, "getChatMember"), chatId, user.id)
                    if (memberResponse.isSuccessful && memberResponse.body()?.ok == true) {
                        chatMemberStatus = memberResponse.body()?.result?.status
                        hasChatAccess = chatMemberStatus in listOf("creator", "administrator", "member")
                    }
                } catch (e: Exception) {
                    Log.w("TelegramRepo", "Chat access check non-fatal error: ${e.message}")
                }
            }

            Result.success(
                BotHealthStatus(
                    token = token,
                    isWorking = true,
                    isBanned = false,
                    botUser = user,
                    chatMemberStatus = chatMemberStatus,
                    hasChatAccess = hasChatAccess,
                    error = null
                )
            )
        } catch (e: Exception) {
            Result.success(
                BotHealthStatus(
                    token = token,
                    isWorking = false,
                    isBanned = false,
                    botUser = null,
                    error = e.message
                )
            )
        }
    }

    /**
     * Deletes a Telegram message by ID.
     */
    suspend fun deleteMessage(token: String, chatId: String, messageId: Long): Result<Boolean> {
        return executeWithRetry("Deleting remote message") {
            api.deleteMessage(botUrl(token, "deleteMessage"), chatId, messageId)
        }
    }

    /**
     * Resyncs by querying updates or checking messages in chat for Manifest documents or legacy messages.
     */
    suspend fun fetchManifestsFromChat(token: String): Result<List<FileManifest>> {
        return try {
            val response = api.getUpdates(botUrl(token, "getUpdates"), offset = null, limit = 100)
            if (response.isSuccessful && response.body()?.ok == true) {
                val updates = response.body()?.result ?: emptyList()
                val manifests = mutableListOf<FileManifest>()
                for (update in updates) {
                    val msg = update.message ?: update.channelPost ?: continue

                    // 1. Check for document attachment with MANIFEST caption
                    val caption = msg.caption
                    val doc = msg.document
                    if (doc != null && caption != null && caption.startsWith(MANIFEST_CAPTION_PREFIX)) {
                        val docResult = downloadManifestDocument(token, doc.fileId)
                        if (docResult.isSuccess) {
                            manifests.add(docResult.getOrThrow())
                            continue
                        }
                    }

                    // 2. Legacy fallback: check for text message starting with MANIFEST_PREFIX
                    val text = msg.text
                    if (text != null && text.startsWith(MANIFEST_PREFIX)) {
                        val json = text.removePrefix(MANIFEST_PREFIX).trim()
                        try {
                            manifestAdapter.fromJson(json)?.let { manifests.add(it) }
                        } catch (_: Exception) {}
                    }
                }
                Result.success(manifests)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pins a Telegram message in the chat.
     */
    suspend fun pinChatMessage(token: String, chatId: String, messageId: Long): Result<Boolean> {
        return executeWithRetry("Pinning message in chat") {
            api.pinChatMessage(botUrl(token, "pinChatMessage"), chatId, messageId, disableNotification = true)
        }
    }

    /**
     * Uploads the full vault structure index as a JSON document to Telegram chat tagged with VAULT_INDEX caption.
     * Overwrites previous index reference by deleting previousIndexMessageId so copies don't accumulate.
     * Automatically pins the new index message in the chat so any other connected device discovers it instantly.
     */
    suspend fun uploadVaultIndex(
        token: String,
        chatId: String,
        vaultIndex: VaultIndex,
        previousIndexMessageId: Long? = null
    ): Result<TelegramMessage> {
        val targetUrl = botUrl(token, "sendDocument")
        val partName = "vault_index.json"
        val captionText = VAULT_INDEX_CAPTION

        val jsonString = vaultIndexAdapter.toJson(vaultIndex)
        val requestBody = jsonString.toRequestBody("application/json".toMediaTypeOrNull())
        val multipart = MultipartBody.Part.createFormData("document", partName, requestBody)
        val chatIdBody = chatId.toRequestBody("text/plain".toMediaTypeOrNull())
        val captionBody = captionText.toRequestBody("text/plain".toMediaTypeOrNull())

        Log.i("TelegramRepo", ">>> [uploadVaultIndex] Uploading index doc to chat $chatId: ${vaultIndex.folders.size} folders, ${vaultIndex.files.size} files, ts=${vaultIndex.timestamp}")

        val uploadResult = executeWithRetry("Uploading vault index document") {
            api.sendDocument(targetUrl, chatIdBody, captionBody, multipart)
        }

        if (uploadResult.isSuccess) {
            val newMsg = uploadResult.getOrThrow()
            // Pin the new index message in the chat so all devices can discover it via getChat
            try {
                Log.i("TelegramRepo", ">>> [uploadVaultIndex] Pinning index message ${newMsg.messageId} in chat $chatId...")
                pinChatMessage(token, chatId, newMsg.messageId)
                Log.i("TelegramRepo", ">>> [uploadVaultIndex] Pinned index message ${newMsg.messageId} successfully.")
            } catch (pinEx: Exception) {
                Log.w("TelegramRepo", "Non-critical: Failed to pin vault index: ${pinEx.message}")
            }

            // Clean up previous index message
            if (previousIndexMessageId != null && previousIndexMessageId > 0 && previousIndexMessageId != newMsg.messageId) {
                try {
                    Log.i("TelegramRepo", ">>> [uploadVaultIndex] Deleting previous index message: $previousIndexMessageId")
                    deleteMessage(token, chatId, previousIndexMessageId)
                } catch (delEx: Exception) {
                    Log.w("TelegramRepo", "Non-critical: Failed to delete previous index message $previousIndexMessageId: ${delEx.message}")
                }
            }
        }

        return uploadResult
    }

    /**
     * Downloads and parses VaultIndex from Telegram remote document file_id.
     */
    suspend fun downloadVaultIndexDocument(token: String, fileId: String): Result<VaultIndex> {
        return try {
            val fileInfoResult = getFileInfo(token, fileId)
            if (fileInfoResult.isFailure) {
                return Result.failure(fileInfoResult.exceptionOrNull() ?: Exception("Failed to get vault index file info"))
            }
            val filePath = fileInfoResult.getOrThrow().filePath
                ?: return Result.failure(Exception("Vault index getFile returned empty file_path"))

            val streamResult = downloadFileStream(token, filePath)
            if (streamResult.isFailure) {
                return Result.failure(streamResult.exceptionOrNull() ?: Exception("Failed to download vault index stream"))
            }

            val jsonString = streamResult.getOrThrow().string()
            val index = vaultIndexAdapter.fromJson(jsonString)
                ?: return Result.failure(Exception("Failed to deserialize VaultIndex JSON from downloaded document"))
            Result.success(index)
        } catch (e: Exception) {
            Log.e("TelegramRepo", "downloadVaultIndexDocument error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Finds the latest VAULT_INDEX message in the chat:
     * 1. Primary: checks chat's pinned_message via getChat(chatId) which contains the pinned VAULT_INDEX.
     * 2. Secondary fallback: checks getUpdates for any VAULT_INDEX messages.
     * Downloads and returns the parsed VaultIndex along with its message ID.
     */
    suspend fun fetchLatestVaultIndex(token: String, chatId: String): Result<Pair<VaultIndex, Long>?> {
        return try {
            // 1. Primary check: check pinned message in chat
            Log.i("TelegramRepo", ">>> [fetchLatestVaultIndex] Step 1: Checking getChat pinned_message for chat $chatId...")
            val chatResponse = api.getChat(botUrl(token, "getChat"), chatId)
            if (chatResponse.isSuccessful && chatResponse.body()?.ok == true) {
                val chat = chatResponse.body()?.result
                val pinnedMsg = chat?.pinnedMessage
                if (pinnedMsg != null) {
                    val caption = pinnedMsg.caption?.trim()
                    val doc = pinnedMsg.document
                    val fileName = doc?.fileName
                    Log.i("TelegramRepo", ">>> [fetchLatestVaultIndex] Chat has pinned message msgId=${pinnedMsg.messageId}, caption='$caption', docFileName='$fileName'")
                    if (doc != null && (caption == VAULT_INDEX_CAPTION || caption?.startsWith(VAULT_INDEX_CAPTION) == true || fileName == "vault_index.json")) {
                        Log.i("TelegramRepo", ">>> [fetchLatestVaultIndex] Found VAULT_INDEX in pinned message! Downloading document fileId=${doc.fileId}...")
                        val downloadResult = downloadVaultIndexDocument(token, doc.fileId)
                        if (downloadResult.isSuccess) {
                            return Result.success(Pair(downloadResult.getOrThrow(), pinnedMsg.messageId))
                        } else {
                            Log.e("TelegramRepo", "Failed to download pinned vault index: ${downloadResult.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    Log.i("TelegramRepo", ">>> [fetchLatestVaultIndex] No pinned message in chat $chatId.")
                }
            } else {
                val err = chatResponse.errorBody()?.string()
                Log.w("TelegramRepo", ">>> [fetchLatestVaultIndex] getChat for $chatId returned HTTP ${chatResponse.code()}: $err")
            }

            // 2. Secondary fallback: check getUpdates in case index was forwarded or not yet pinned
            Log.i("TelegramRepo", ">>> [fetchLatestVaultIndex] Step 2: Checking getUpdates fallback...")
            val response = api.getUpdates(botUrl(token, "getUpdates"), offset = null, limit = 100)
            if (response.isSuccessful && response.body()?.ok == true) {
                val updates = response.body()?.result ?: emptyList()
                val indexMessages = mutableListOf<TelegramMessage>()
                for (update in updates) {
                    val msg = update.message ?: update.channelPost ?: continue
                    val caption = msg.caption?.trim()
                    val doc = msg.document
                    if (doc != null && (caption == VAULT_INDEX_CAPTION || caption?.startsWith(VAULT_INDEX_CAPTION) == true || doc.fileName == "vault_index.json")) {
                        indexMessages.add(msg)
                    }
                }

                if (indexMessages.isNotEmpty()) {
                    val latestMsg = indexMessages.maxByOrNull { it.messageId } ?: indexMessages.last()
                    val doc = latestMsg.document
                    if (doc != null) {
                        Log.i("TelegramRepo", ">>> [fetchLatestVaultIndex] Found VAULT_INDEX in getUpdates! msgId=${latestMsg.messageId}, fileId=${doc.fileId}")
                        val downloadResult = downloadVaultIndexDocument(token, doc.fileId)
                        if (downloadResult.isSuccess) {
                            // Automatically pin it now so future syncs find it via getChat
                            try {
                                pinChatMessage(token, chatId, latestMsg.messageId)
                            } catch (_: Exception) {}
                            return Result.success(Pair(downloadResult.getOrThrow(), latestMsg.messageId))
                        }
                    }
                }
            }

            Log.i("TelegramRepo", ">>> [fetchLatestVaultIndex] No VAULT_INDEX found in chat $chatId.")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("TelegramRepo", "fetchLatestVaultIndex error: ${e.message}", e)
            Result.failure(e)
        }
    }
}

/**
 * RequestBody wrapper that tracks write progress for upload speed and percent calculation.
 */
class CountingRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType() = delegate.contentType()

    override fun contentLength(): Long = try {
        delegate.contentLength()
    } catch (_: IOException) {
        -1L
    }

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var bytesWritten = 0L
        var lastReportedTime = 0L

        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                val now = SystemClock.elapsedRealtime()
                // Throttle progress updates to roughly 8-10 per second per chunk (~100ms interval) or completion
                if (now - lastReportedTime >= 100L || bytesWritten >= total) {
                    lastReportedTime = now
                    onProgress(bytesWritten, total)
                }
            }
        }

        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
        // Ensure completion callback is always delivered
        if (bytesWritten >= total && lastReportedTime != 0L) {
            onProgress(bytesWritten, total)
        }
    }
}

/**
 * Diagnostics EventListener for OkHttp that logs network connection lifecycle,
 * connection pool acquisitions, timeouts, and request/response durations.
 */
class TelegramHttpEventListener : EventListener() {
    private var callStartTime: Long = 0L
    private var callUrl: String = ""

    override fun callStart(call: Call) {
        callStartTime = SystemClock.elapsedRealtime()
        callUrl = TelegramRepository.redactToken(call.request().url.toString())
        val callTimeoutMs = call.timeout().timeoutNanos() / 1_000_000
        Log.i(
            "TelegramHttpEvent",
            ">>> [CALL START] ${call.request().method} $callUrl | callTimeout=${callTimeoutMs}ms"
        )
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        val socket = try { connection.socket().remoteSocketAddress?.toString() } catch (_: Exception) { "unknown" }
        Log.i(
            "TelegramHttpEvent",
            ">>> [CONN ACQUIRED] socket=$socket protocol=${connection.protocol()} for $callUrl"
        )
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        Log.d("TelegramHttpEvent", "<<< [CONN RELEASED] protocol=${connection.protocol()} for $callUrl")
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        Log.i("TelegramHttpEvent", ">>> [CONNECT START] $inetSocketAddress")
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        Log.i("TelegramHttpEvent", "<<< [CONNECT END] $inetSocketAddress protocol=$protocol")
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
        Log.w(
            "TelegramHttpEvent",
            "!!! [CONNECT FAILED] $inetSocketAddress: ${ioe::class.java.simpleName} - ${ioe.message}"
        )
    }

    override fun requestHeadersStart(call: Call) {
        Log.d("TelegramHttpEvent", ">>> [REQUEST HEADERS START] $callUrl")
    }

    override fun requestBodyStart(call: Call) {
        Log.i("TelegramHttpEvent", ">>> [REQUEST BODY START] $callUrl")
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        val elapsed = SystemClock.elapsedRealtime() - callStartTime
        Log.i("TelegramHttpEvent", "<<< [REQUEST BODY END] byteCount=$byteCount in ${elapsed}ms for $callUrl")
    }

    override fun responseHeadersStart(call: Call) {
        val elapsed = SystemClock.elapsedRealtime() - callStartTime
        Log.i("TelegramHttpEvent", "<<< [RESPONSE HEADERS START] in ${elapsed}ms for $callUrl")
    }

    override fun callEnd(call: Call) {
        val elapsed = SystemClock.elapsedRealtime() - callStartTime
        Log.i("TelegramHttpEvent", "<<< [CALL SUCCESS] totalDuration=${elapsed}ms for $callUrl")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val elapsed = SystemClock.elapsedRealtime() - callStartTime
        val isTimeout = ioe is SocketTimeoutException ||
                ioe is InterruptedIOException ||
                ioe.message?.contains("timeout", ignoreCase = true) == true ||
                ioe.message?.contains("Canceled", ignoreCase = true) == true

        Log.e(
            "TelegramHttpEvent",
            "!!! [CALL FAILED / TIMEOUT] elapsed=${elapsed}ms | isTimeout=$isTimeout | " +
            "exception=${ioe::class.java.name}: ${ioe.message} for $callUrl",
            ioe
        )
    }
}

