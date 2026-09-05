package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    private val captionMetaAdapter = moshi.adapter(ChunkCaptionMeta::class.java)

    companion object {
        const val BASE_API_URL = "https://api.telegram.org"
        const val MANIFEST_PREFIX = "TELEVAULT_MANIFEST_V1:"
        const val CHUNK_CAPTION_PREFIX = "TELEVAULT_CHUNK:"
        private const val MAX_RETRIES = 3
        private const val BASE_BACKOFF_MS = 1000L

        fun createDefaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
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
                val response = call()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.ok && body.result != null) {
                        return Result.success(body.result)
                    } else {
                        val errorMsg = body?.description ?: "Telegram API returned empty or invalid response"
                        return Result.failure(Exception("Telegram error: $errorMsg"))
                    }
                }

                // Handle HTTP 429 (Rate Limit)
                if (response.code() == 429) {
                    val errorBody = response.errorBody()?.string()
                    var retryAfterSeconds = 5
                    if (!errorBody.isNullOrBlank()) {
                        try {
                            val parsed = moshi.adapter(TelegramResponse::class.java).fromJson(errorBody)
                            parsed?.parameters?.retryAfter?.let { retryAfterSeconds = it }
                        } catch (_: Exception) {}
                    }
                    if (attempt < MAX_RETRIES) {
                        delay((retryAfterSeconds * 1000L).coerceAtLeast(1000L))
                        continue
                    }
                    return Result.failure(
                        Exception("Telegram rate limit reached (HTTP 429). Please wait $retryAfterSeconds seconds.")
                    )
                }

                // Translate well-known HTTP error codes into human-readable messages
                val readableError = when (response.code()) {
                    400 -> "Bad Request: Check that your Chat ID is correct and that you've sent /start to your bot."
                    401 -> "Unauthorized: The Telegram Bot Token is invalid or revoked."
                    403 -> "Forbidden: Bot was blocked by the user or lacks permission to post in this chat."
                    404 -> "Not Found: Invalid bot token or endpoint URL."
                    413 -> "Payload Too Large: The file chunk exceeds Telegram's limit (~50MB)."
                    500, 502, 503, 504 -> "Telegram server is temporarily unavailable (HTTP ${response.code()}). Retrying..."
                    else -> "Telegram API error (${response.code()}): ${response.message()}"
                }

                if (response.code() in 500..504 && attempt < MAX_RETRIES) {
                    delay(currentDelay)
                    currentDelay *= 2
                    continue
                }

                return Result.failure(Exception(readableError))

            } catch (e: IOException) {
                if (attempt < MAX_RETRIES) {
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    return Result.failure(
                        Exception("Network connection failed during $actionName. Please check your internet connection: ${e.localizedMessage}")
                    )
                }
            } catch (e: Exception) {
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
     * Uploads a single binary chunk as a Telegram document with metadata caption.
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
        val captionPayload = ChunkCaptionMeta(
            fileId = fileId,
            name = fileName,
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            sha256 = chunkSha256
        )
        val captionJson = CHUNK_CAPTION_PREFIX + captionMetaAdapter.toJson(captionPayload)

        val chunkPartName = "${fileName}.chunk_${chunkIndex}_of_${totalChunks}.tpart"
        val rawRequestBody = chunkBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val countingBody = CountingRequestBody(rawRequestBody, onProgress)
        val multipart = MultipartBody.Part.createFormData("document", chunkPartName, countingBody)

        val chatIdBody = chatId.toRequestBody("text/plain".toMediaTypeOrNull())
        val captionBody = captionJson.toRequestBody("text/plain".toMediaTypeOrNull())

        return executeWithRetry("Uploading chunk ${chunkIndex + 1}/$totalChunks") {
            api.sendDocument(botUrl(token, "sendDocument"), chatIdBody, captionBody, multipart)
        }
    }

    /**
     * Uploads the final reassembly Manifest message to the Telegram chat.
     */
    suspend fun uploadManifest(
        token: String,
        chatId: String,
        manifest: FileManifest
    ): Result<TelegramMessage> {
        val manifestJson = MANIFEST_PREFIX + manifestAdapter.toJson(manifest)
        return executeWithRetry("Uploading file manifest") {
            api.sendMessage(botUrl(token, "sendMessage"), chatId, manifestJson)
        }
    }

    /**
     * Retrieves remote file path using Telegram file_id.
     */
    suspend fun getFileInfo(token: String, fileId: String): Result<TelegramRemoteFile> {
        return executeWithRetry("Fetching file metadata") {
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
                if (response.code() == 429) {
                    delay(3000L)
                    continue
                }
                if (attempt < MAX_RETRIES) {
                    delay(currentDelay)
                    currentDelay *= 2
                    continue
                }
                return Result.failure(Exception("Failed to download chunk (HTTP ${response.code()})"))
            } catch (e: Exception) {
                if (attempt < MAX_RETRIES) {
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    return Result.failure(Exception("Chunk download failed: ${e.localizedMessage}"))
                }
            }
        }
        return Result.failure(Exception("Download failed after $MAX_RETRIES retries."))
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
     * Resyncs by querying updates or checking messages in chat for Manifest JSONs.
     */
    suspend fun fetchManifestsFromChat(token: String): Result<List<FileManifest>> {
        return try {
            val response = api.getUpdates(botUrl(token, "getUpdates"), offset = null, limit = 100)
            if (response.isSuccessful && response.body()?.ok == true) {
                val updates = response.body()?.result ?: emptyList()
                val manifests = mutableListOf<FileManifest>()
                for (update in updates) {
                    val text = update.message?.text ?: update.channelPost?.text
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

        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                onProgress(bytesWritten, total)
            }
        }

        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}
