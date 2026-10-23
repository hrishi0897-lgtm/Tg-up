package com.example.data.transfer

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.remote.TelegramApiException
import com.example.data.remote.TelegramRepository
import com.example.domain.CryptoStreamEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Live telemetry for the zero-download RAM sliding window.
 */
data class EphemeralBufferStats(
    val allocatedRamBytes: Long = 0L,
    val maxWindowBytes: Long = EphemeralStreamManager.MAX_RAM_WINDOW_BYTES,
    val cachedChunkIndices: List<Int> = emptyList(),
    val activeChunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val isFetchingChunk: Boolean = false,
    val isRefreshingLink: Boolean = false,
    val refreshedLinksCount: Int = 0,
    val statusLabel: String = "RAM Stream Ready"
)

/**
 * Cached Telegram `getFile` link entry with TTL tracking.
 * Telegram Bot API download links (`https://api.telegram.org/file/bot<token>/<file_path>`)
 * expire after ~1 hour. We enforce a 45-minute proactive TTL plus automatic reactive
 * re-requesting whenever a download returns HTTP 400/401/403/404.
 */
private data class ResolvedTelegramLink(
    val token: String,
    val fileId: String,
    val filePath: String,
    val resolvedAtMs: Long
)

/**
 * Ephemeral Zero-Download RAM Sliding-Window Stream Engine.
 *
 * - Streams and decrypts Telegram chunks strictly in RAM within a 20–50 MB sliding window
 *   ([MAX_RAM_WINDOW_BYTES] = 36 MB, holding the active 18 MB chunk + 1 lookahead/adjacent chunk).
 * - Zero-fills (`ByteArray.fill(0)`) evicted and wiped buffers immediately so sensitive plaintext
 *   never lingers in memory or on disk.
 * - Transparently handles expiring Telegram file links by re-requesting `getFile` (and falling back
 *   to server-side `regenerateFileId` + multi-bot token rotation if a bot token or `file_id` expired).
 * - Guarantees cleanup on viewer exit, app backgrounding (`ON_STOP`), and app startup crash recovery.
 */
class EphemeralStreamManager private constructor(
    private val context: Context,
    private val database: AppDatabase,
    private val credentialsManager: EncryptedCredentialsManager,
    private val repository: TelegramRepository
) {
    companion object {
        private const val TAG = "EphemeralStreamMgr"

        /**
         * 36 MB RAM sliding window cap (within the 20–50 MB budget).
         * Holds up to two 18 MB decrypted chunks in memory simultaneously (active chunk + 1 lookahead).
         */
        const val MAX_RAM_WINDOW_BYTES: Long = 36L * 1024L * 1024L

        /**
         * Proactive TTL for Telegram `file_path` URLs (45 minutes; Telegram links expire after ~60 mins).
         */
        private const val LINK_TTL_MS: Long = 45L * 60L * 1000L

        /**
         * Ephemeral fallback directory name purged on startup crash recovery, background, and exit.
         */
        private const val EPHEMERAL_TEMP_DIR_NAME = ".ephemeral_stream_tmp"

        @Volatile
        private var INSTANCE: EphemeralStreamManager? = null

        fun getInstance(context: Context): EphemeralStreamManager {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                INSTANCE ?: EphemeralStreamManager(
                    appCtx,
                    AppDatabase.getInstance(appCtx),
                    EncryptedCredentialsManager(appCtx),
                    TelegramRepository.getInstance()
                ).also { INSTANCE = it }
            }
        }

        /**
         * Startup crash recovery: purges any orphaned ephemeral stream buffers on disk
         * and zeroes out any in-memory window if an instance is active.
         */
        fun clearOrphanedEphemeralBuffers(context: Context) {
            try {
                val orphanDir = File(context.applicationContext.cacheDir, EPHEMERAL_TEMP_DIR_NAME)
                if (orphanDir.exists()) {
                    orphanDir.walkBottomUp().forEach { file ->
                        if (file.isFile) {
                            try {
                                // Overwrite with zeros before deleting if small
                                if (file.length() in 1..(64 * 1024)) {
                                    file.writeBytes(ByteArray(file.length().toInt()))
                                }
                            } catch (_: Throwable) {}
                        }
                        file.delete()
                    }
                    orphanDir.deleteRecursively()
                    Log.i(TAG, "Crash recovery: purged orphaned ephemeral stream directory")
                }
                INSTANCE?.wipeAllEphemeralBuffers("Startup crash recovery check")
            } catch (e: Throwable) {
                Log.w(TAG, "Non-fatal error during clearOrphanedEphemeralBuffers: ${e.message}")
            }
        }
    }

    private val streamJob = SupervisorJob()
    private val streamScope = CoroutineScope(Dispatchers.IO + streamJob)

    private val cacheLock = Any()

    // Access-ordered LRU sliding window of decrypted plaintext chunks: key = "$fileId:$chunkIndex"
    private val ramWindowCache = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {}
    private var currentRamBytes: Long = 0L

    // Resolved Telegram file_path links with timestamp
    private val resolvedLinks = ConcurrentHashMap<String, ResolvedTelegramLink>()

    // Deduplication for concurrent chunk fetches
    private val inFlightMutex = Mutex()
    private val inFlightChunks = ConcurrentHashMap<String, Deferred<ByteArray>>()

    private val _bufferStats = MutableStateFlow(EphemeralBufferStats())
    val bufferStats: StateFlow<EphemeralBufferStats> = _bufferStats.asStateFlow()

    /**
     * Loads the ordered list of uploaded chunks for [fileId] from Room.
     */
    suspend fun getOrderedChunks(fileId: String): List<ChunkEntity> = withContext(Dispatchers.IO) {
        database.chunkDao().getChunksForFile(fileId).sortedBy { it.chunkIndex }
    }

    /**
     * Reads up to [length] plaintext bytes starting at [globalPosition] across chunk boundaries
     * into [buffer] at [offset]. Returns the number of bytes read, or -1 if at EOF.
     */
    suspend fun readStreamRange(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        globalPosition: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int = withContext(Dispatchers.IO) {
        if (length <= 0) return@withContext 0
        if (chunks.isEmpty()) return@withContext -1

        val totalSize = computeTotalPlaintextSize(file, chunks)
        if (globalPosition >= totalSize) return@withContext -1

        // Locate which chunk contains globalPosition
        val (chunkIndex, offsetInChunk) = resolveChunkAndOffset(file, chunks, globalPosition)
        if (chunkIndex < 0 || chunkIndex >= chunks.size) return@withContext -1

        val chunkBytes = getChunkPlaintextBytes(file, chunks, chunkIndex)
        if (offsetInChunk >= chunkBytes.size) {
            // If current chunk was slightly smaller than estimated, advance to next chunk if available
            if (chunkIndex + 1 < chunks.size) {
                val nextChunkBytes = getChunkPlaintextBytes(file, chunks, chunkIndex + 1)
                if (nextChunkBytes.isEmpty()) return@withContext -1
                val toCopy = minOf(length, nextChunkBytes.size)
                System.arraycopy(nextChunkBytes, 0, buffer, offset, toCopy)
                return@withContext toCopy
            }
            return@withContext -1
        }

        val availableInChunk = chunkBytes.size - offsetInChunk.toInt()
        val bytesToCopy = minOf(length, availableInChunk)
        System.arraycopy(chunkBytes, offsetInChunk.toInt(), buffer, offset, bytesToCopy)

        // Trigger lookahead prefetch of the next chunk once playback crosses 65% of the active chunk
        val progressInChunk = (offsetInChunk + bytesToCopy).toFloat() / chunkBytes.size.coerceAtLeast(1).toFloat()
        if (progressInChunk >= 0.65f && chunkIndex + 1 < chunks.size) {
            prefetchChunkAsync(file, chunks, chunkIndex + 1)
        }

        updateStatsSnapshot(
            activeChunkIndex = chunkIndex,
            totalChunks = chunks.size,
            isFetching = false,
            statusLabel = "RAM Stream • Chunk ${chunkIndex + 1}/${chunks.size}"
        )

        bytesToCopy
    }

    /**
     * Fetches (or returns from the 36 MB RAM sliding window) the full decrypted plaintext bytes
     * for [chunkIndex]. Deduplicates concurrent requests for the same chunk.
     */
    suspend fun getChunkPlaintextBytes(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int
    ): ByteArray = withContext(Dispatchers.IO) {
        val key = "${file.id}:$chunkIndex"

        synchronized(cacheLock) {
            ramWindowCache[key]?.let { cached ->
                return@withContext cached
            }
        }

        val deferred = inFlightMutex.withLock {
            synchronized(cacheLock) {
                ramWindowCache[key]?.let { cached ->
                    return@withContext cached
                }
            }
            inFlightChunks[key]?.takeIf { it.isActive } ?: streamScope.async {
                try {
                    fetchAndDecryptChunkToRam(file, chunks, chunkIndex)
                } finally {
                    inFlightChunks.remove(key)
                }
            }.also { inFlightChunks[key] = it }
        }

        deferred.await()
    }

    /**
     * Warms [chunkIndex] in the background into the RAM sliding window if not already cached.
     */
    fun prefetchChunkAsync(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int
    ) {
        if (chunkIndex < 0 || chunkIndex >= chunks.size) return
        val key = "${file.id}:$chunkIndex"
        synchronized(cacheLock) {
            if (ramWindowCache.containsKey(key)) return
        }
        if (inFlightChunks.containsKey(key)) return

        streamScope.launch {
            try {
                getChunkPlaintextBytes(file, chunks, chunkIndex)
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.d(TAG, "Lookahead prefetch skipped for chunk $chunkIndex: ${e.message}")
            }
        }
    }

    /**
     * Downloads the raw chunk bytes from Telegram into RAM, automatically re-requesting
     * expired `getFile` links on HTTP 400/401/403/404, decrypts AES-256-GCM in memory if encrypted,
     * and inserts the plaintext into the 36 MB LRU RAM sliding window.
     */
    private suspend fun fetchAndDecryptChunkToRam(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int
    ): ByteArray {
        val chunk = chunks.getOrNull(chunkIndex)
            ?: throw IllegalArgumentException("Chunk index $chunkIndex out of bounds (total=${chunks.size})")

        updateStatsSnapshot(
            activeChunkIndex = chunkIndex,
            totalChunks = chunks.size,
            isFetching = true,
            statusLabel = if (file.isEncrypted) {
                "Decrypting Chunk ${chunkIndex + 1}/${chunks.size} in RAM…"
            } else {
                "Buffering Chunk ${chunkIndex + 1}/${chunks.size} in RAM…"
            }
        )

        var lastException: Throwable? = null
        var forceLinkRefresh = false

        for (attempt in 1..3) {
            try {
                val resolvedLink = resolveTelegramFileLink(
                    file = file,
                    chunk = chunk,
                    forceRefresh = forceLinkRefresh
                )

                val streamResult = repository.downloadFileStreamWithRange(
                    token = resolvedLink.token,
                    filePath = resolvedLink.filePath,
                    startOffset = 0L
                )

                if (streamResult.isFailure) {
                    val ex = streamResult.exceptionOrNull()
                    if (isLinkExpiredOrAuthError(ex)) {
                        Log.w(TAG, "Telegram file link expired or unauthorized on chunk $chunkIndex (attempt $attempt). Re-requesting fresh link...")
                        resolvedLinks.remove("${file.id}:${chunk.chunkIndex}")
                        forceLinkRefresh = true
                        updateStatsSnapshot(
                            activeChunkIndex = chunkIndex,
                            totalChunks = chunks.size,
                            isFetching = true,
                            isRefreshingLink = true,
                            incrementRefreshedLinks = true,
                            statusLabel = "Refreshing expired Telegram link…"
                        )
                        continue
                    }
                    throw ex ?: IllegalStateException("Failed to stream chunk $chunkIndex")
                }

                val body = streamResult.getOrThrow().body
                val rawBytes = body.use { responseBody ->
                    val expectedCap = if (chunk.size in 1..(22 * 1024 * 1024)) chunk.size.toInt() else 1024 * 1024
                    val baos = ByteArrayOutputStream(expectedCap)
                    responseBody.byteStream().use { input ->
                        val tempBuf = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(tempBuf).also { read = it } != -1) {
                            baos.write(tempBuf, 0, read)
                        }
                        tempBuf.fill(0)
                    }
                    baos.toByteArray()
                }

                val plainBytes = if (file.isEncrypted && !file.encryptionKeyBase64.isNullOrBlank()) {
                    try {
                        CryptoStreamEngine.decryptChunkBytes(rawBytes, file.encryptionKeyBase64)
                    } finally {
                        // Zero-fill raw ciphertext buffer immediately
                        rawBytes.fill(0)
                    }
                } else {
                    rawBytes
                }

                storeInRamSlidingWindow("${file.id}:$chunkIndex", plainBytes)

                updateStatsSnapshot(
                    activeChunkIndex = chunkIndex,
                    totalChunks = chunks.size,
                    isFetching = false,
                    isRefreshingLink = false,
                    statusLabel = "RAM Stream • Chunk ${chunkIndex + 1}/${chunks.size}"
                )

                return plainBytes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastException = e
                Log.w(TAG, "Attempt $attempt/3 failed to stream chunk $chunkIndex: ${e.message}")
                resolvedLinks.remove("${file.id}:${chunk.chunkIndex}")
                forceLinkRefresh = true
            }
        }

        updateStatsSnapshot(
            activeChunkIndex = chunkIndex,
            totalChunks = chunks.size,
            isFetching = false,
            isRefreshingLink = false,
            statusLabel = "Stream Error on Chunk ${chunkIndex + 1}"
        )
        throw IllegalStateException(
            lastException?.message ?: "Unable to stream chunk ${chunkIndex + 1} from Telegram",
            lastException
        )
    }

    /**
     * Resolves a fresh Telegram `file_path` for [chunk], automatically re-requesting `getFile`
     * if the cached URL exceeded its 45-minute TTL or if [forceRefresh] is true.
     * Also performs ban-resilient `regenerateFileId` recovery if `file_id` is missing or expired.
     */
    private suspend fun resolveTelegramFileLink(
        file: FileEntity,
        chunk: ChunkEntity,
        forceRefresh: Boolean
    ): ResolvedTelegramLink {
        val linkKey = "${file.id}:${chunk.chunkIndex}"
        val now = System.currentTimeMillis()

        if (!forceRefresh) {
            val cached = resolvedLinks[linkKey]
            if (cached != null && (now - cached.resolvedAtMs) < LINK_TTL_MS) {
                return cached
            }
        }

        val primaryToken = credentialsManager.getBotToken()
            ?: throw IllegalStateException("No Telegram bot token configured")
        val loadBalancer = MultiBotLoadBalancer.getInstance(context)
        var currentToken = loadBalancer.acquireBotForChunk(primaryToken)

        try {
            val chunkChannelId = chunk.channelId.ifBlank {
                file.channelId ?: credentialsManager.getChatId() ?: ""
            }
            val chunkMessageId = chunk.telegramMessageId
            var activeFileId = chunk.telegramFileId

            if (activeFileId.isNullOrBlank() && chunkChannelId.isNotBlank() && chunkMessageId != null) {
                val regen = repository.regenerateFileId(currentToken, chunkChannelId, chunkMessageId)
                if (regen.isSuccess) {
                    activeFileId = regen.getOrThrow()
                    database.chunkDao().updateRemoteFileId(file.id, chunk.chunkIndex, activeFileId)
                }
            }

            val targetFileId = activeFileId
                ?: throw IllegalStateException("Missing Telegram file_id for chunk ${chunk.chunkIndex + 1}")

            var fileInfoResult = repository.getFileInfo(currentToken, targetFileId)

            if (fileInfoResult.isFailure) {
                val rawEx = fileInfoResult.exceptionOrNull()
                if (isLinkExpiredOrAuthError(rawEx)) {
                    Log.w(TAG, "getFileInfo failed for chunk ${chunk.chunkIndex} (${rawEx?.message}). Refreshing token / file_id...")
                    updateStatsSnapshot(
                        activeChunkIndex = chunk.chunkIndex,
                        totalChunks = file.totalChunks,
                        isFetching = true,
                        isRefreshingLink = true,
                        incrementRefreshedLinks = true,
                        statusLabel = "Re-requesting Telegram file link…"
                    )

                    val rotated = credentialsManager.rotateToNextToken(currentToken)
                    if (rotated != null && rotated != currentToken) {
                        loadBalancer.releaseBotForChunk(currentToken, 0L, false)
                        currentToken = loadBalancer.acquireBotForChunk(rotated)
                    }

                    if (chunkChannelId.isNotBlank() && chunkMessageId != null) {
                        val regenResult = repository.regenerateFileId(currentToken, chunkChannelId, chunkMessageId)
                        if (regenResult.isSuccess) {
                            val freshId = regenResult.getOrThrow()
                            activeFileId = freshId
                            database.chunkDao().updateRemoteFileId(file.id, chunk.chunkIndex, freshId)
                            fileInfoResult = repository.getFileInfo(currentToken, freshId)
                        }
                    } else {
                        fileInfoResult = repository.getFileInfo(currentToken, targetFileId)
                    }
                }
            }

            val remoteFile = fileInfoResult.getOrElse { ex ->
                throw IllegalStateException("Telegram getFile failed: ${ex.message}", ex)
            }
            val filePath = remoteFile.filePath
                ?: throw IllegalStateException("Telegram getFile returned empty file_path")

            loadBalancer.releaseBotForChunk(currentToken, chunk.size, true)

            val resolved = ResolvedTelegramLink(
                token = currentToken,
                fileId = activeFileId ?: targetFileId,
                filePath = filePath,
                resolvedAtMs = System.currentTimeMillis()
            )
            resolvedLinks[linkKey] = resolved
            return resolved
        } catch (e: Throwable) {
            loadBalancer.releaseBotForChunk(currentToken, 0L, false)
            throw e
        }
    }

    private fun isLinkExpiredOrAuthError(ex: Throwable?): Boolean {
        if (ex == null) return false
        val msg = ex.message ?: ""
        if (ex is TelegramApiException) {
            if (ex.errorCode in listOf(400, 401, 403, 404)) return true
        }
        return msg.contains("400") ||
            msg.contains("401") ||
            msg.contains("403") ||
            msg.contains("404") ||
            msg.contains("file_id", ignoreCase = true) ||
            msg.contains("FILE_ID_INVALID", ignoreCase = true) ||
            msg.contains("wrong file identifier", ignoreCase = true) ||
            msg.contains("Unauthorized", ignoreCase = true) ||
            msg.contains("Not Found", ignoreCase = true)
    }

    /**
     * Inserts [plainBytes] into the LRU RAM sliding window, evicting and zero-filling
     * older chunks until total RAM usage is within [MAX_RAM_WINDOW_BYTES] (36 MB).
     */
    private fun storeInRamSlidingWindow(key: String, plainBytes: ByteArray) {
        synchronized(cacheLock) {
            ramWindowCache.remove(key)?.let { previous ->
                currentRamBytes = (currentRamBytes - previous.size).coerceAtLeast(0L)
                if (previous !== plainBytes) {
                    previous.fill(0)
                }
            }

            // Evict least-recently-used chunks until the new chunk fits inside the 36 MB window
            val iterator = ramWindowCache.entries.iterator()
            while (iterator.hasNext() && (currentRamBytes + plainBytes.size > MAX_RAM_WINDOW_BYTES) && ramWindowCache.isNotEmpty()) {
                val eldest = iterator.next()
                val evictedBytes = eldest.value
                currentRamBytes = (currentRamBytes - evictedBytes.size).coerceAtLeast(0L)
                // Zero-fill evicted plaintext buffer immediately
                evictedBytes.fill(0)
                iterator.remove()
                Log.d(TAG, "Evicted & zero-filled chunk ${eldest.key} (${evictedBytes.size / 1024} KB) from RAM sliding window")
            }

            ramWindowCache[key] = plainBytes
            currentRamBytes += plainBytes.size
        }
    }

    /**
     * Computes total plaintext stream size across all chunks.
     */
    fun computeTotalPlaintextSize(file: FileEntity, chunks: List<ChunkEntity>): Long {
        if (file.size > 0L) return file.size
        val sum = chunks.sumOf { it.size }
        return if (sum > 0L) sum else 0L
    }

    /**
     * Maps a global byte offset [globalPosition] to `(chunkIndex, offsetInChunk)`.
     */
    private fun resolveChunkAndOffset(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        globalPosition: Long
    ): Pair<Int, Long> {
        if (chunks.size == 1) {
            return 0 to globalPosition.coerceAtLeast(0L)
        }

        // Check if chunks have explicit byte sizes populated
        val allHaveSizes = chunks.all { it.size > 0L }
        if (allHaveSizes) {
            var cumulative = 0L
            for (i in chunks.indices) {
                val cSize = synchronized(cacheLock) {
                    ramWindowCache["${file.id}:$i"]?.size?.toLong()
                } ?: chunks[i].size
                if (globalPosition < cumulative + cSize || i == chunks.lastIndex) {
                    return i to (globalPosition - cumulative).coerceAtLeast(0L)
                }
                cumulative += cSize
            }
        }

        // Fallback if chunk sizes were not populated in DB: uniform chunk size based on totalChunks
        val estimatedChunkSize = if (file.totalChunks > 1 && file.size > 0L) {
            ((file.size + file.totalChunks - 1) / file.totalChunks).coerceAtLeast(1L)
        } else {
            EncryptedCredentialsManager.CHUNK_SIZE_BYTES
        }
        val idx = (globalPosition / estimatedChunkSize).toInt().coerceIn(0, chunks.lastIndex)
        val offset = (globalPosition - (idx * estimatedChunkSize)).coerceAtLeast(0L)
        return idx to offset
    }

    private fun updateStatsSnapshot(
        activeChunkIndex: Int,
        totalChunks: Int,
        isFetching: Boolean,
        isRefreshingLink: Boolean = false,
        incrementRefreshedLinks: Boolean = false,
        statusLabel: String
    ) {
        val (ramBytes, cachedIndices) = synchronized(cacheLock) {
            val indices = ramWindowCache.keys.mapNotNull { key ->
                key.substringAfterLast(':', "").toIntOrNull()
            }.sorted()
            currentRamBytes to indices
        }
        val prev = _bufferStats.value
        _bufferStats.value = EphemeralBufferStats(
            allocatedRamBytes = ramBytes,
            maxWindowBytes = MAX_RAM_WINDOW_BYTES,
            cachedChunkIndices = cachedIndices,
            activeChunkIndex = activeChunkIndex,
            totalChunks = totalChunks.coerceAtLeast(1),
            isFetchingChunk = isFetching,
            isRefreshingLink = isRefreshingLink,
            refreshedLinksCount = if (incrementRefreshedLinks) prev.refreshedLinksCount + 1 else prev.refreshedLinksCount,
            statusLabel = statusLabel
        )
    }

    /**
     * Immediately cancels all in-flight chunk streams, zero-fills every `ByteArray` in the
     * RAM sliding window, clears cached links, and removes any temporary files.
     * Called on viewer exit (`DisposableEffect`), app backgrounding (`ON_STOP`), and crash recovery.
     */
    fun wipeAllEphemeralBuffers(reason: String = "Viewer exited") {
        try {
            streamJob.cancelChildren()
            inFlightChunks.clear()

            var wipedCount = 0
            var wipedBytes = 0L
            synchronized(cacheLock) {
                for ((_, bytes) in ramWindowCache) {
                    wipedBytes += bytes.size
                    bytes.fill(0)
                    wipedCount++
                }
                ramWindowCache.clear()
                currentRamBytes = 0L
            }

            val orphanDir = File(context.cacheDir, EPHEMERAL_TEMP_DIR_NAME)
            if (orphanDir.exists()) {
                orphanDir.deleteRecursively()
            }

            _bufferStats.value = EphemeralBufferStats(
                allocatedRamBytes = 0L,
                maxWindowBytes = MAX_RAM_WINDOW_BYTES,
                cachedChunkIndices = emptyList(),
                activeChunkIndex = 0,
                totalChunks = 1,
                isFetchingChunk = false,
                isRefreshingLink = false,
                refreshedLinksCount = 0,
                statusLabel = "RAM Buffer Wiped (0.0 MB)"
            )

            Log.i(TAG, "Wiped ephemeral RAM sliding window ($reason): zero-filled $wipedCount chunks (${wipedBytes / 1024} KB)")
        } catch (e: Throwable) {
            Log.w(TAG, "Error during wipeAllEphemeralBuffers: ${e.message}")
        }
    }
}
