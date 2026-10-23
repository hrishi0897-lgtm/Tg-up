package com.example.data.transfer

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.remote.TelegramApiException
import com.example.data.remote.TelegramRepository
import com.example.domain.CryptoStreamEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

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
    val fetchingChunkIndex: Int? = null,
    val isRefreshingLink: Boolean = false,
    val refreshedLinksCount: Int = 0,
    val statusLabel: String = "RAM Stream Ready"
)

/**
 * Cached Telegram `getFile` link entry with TTL tracking.
 * Telegram Bot API download links (`https://api.telegram.org/file/bot<token>/<file_path>`)
 * expire after ~1 hour. We enforce a 45-minute proactive TTL plus automatic reactive
 * re-requesting whenever a download or decryption fails.
 */
private data class ResolvedTelegramLink(
    val token: String,
    val fileId: String,
    val filePath: String,
    val resolvedAtMs: Long
)

/**
 * Holds progressive in-RAM plaintext bytes and optional HTTP Range window bytes for a single chunk
 * so ExoPlayer reads can be satisfied immediately as network slices arrive without waiting for an
 * entire 18 MB chunk to finish downloading.
 */
private class ProgressiveChunkState(
    val key: String,
    val chunkIndex: Int,
    expectedPlainSize: Int
) {
    val stateLock = Any()

    // Progressive buffer filled sequentially from plaintext offset 0
    var progressivePlainBuffer: ByteArray = ByteArray(expectedPlainSize.coerceAtLeast(0))
    @Volatile
    var contiguousPlainBytes: Int = 0
    @Volatile
    var isProgressiveComplete: Boolean = false
    @Volatile
    var progressiveJob: Job? = null
    @Volatile
    var progressiveError: Throwable? = null

    // 12-byte GCM IV for encrypted chunks (cached as soon as bytes 0..11 are read)
    @Volatile
    var gcmIv: ByteArray? = null

    // Random-access HTTP Range read-ahead window (2–4 MB) for mid-chunk or tail index reads (moov/Cues)
    @Volatile
    var rangeWindowStart: Int = -1
    var rangeWindowBuffer: ByteArray? = null
    @Volatile
    var rangeWindowAvailable: Int = 0
    @Volatile
    var rangeJob: Job? = null
    @Volatile
    var rangeError: Throwable? = null

    // Signaled whenever new progressive or range bytes arrive or stream completes/fails
    @Volatile
    var updateSignal = CompletableDeferred<Unit>()

    fun notifyWaitersLocked() {
        val old = updateSignal
        updateSignal = CompletableDeferred()
        old.complete(Unit)
    }

    fun allocatedBytesLocked(): Long {
        val progSize = if (contiguousPlainBytes > 0 || progressiveJob?.isActive == true) {
            progressivePlainBuffer.size.toLong()
        } else 0L
        val rangeSize = rangeWindowBuffer?.size?.toLong() ?: 0L
        return progSize + rangeSize
    }

    fun wipeLocked() {
        progressiveJob?.cancel()
        progressiveJob = null
        rangeJob?.cancel()
        rangeJob = null
        if (progressivePlainBuffer.isNotEmpty()) {
            progressivePlainBuffer.fill(0)
            progressivePlainBuffer = ByteArray(0)
        }
        contiguousPlainBytes = 0
        isProgressiveComplete = false
        gcmIv?.fill(0)
        gcmIv = null
        rangeWindowBuffer?.fill(0)
        rangeWindowBuffer = null
        rangeWindowStart = -1
        rangeWindowAvailable = 0
        notifyWaitersLocked()
    }
}

/**
 * Ephemeral Zero-Download RAM Sliding-Window Stream Engine.
 *
 * - Streams and decrypts Telegram chunks strictly in RAM within a 20–50 MB sliding window
 *   ([MAX_RAM_WINDOW_BYTES] = 36 MB).
 * - Satisfies ExoPlayer reads progressively from partial data and HTTP Range requests (3 MB read-ahead)
 *   using on-the-fly AES-CTR range decryption (counter starting at 2 for 12-byte GCM IVs).
 * - Resolves and starts the FIRST chunk and LAST chunk in parallel on player open, while warming
 *   `getFile` links for the next 2 chunks in the background.
 * - Zero-fills (`ByteArray.fill(0)`) evicted and wiped buffers immediately so sensitive plaintext
 *   never lingers in memory or on disk.
 */
class EphemeralStreamManager private constructor(
    private val context: Context,
    private val database: AppDatabase,
    private val credentialsManager: EncryptedCredentialsManager,
    private val repository: TelegramRepository
) {
    companion object {
        private const val TAG = "EphemeralStreamMgr"
        const val TIMING_TAG = "StreamTiming"

        /**
         * 36 MB RAM sliding window cap (within the 20–50 MB budget).
         */
        const val MAX_RAM_WINDOW_BYTES: Long = 36L * 1024L * 1024L

        /**
         * 3 MB read-ahead window for HTTP Range requests (within the 2–4 MB target).
         */
        private const val RANGE_READ_AHEAD_BYTES: Int = 3 * 1024 * 1024

        /**
         * Tail pre-warm size for the last chunk (MP4 `moov` atom / MKV `Cues` index at end of file).
         */
        private const val TAIL_INDEX_WARM_BYTES: Int = 2 * 1024 * 1024

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

    // Incremented on every wipe so late-completing fetches never repopulate a wiped RAM window
    private val wipeGeneration = AtomicLong(0L)

    // Timestamp when the current streaming session started (for StreamTiming logs)
    private val sessionStartElapsedMs = AtomicLong(0L)
    private val loggedFirstByteOfChunk1 = AtomicBoolean(false)

    private val cacheLock = Any()

    // Access-ordered LRU sliding window of completed decrypted plaintext chunks: key = "$fileId:$chunkIndex"
    private val ramWindowCache = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {}
    private var currentRamBytes: Long = 0L

    // Active progressive / range chunk states
    private val progressiveStates = ConcurrentHashMap<String, ProgressiveChunkState>()

    // Resolved Telegram file_path links with timestamp
    private val resolvedLinks = ConcurrentHashMap<String, ResolvedTelegramLink>()
    private val linkResolutionMutexes = ConcurrentHashMap<String, Mutex>()

    // Deduplication for full-chunk fetches (used by non-media viewers like PDF/Image/Text)
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
     * Returns true if [chunkIndex] for [fileId] is already cached or has progressive data in RAM.
     */
    fun isChunkCachedInRam(fileId: String, chunkIndex: Int): Boolean {
        val key = "$fileId:$chunkIndex"
        synchronized(cacheLock) {
            if (ramWindowCache.containsKey(key)) return true
        }
        val prog = progressiveStates[key] ?: return false
        return prog.contiguousPlainBytes >= (256 * 1024) || prog.isProgressiveComplete || prog.rangeWindowAvailable > 0
    }

    /**
     * Parallel Start when the player opens:
     * 1. Resolves Telegram `getFile` links and starts downloading the FIRST chunk (index 0) and
     *    the LAST chunk (`chunks.lastIndex`) simultaneously in parallel so MP4 `moov` / MKV `Cues`
     *    at the end of the file and initial video frames at the start arrive concurrently.
     * 2. Pre-resolves `getFile` links for the next 2 chunks (`chunk 1` and `chunk 2`) in the background
     *    so subsequent chunks never wait for a `getFile` round trip.
     */
    fun warmStartStreamingSession(file: FileEntity, chunks: List<ChunkEntity>) {
        if (chunks.isEmpty()) return
        val startElapsed = SystemClock.elapsedRealtime()
        sessionStartElapsedMs.set(startElapsed)
        loggedFirstByteOfChunk1.set(false)
        val gen = wipeGeneration.get()

        Log.d(
            TIMING_TAG,
            "warmStartStreamingSession: file='${file.name}', chunks=${chunks.size}, encrypted=${file.isEncrypted}, totalBytes=${file.size}"
        )

        streamScope.launch {
            if (wipeGeneration.get() != gen) return@launch
            val parallelLinkStart = SystemClock.elapsedRealtime()

            // Launch FIRST chunk (0) and LAST chunk (lastIndex) link resolution + download in parallel
            val firstChunkDeferred = async {
                try {
                    resolveTelegramFileLink(file, chunks[0], forceRefresh = false)
                    startProgressiveChunkDownload(
                        file = file,
                        chunks = chunks,
                        chunkIndex = 0,
                        isForegroundDemand = true,
                        expectedGeneration = gen
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "Warm-start first chunk failed: ${e.message}")
                }
            }

            val lastChunkDeferred = if (chunks.size > 1) {
                val lastIdx = chunks.lastIndex
                async {
                    try {
                        resolveTelegramFileLink(file, chunks[lastIdx], forceRefresh = false)
                        val lastPlainSize = getExpectedPlaintextChunkSize(file, chunks, lastIdx)
                        if (lastPlainSize <= RANGE_READ_AHEAD_BYTES) {
                            // Small last chunk: stream progressively from byte 0
                            startProgressiveChunkDownload(
                                file = file,
                                chunks = chunks,
                                chunkIndex = lastIdx,
                                isForegroundDemand = false,
                                expectedGeneration = gen
                            )
                        } else {
                            // Large last chunk: pre-fetch the tail window (last 2 MB) for MP4 moov / MKV Cues
                            val tailStart = (lastPlainSize - TAIL_INDEX_WARM_BYTES).coerceAtLeast(0)
                            startRangeWindowDownload(
                                file = file,
                                chunks = chunks,
                                chunkIndex = lastIdx,
                                requestedPlainOffset = tailStart,
                                expectedGeneration = gen
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.w(TAG, "Warm-start last chunk failed: ${e.message}")
                    }
                }
            } else null

            // Also resolve getFile links for the next 2 chunks (chunk 1 and chunk 2) in the background
            for (nextIdx in 1..2) {
                if (nextIdx < chunks.size && nextIdx != chunks.lastIndex) {
                    launch {
                        try {
                            if (wipeGeneration.get() == gen) {
                                resolveTelegramFileLink(file, chunks[nextIdx], forceRefresh = false)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }

            firstChunkDeferred.await()
            lastChunkDeferred?.await()
            val parallelElapsed = SystemClock.elapsedRealtime() - parallelLinkStart
            Log.d(
                TIMING_TAG,
                "Parallel start (first chunk + last chunk) dispatched in $parallelElapsed ms"
            )
        }
    }

    /**
     * Maps a byte position in [file] to the corresponding chunk index.
     */
    fun getChunkIndexForPosition(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        globalPosition: Long
    ): Int {
        if (chunks.isEmpty()) return 0
        return resolveChunkAndOffset(file, chunks, globalPosition.coerceAtLeast(0L)).first
    }

    /**
     * Reads up to [length] plaintext bytes starting at [globalPosition] across chunk boundaries
     * into [buffer] at [offset].
     *
     * Satisfies ExoPlayer reads immediately from partial progressive data or a 3 MB HTTP Range
     * window without waiting for an entire 18 MB chunk to finish downloading.
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

        val (chunkIndex, offsetInChunkLong) = resolveChunkAndOffset(file, chunks, globalPosition)
        if (chunkIndex < 0 || chunkIndex >= chunks.size) return@withContext -1
        val offsetInChunk = offsetInChunkLong.toInt()

        val expectedPlainSize = getExpectedPlaintextChunkSize(file, chunks, chunkIndex)
        if (expectedPlainSize > 0 && offsetInChunk >= expectedPlainSize) {
            if (chunkIndex + 1 < chunks.size) {
                val nextChunkStartGlobal = globalPosition + (expectedPlainSize - offsetInChunk).coerceAtLeast(0)
                return@withContext readStreamRange(
                    file = file,
                    chunks = chunks,
                    globalPosition = nextChunkStartGlobal,
                    buffer = buffer,
                    offset = offset,
                    length = length
                )
            }
            return@withContext -1
        }

        val key = "${file.id}:$chunkIndex"

        // 1. Fast path: Full chunk is already cached in the 36 MB RAM sliding window
        synchronized(cacheLock) {
            ramWindowCache[key]?.let { cachedBytes ->
                if (offsetInChunk >= cachedBytes.size) return@withContext -1
                val available = cachedBytes.size - offsetInChunk
                val toCopy = minOf(length, available)
                System.arraycopy(cachedBytes, offsetInChunk, buffer, offset, toCopy)
                maybeTriggerLookaheadPrefetch(file, chunks, chunkIndex, offsetInChunk + toCopy, cachedBytes.size)
                return@withContext toCopy
            }
        }

        // 2. Progressive / Partial Range path: satisfy read as soon as partial bytes cover offsetInChunk
        val bytesCopied = readPartialFromChunk(
            file = file,
            chunks = chunks,
            chunkIndex = chunkIndex,
            offsetInChunk = offsetInChunk,
            buffer = buffer,
            bufferOffset = offset,
            maxLength = length
        )

        if (bytesCopied > 0) {
            val effectiveSize = if (expectedPlainSize > 0) expectedPlainSize else (offsetInChunk + bytesCopied)
            maybeTriggerLookaheadPrefetch(file, chunks, chunkIndex, offsetInChunk + bytesCopied, effectiveSize)
        }

        bytesCopied
    }

    private fun maybeTriggerLookaheadPrefetch(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int,
        readEndOffsetInChunk: Int,
        chunkSize: Int
    ) {
        if (chunkSize <= 0) return
        val progress = readEndOffsetInChunk.toFloat() / chunkSize.toFloat()
        // Prefetch next chunk in background once playback advances past 25% of current chunk
        if (progress >= 0.25f && chunkIndex + 1 < chunks.size) {
            prefetchChunkAsync(file, chunks, chunkIndex + 1)
        }
        // Also warm getFile link 2 chunks ahead so it's ready without API latency
        if (chunkIndex + 2 < chunks.size) {
            val lookaheadIdx = chunkIndex + 2
            val linkKey = "${file.id}:${chunks[lookaheadIdx].chunkIndex}"
            if (!resolvedLinks.containsKey(linkKey)) {
                val gen = wipeGeneration.get()
                streamScope.launch {
                    try {
                        if (wipeGeneration.get() == gen) {
                            resolveTelegramFileLink(file, chunks[lookaheadIdx], forceRefresh = false)
                        }
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    /**
     * Satisfies a read for `[offsetInChunk, offsetInChunk + maxLength)` using either:
     * - Progressive download from byte 0 (when reading near the start or already covered by progressive buffer)
     * - A 3 MB HTTP Range read-ahead window (when ExoPlayer seeks to the middle or tail of a chunk)
     */
    private suspend fun readPartialFromChunk(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int,
        offsetInChunk: Int,
        buffer: ByteArray,
        bufferOffset: Int,
        maxLength: Int
    ): Int {
        val key = "${file.id}:$chunkIndex"
        val expectedPlainSize = getExpectedPlaintextChunkSize(file, chunks, chunkIndex)
        val gen = wipeGeneration.get()

        while (true) {
            coroutineContext.ensureActive()
            if (wipeGeneration.get() != gen) {
                throw CancellationException("RAM stream session wiped")
            }

            // Check completed cache first
            synchronized(cacheLock) {
                ramWindowCache[key]?.let { cached ->
                    if (offsetInChunk >= cached.size) return -1
                    val toCopy = minOf(maxLength, cached.size - offsetInChunk)
                    System.arraycopy(cached, offsetInChunk, buffer, bufferOffset, toCopy)
                    return toCopy
                }
            }

            val state = getOrCreateProgressiveState(file, chunks, chunkIndex, expectedPlainSize)
            var waitDeferred: CompletableDeferred<Unit>? = null
            var copied = 0
            var reachedEof = false
            var fatalError: Throwable? = null
            var needStartProgressive = false
            var needStartRange = false

            synchronized(state.stateLock) {
                val progAvail = state.contiguousPlainBytes
                val progBuf = state.progressivePlainBuffer

                if (offsetInChunk < progAvail && offsetInChunk < progBuf.size) {
                    // Covered by progressive buffer from byte 0!
                    val avail = minOf(progAvail, progBuf.size) - offsetInChunk
                    copied = minOf(maxLength, avail)
                    System.arraycopy(progBuf, offsetInChunk, buffer, bufferOffset, copied)
                } else {
                    // Check if covered by the active HTTP Range read-ahead window
                    val rStart = state.rangeWindowStart
                    val rAvail = state.rangeWindowAvailable
                    val rBuf = state.rangeWindowBuffer
                    if (rBuf != null && rStart >= 0 && offsetInChunk >= rStart && offsetInChunk < rStart + rAvail) {
                        val offsetInWindow = offsetInChunk - rStart
                        val avail = minOf(rAvail, rBuf.size) - offsetInWindow
                        if (avail > 0) {
                            copied = minOf(maxLength, avail)
                            System.arraycopy(rBuf, offsetInWindow, buffer, bufferOffset, copied)
                        }
                    }
                }

                if (copied == 0) {
                    if (state.isProgressiveComplete && offsetInChunk >= state.contiguousPlainBytes) {
                        reachedEof = true
                    } else {
                        // Decide whether to wait on / start progressive stream or start a 3 MB HTTP Range window
                        val distanceAheadOfProgressive = offsetInChunk - state.contiguousPlainBytes
                        val progActive = state.progressiveJob?.isActive == true
                        val rangeCoversTarget = state.rangeWindowBuffer != null &&
                            state.rangeWindowStart in 0..offsetInChunk &&
                            offsetInChunk < state.rangeWindowStart + (state.rangeWindowBuffer?.size ?: 0)
                        val rangeActive = state.rangeJob?.isActive == true && rangeCoversTarget

                        if (distanceAheadOfProgressive <= 512 * 1024) {
                            // Sequential or near-front read: use progressive download from byte 0
                            if (!progActive && !state.isProgressiveComplete) {
                                if (state.progressiveError != null) {
                                    fatalError = state.progressiveError
                                } else {
                                    needStartProgressive = true
                                }
                            }
                        } else {
                            // Mid-chunk or tail read (> 512 KB ahead of progressive cursor): use HTTP Range
                            if (!rangeActive) {
                                if (state.rangeError != null && rangeCoversTarget) {
                                    fatalError = state.rangeError
                                } else {
                                    needStartRange = true
                                }
                            }
                        }

                        if (fatalError == null) {
                            waitDeferred = state.updateSignal
                        }
                    }
                }
            }

            if (copied > 0) {
                return copied
            }
            if (reachedEof) {
                return -1
            }
            if (fatalError != null) {
                // Clear error state so a manual user Retry button can try again cleanly
                synchronized(state.stateLock) {
                    state.progressiveError = null
                    state.rangeError = null
                }
                throw if (fatalError is IOException) fatalError as IOException else IOException(fatalError?.message, fatalError)
            }

            if (needStartProgressive) {
                startProgressiveChunkDownload(
                    file = file,
                    chunks = chunks,
                    chunkIndex = chunkIndex,
                    isForegroundDemand = true,
                    expectedGeneration = gen
                )
            }
            if (needStartRange) {
                startRangeWindowDownload(
                    file = file,
                    chunks = chunks,
                    chunkIndex = chunkIndex,
                    requestedPlainOffset = offsetInChunk,
                    expectedGeneration = gen
                )
            }

            waitDeferred?.await()
        }
    }

    private fun getOrCreateProgressiveState(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int,
        expectedPlainSize: Int
    ): ProgressiveChunkState {
        val key = "${file.id}:$chunkIndex"
        return progressiveStates.computeIfAbsent(key) {
            val safeSize = if (expectedPlainSize > 0) expectedPlainSize else (18 * 1024 * 1024)
            evictRamIfNeededForBytes(safeSize.toLong(), excludeKey = key)
            ProgressiveChunkState(
                key = key,
                chunkIndex = chunkIndex,
                expectedPlainSize = safeSize
            ).also {
                recalculateAllocatedRamBytes(
                    activeChunkIndex = chunkIndex,
                    totalChunks = chunks.size,
                    isFetching = true,
                    fetchingChunkIndex = chunkIndex,
                    statusLabel = "Streaming Chunk ${chunkIndex + 1}/${chunks.size}"
                )
            }
        }
    }

    /**
     * Starts progressive streaming and on-the-fly AES-CTR decryption of [chunkIndex] from byte 0.
     * Unblocks waiting ExoPlayer reads after every network slice (`32 KB`) so the first frame
     * renders as soon as the initial header/frame bytes arrive.
     */
    private fun startProgressiveChunkDownload(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int,
        isForegroundDemand: Boolean,
        expectedGeneration: Long
    ) {
        val chunk = chunks.getOrNull(chunkIndex) ?: return
        val expectedPlainSize = getExpectedPlaintextChunkSize(file, chunks, chunkIndex)
        val state = getOrCreateProgressiveState(file, chunks, chunkIndex, expectedPlainSize)

        synchronized(state.stateLock) {
            if (state.isProgressiveComplete || state.progressiveJob?.isActive == true) return
            state.progressiveError = null
            state.progressiveJob = streamScope.launch {
                runProgressiveChunkDownload(
                    file = file,
                    chunks = chunks,
                    chunk = chunk,
                    chunkIndex = chunkIndex,
                    state = state,
                    isForegroundDemand = isForegroundDemand,
                    expectedGeneration = expectedGeneration
                )
            }
        }
    }

    private suspend fun runProgressiveChunkDownload(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunk: ChunkEntity,
        chunkIndex: Int,
        state: ProgressiveChunkState,
        isForegroundDemand: Boolean,
        expectedGeneration: Long
    ) {
        val key = "${file.id}:$chunkIndex"
        val isEncrypted = file.isEncrypted && !file.encryptionKeyBase64.isNullOrBlank()
        val keyBase64 = file.encryptionKeyBase64.orEmpty()
        var lastException: Throwable? = null

        // Single retry after refreshing the Telegram link if Attempt 1 fails
        for (attempt in 1..2) {
            coroutineContext.ensureActive()
            if (wipeGeneration.get() != expectedGeneration) return

            val forceRefresh = (attempt == 2)
            try {
                val resolvedLink = resolveTelegramFileLink(
                    file = file,
                    chunk = chunk,
                    forceRefresh = forceRefresh
                )

                val httpStartMs = SystemClock.elapsedRealtime()
                val resumePlainOffset = synchronized(state.stateLock) { state.contiguousPlainBytes }
                val rawStartOffset = if (resumePlainOffset > 0 && !isEncrypted) {
                    resumePlainOffset.toLong()
                } else {
                    0L
                }

                val streamResult = repository.downloadFileStreamWithRange(
                    token = resolvedLink.token,
                    filePath = resolvedLink.filePath,
                    startOffset = rawStartOffset,
                    endOffsetInclusive = null,
                    maxAttempts = 1
                )

                if (streamResult.isFailure) {
                    val ex = streamResult.exceptionOrNull()
                    throw IOException(
                        ex?.message ?: "HTTP download failed for chunk ${chunkIndex + 1}/${chunks.size}",
                        ex
                    )
                }

                val downloadResult = streamResult.getOrThrow()
                val honoredRange = downloadResult.isPartialContent && rawStartOffset > 0L
                var plainWriteOffset = if (honoredRange) resumePlainOffset else 0

                // For encrypted chunks, retain raw bytes temporarily in RAM only until chunk finishes
                // so we can run non-blocking GCM authentication tag verification at the end.
                val rawEncryptedAccumulator = if (isEncrypted) {
                    ByteArrayOutputStream(
                        (state.progressivePlainBuffer.size + CryptoStreamEngine.GCM_OVERHEAD_BYTES).coerceAtLeast(1024)
                    )
                } else null

                val ivBuffer = ByteArray(CryptoStreamEngine.GCM_IV_BYTES)
                var ivBytesCollected = 0
                // Keep a 16-byte tail holdback when reading encrypted streams so we never feed the
                // trailing 16-byte GCM authentication tag into the payload CTR stream.
                val tagHoldback = ByteArray(CryptoStreamEngine.GCM_TAG_BYTES)
                var tagHoldbackLen = 0
                var loggedFirstSliceDecrypt = false

                downloadResult.body.use { responseBody ->
                    responseBody.byteStream().use { input ->
                        val netBuf = ByteArray(32 * 1024)
                        var readCount: Int
                        while (input.read(netBuf).also { readCount = it } != -1) {
                            coroutineContext.ensureActive()
                            if (wipeGeneration.get() != expectedGeneration) {
                                netBuf.fill(0)
                                return
                            }
                            if (readCount <= 0) continue

                            if (chunkIndex == 0 && loggedFirstByteOfChunk1.compareAndSet(false, true)) {
                                val now = SystemClock.elapsedRealtime()
                                val sessionStart = sessionStartElapsedMs.get()
                                val fromSessionStart = if (sessionStart > 0L) now - sessionStart else (now - httpStartMs)
                                Log.d(
                                    TIMING_TAG,
                                    "Time to first byte of chunk 1: $fromSessionStart ms (HTTP TTFB=${now - httpStartMs} ms, code=${downloadResult.responseCode})"
                                )
                            }

                            if (!isEncrypted) {
                                synchronized(state.stateLock) {
                                    ensureProgressiveCapacityLocked(state, plainWriteOffset + readCount)
                                    System.arraycopy(
                                        netBuf,
                                        0,
                                        state.progressivePlainBuffer,
                                        plainWriteOffset,
                                        readCount
                                    )
                                    plainWriteOffset += readCount
                                    if (plainWriteOffset > state.contiguousPlainBytes) {
                                        state.contiguousPlainBytes = plainWriteOffset
                                    }
                                    state.notifyWaitersLocked()
                                }
                            } else {
                                rawEncryptedAccumulator?.write(netBuf, 0, readCount)
                                var offsetInNet = 0
                                var remainingInNet = readCount

                                // Step 1: Collect 12-byte GCM IV from the beginning of the encrypted chunk
                                if (ivBytesCollected < CryptoStreamEngine.GCM_IV_BYTES) {
                                    val neededIv = CryptoStreamEngine.GCM_IV_BYTES - ivBytesCollected
                                    val toTakeIv = minOf(neededIv, remainingInNet)
                                    System.arraycopy(netBuf, offsetInNet, ivBuffer, ivBytesCollected, toTakeIv)
                                    ivBytesCollected += toTakeIv
                                    offsetInNet += toTakeIv
                                    remainingInNet -= toTakeIv

                                    if (ivBytesCollected == CryptoStreamEngine.GCM_IV_BYTES) {
                                        synchronized(state.stateLock) {
                                            if (state.gcmIv == null) {
                                                state.gcmIv = ivBuffer.copyOf()
                                            }
                                        }
                                    }
                                }

                                // Step 2: Process ciphertext while holding back the trailing 16 bytes (GCM auth tag)
                                if (remainingInNet > 0 && ivBytesCollected == CryptoStreamEngine.GCM_IV_BYTES) {
                                    val combinedLen = tagHoldbackLen + remainingInNet
                                    if (combinedLen <= CryptoStreamEngine.GCM_TAG_BYTES) {
                                        System.arraycopy(netBuf, offsetInNet, tagHoldback, tagHoldbackLen, remainingInNet)
                                        tagHoldbackLen += remainingInNet
                                    } else {
                                        val cipherReadyLen = combinedLen - CryptoStreamEngine.GCM_TAG_BYTES
                                        val cipherSlice = ByteArray(cipherReadyLen)

                                        if (tagHoldbackLen > 0) {
                                            val fromHoldback = minOf(tagHoldbackLen, cipherReadyLen)
                                            System.arraycopy(tagHoldback, 0, cipherSlice, 0, fromHoldback)
                                            val fromNet = cipherReadyLen - fromHoldback
                                            if (fromNet > 0) {
                                                System.arraycopy(netBuf, offsetInNet, cipherSlice, fromHoldback, fromNet)
                                            }
                                            // Update tagHoldback with the last 16 bytes
                                            val remainingHoldback = tagHoldbackLen - fromHoldback
                                            if (remainingHoldback > 0) {
                                                System.arraycopy(tagHoldback, fromHoldback, tagHoldback, 0, remainingHoldback)
                                            }
                                            System.arraycopy(
                                                netBuf,
                                                offsetInNet + fromNet,
                                                tagHoldback,
                                                remainingHoldback,
                                                CryptoStreamEngine.GCM_TAG_BYTES - remainingHoldback
                                            )
                                            tagHoldbackLen = CryptoStreamEngine.GCM_TAG_BYTES
                                        } else {
                                            System.arraycopy(netBuf, offsetInNet, cipherSlice, 0, cipherReadyLen)
                                            System.arraycopy(
                                                netBuf,
                                                offsetInNet + cipherReadyLen,
                                                tagHoldback,
                                                0,
                                                CryptoStreamEngine.GCM_TAG_BYTES
                                            )
                                            tagHoldbackLen = CryptoStreamEngine.GCM_TAG_BYTES
                                        }

                                        val decryptStartNs = System.nanoTime()
                                        val decryptedSlice = CryptoStreamEngine.decryptRangeCtr(
                                            iv = ivBuffer,
                                            keyBase64 = keyBase64,
                                            plainOffset = plainWriteOffset.toLong(),
                                            ciphertextSlice = cipherSlice
                                        )
                                        val decryptElapsedNs = System.nanoTime() - decryptStartNs
                                        cipherSlice.fill(0)

                                        if (!loggedFirstSliceDecrypt) {
                                            loggedFirstSliceDecrypt = true
                                            Log.d(
                                                TIMING_TAG,
                                                "Time to decrypt first slice of chunk ${chunkIndex + 1} (${decryptedSlice.size} bytes via AES-CTR): ${"%.2f".format(decryptElapsedNs / 1_000_000.0)} ms"
                                            )
                                        }

                                        synchronized(state.stateLock) {
                                            ensureProgressiveCapacityLocked(state, plainWriteOffset + decryptedSlice.size)
                                            System.arraycopy(
                                                decryptedSlice,
                                                0,
                                                state.progressivePlainBuffer,
                                                plainWriteOffset,
                                                decryptedSlice.size
                                            )
                                            plainWriteOffset += decryptedSlice.size
                                            if (plainWriteOffset > state.contiguousPlainBytes) {
                                                state.contiguousPlainBytes = plainWriteOffset
                                            }
                                            state.notifyWaitersLocked()
                                        }
                                        decryptedSlice.fill(0)
                                    }
                                }
                            }
                        }
                        netBuf.fill(0)
                    }
                }

                if (plainWriteOffset <= 0) {
                    throw IOException("Received empty payload for chunk ${chunkIndex + 1}/${chunks.size}")
                }

                // Finalize completed plaintext array in the 36 MB RAM sliding window
                val finalPlainBytes = synchronized(state.stateLock) {
                    val trimmed = if (state.progressivePlainBuffer.size == plainWriteOffset) {
                        state.progressivePlainBuffer
                    } else {
                        state.progressivePlainBuffer.copyOf(plainWriteOffset).also {
                            state.progressivePlainBuffer.fill(0)
                            state.progressivePlainBuffer = it
                        }
                    }
                    state.contiguousPlainBytes = plainWriteOffset
                    state.isProgressiveComplete = true
                    // Free any temporary range window buffer now that the full chunk is in RAM
                    state.rangeWindowBuffer?.fill(0)
                    state.rangeWindowBuffer = null
                    state.rangeWindowAvailable = 0
                    state.notifyWaitersLocked()
                    trimmed
                }

                if (wipeGeneration.get() != expectedGeneration) {
                    finalPlainBytes.fill(0)
                    return
                }

                storeInRamSlidingWindow(key, finalPlainBytes)

                // Non-blocking GCM authentication tag verification in background once full chunk completes
                if (isEncrypted && rawEncryptedAccumulator != null) {
                    val rawEncrypted = rawEncryptedAccumulator.toByteArray()
                    streamScope.launch {
                        try {
                            val verifyStartNs = System.nanoTime()
                            val tagValid = CryptoStreamEngine.verifyGcmTag(rawEncrypted, keyBase64)
                            val verifyMs = (System.nanoTime() - verifyStartNs) / 1_000_000.0
                            Log.d(
                                TIMING_TAG,
                                "Background GCM tag verification for chunk ${chunkIndex + 1}: valid=$tagValid in ${"%.2f".format(verifyMs)} ms"
                            )
                            if (!tagValid) {
                                Log.w(TAG, "GCM tag verification mismatch on chunk ${chunkIndex + 1}")
                            }
                        } finally {
                            rawEncrypted.fill(0)
                        }
                    }
                }

                recalculateAllocatedRamBytes(
                    activeChunkIndex = chunkIndex,
                    totalChunks = chunks.size,
                    isFetching = false,
                    fetchingChunkIndex = null,
                    statusLabel = "RAM Stream • Chunk ${chunkIndex + 1}/${chunks.size}"
                )
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastException = e
                resolvedLinks.remove("${file.id}:${chunk.chunkIndex}")
                Log.w(
                    TAG,
                    "Progressive chunk ${chunkIndex + 1}/${chunks.size} attempt $attempt/2 failed: ${e.message}"
                )
            }
        }

        val finalError = IOException(
            "Unable to download or decrypt chunk ${chunkIndex + 1}/${chunks.size} after refreshing Telegram link: ${lastException?.message ?: "Unknown stream error"}",
            lastException
        )
        synchronized(state.stateLock) {
            state.progressiveError = finalError
            state.notifyWaitersLocked()
        }
    }

    /**
     * Starts an HTTP Range read-ahead download (`3 MB` window) starting at [requestedPlainOffset].
     *
     * - For encrypted files, fetches the 12-byte GCM IV first (if not already cached on [state]),
     *   aligns the request to a 16-byte AES block boundary, and decrypts incoming slices on the fly
     *   using `CryptoStreamEngine.decryptRangeCtr` (counter = `2 + blockIndex`).
     * - If the server does not honor HTTP `Range` (returns `200 OK` instead of `206 Partial Content`),
     *   automatically falls back to feeding the progressive chunk stream from byte 0.
     */
    private fun startRangeWindowDownload(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int,
        requestedPlainOffset: Int,
        expectedGeneration: Long
    ) {
        val chunk = chunks.getOrNull(chunkIndex) ?: return
        val expectedPlainSize = getExpectedPlaintextChunkSize(file, chunks, chunkIndex)
        val state = getOrCreateProgressiveState(file, chunks, chunkIndex, expectedPlainSize)

        synchronized(state.stateLock) {
            val currentStart = state.rangeWindowStart
            val currentCap = state.rangeWindowBuffer?.size ?: 0
            if (state.rangeJob?.isActive == true &&
                currentStart in 0..requestedPlainOffset &&
                requestedPlainOffset < currentStart + currentCap
            ) {
                return
            }
            state.rangeJob?.cancel()
            state.rangeError = null
            state.rangeJob = streamScope.launch {
                runRangeWindowDownload(
                    file = file,
                    chunks = chunks,
                    chunk = chunk,
                    chunkIndex = chunkIndex,
                    state = state,
                    requestedPlainOffset = requestedPlainOffset,
                    expectedPlainSize = expectedPlainSize,
                    expectedGeneration = expectedGeneration
                )
            }
        }
    }

    private suspend fun runRangeWindowDownload(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunk: ChunkEntity,
        chunkIndex: Int,
        state: ProgressiveChunkState,
        requestedPlainOffset: Int,
        expectedPlainSize: Int,
        expectedGeneration: Long
    ) {
        val isEncrypted = file.isEncrypted && !file.encryptionKeyBase64.isNullOrBlank()
        val keyBase64 = file.encryptionKeyBase64.orEmpty()

        // Align down to 16-byte AES block boundary so CTR decryption starts cleanly on a block boundary
        val alignedPlainStart = if (isEncrypted) {
            (requestedPlainOffset / 16) * 16
        } else {
            requestedPlainOffset.coerceAtLeast(0)
        }
        val maxPlainEnd = if (expectedPlainSize > 0) {
            minOf(expectedPlainSize, alignedPlainStart + RANGE_READ_AHEAD_BYTES)
        } else {
            alignedPlainStart + RANGE_READ_AHEAD_BYTES
        }
        val windowLength = (maxPlainEnd - alignedPlainStart).coerceAtLeast(64 * 1024)

        synchronized(state.stateLock) {
            state.rangeWindowBuffer?.fill(0)
            state.rangeWindowBuffer = ByteArray(windowLength)
            state.rangeWindowStart = alignedPlainStart
            state.rangeWindowAvailable = 0
        }

        var lastException: Throwable? = null

        for (attempt in 1..2) {
            coroutineContext.ensureActive()
            if (wipeGeneration.get() != expectedGeneration) return

            val forceRefresh = (attempt == 2)
            try {
                val resolvedLink = resolveTelegramFileLink(
                    file = file,
                    chunk = chunk,
                    forceRefresh = forceRefresh
                )

                // Step 1: If encrypted, ensure we have the 12-byte GCM IV from bytes 0..11 of the chunk
                var iv = synchronized(state.stateLock) { state.gcmIv }
                if (isEncrypted && iv == null) {
                    val ivStartMs = SystemClock.elapsedRealtime()
                    val ivResult = repository.downloadFileStreamWithRange(
                        token = resolvedLink.token,
                        filePath = resolvedLink.filePath,
                        startOffset = 0L,
                        endOffsetInclusive = (CryptoStreamEngine.GCM_IV_BYTES - 1).toLong(),
                        maxAttempts = 1
                    )
                    if (ivResult.isSuccess) {
                        val ivStream = ivResult.getOrThrow()
                        if (!ivStream.isPartialContent && ivStream.responseCode == 200) {
                            // Server ignored Range and sent full chunk from byte 0!
                            // Close this stream and delegate to progressive from-0 download.
                            ivStream.body.close()
                            startProgressiveChunkDownload(
                                file = file,
                                chunks = chunks,
                                chunkIndex = chunkIndex,
                                isForegroundDemand = true,
                                expectedGeneration = expectedGeneration
                            )
                            return
                        }
                        val fetchedIv = ByteArray(CryptoStreamEngine.GCM_IV_BYTES)
                        var totalIv = 0
                        ivStream.body.use { body ->
                            body.byteStream().use { ins ->
                                while (totalIv < CryptoStreamEngine.GCM_IV_BYTES) {
                                    val r = ins.read(fetchedIv, totalIv, CryptoStreamEngine.GCM_IV_BYTES - totalIv)
                                    if (r <= 0) break
                                    totalIv += r
                                }
                            }
                        }
                        if (totalIv == CryptoStreamEngine.GCM_IV_BYTES) {
                            iv = fetchedIv
                            synchronized(state.stateLock) {
                                state.gcmIv = fetchedIv.copyOf()
                            }
                            Log.d(
                                TIMING_TAG,
                                "Fetched 12-byte GCM IV for chunk ${chunkIndex + 1} via Range in ${SystemClock.elapsedRealtime() - ivStartMs} ms"
                            )
                        } else {
                            throw IOException("Failed to read 12-byte GCM IV for chunk ${chunkIndex + 1}")
                        }
                    } else {
                        throw IOException("Failed to fetch GCM IV for chunk ${chunkIndex + 1}", ivResult.exceptionOrNull())
                    }
                }

                // Step 2: Request the target byte range [rawStart .. rawEndInclusive]
                val rawStart = if (isEncrypted) {
                    CryptoStreamEngine.GCM_IV_BYTES.toLong() + alignedPlainStart.toLong()
                } else {
                    alignedPlainStart.toLong()
                }
                val rawEndInclusive = rawStart + windowLength.toLong() - 1L

                val rangeStartMs = SystemClock.elapsedRealtime()
                val rangeResult = repository.downloadFileStreamWithRange(
                    token = resolvedLink.token,
                    filePath = resolvedLink.filePath,
                    startOffset = rawStart,
                    endOffsetInclusive = rawEndInclusive,
                    maxAttempts = 1
                )

                if (rangeResult.isFailure) {
                    val ex = rangeResult.exceptionOrNull()
                    throw IOException(
                        ex?.message ?: "HTTP Range download failed for chunk ${chunkIndex + 1}",
                        ex
                    )
                }

                val streamRes = rangeResult.getOrThrow()
                if (!streamRes.isPartialContent && rawStart > 0L) {
                    // Server did not honor HTTP Range (returned 200 OK from byte 0):
                    // Fall back to full progressive download from byte 0!
                    Log.d(
                        TIMING_TAG,
                        "Server returned HTTP ${streamRes.responseCode} (no Range) for chunk ${chunkIndex + 1}; falling back to progressive stream"
                    )
                    streamRes.body.close()
                    startProgressiveChunkDownload(
                        file = file,
                        chunks = chunks,
                        chunkIndex = chunkIndex,
                        isForegroundDemand = true,
                        expectedGeneration = expectedGeneration
                    )
                    return
                }

                var writtenInWindow = 0
                var loggedRangeTiming = false

                streamRes.body.use { responseBody ->
                    responseBody.byteStream().use { input ->
                        val netBuf = ByteArray(32 * 1024)
                        var readCount: Int
                        while (input.read(netBuf).also { readCount = it } != -1) {
                            coroutineContext.ensureActive()
                            if (wipeGeneration.get() != expectedGeneration) {
                                netBuf.fill(0)
                                return
                            }
                            if (readCount <= 0) continue

                            val remainingCap = windowLength - writtenInWindow
                            if (remainingCap <= 0) break
                            val sliceLen = minOf(readCount, remainingCap)

                            if (!isEncrypted) {
                                synchronized(state.stateLock) {
                                    val targetBuf = state.rangeWindowBuffer
                                    if (targetBuf != null && writtenInWindow + sliceLen <= targetBuf.size) {
                                        System.arraycopy(netBuf, 0, targetBuf, writtenInWindow, sliceLen)
                                        writtenInWindow += sliceLen
                                        state.rangeWindowAvailable = writtenInWindow
                                        state.notifyWaitersLocked()
                                    }
                                }
                            } else {
                                val activeIv = iv ?: throw IOException("Missing GCM IV for encrypted range read")
                                val decryptStartNs = System.nanoTime()
                                val plainSlice = CryptoStreamEngine.decryptRangeCtr(
                                    iv = activeIv,
                                    keyBase64 = keyBase64,
                                    plainOffset = (alignedPlainStart + writtenInWindow).toLong(),
                                    ciphertextSlice = netBuf,
                                    cipherSliceOffset = 0,
                                    length = sliceLen
                                )
                                val decryptMs = (System.nanoTime() - decryptStartNs) / 1_000_000.0

                                if (!loggedRangeTiming) {
                                    loggedRangeTiming = true
                                    Log.d(
                                        TIMING_TAG,
                                        "Range chunk ${chunkIndex + 1} offset=$alignedPlainStart first slice (${plainSlice.size} B): HTTP=${SystemClock.elapsedRealtime() - rangeStartMs} ms, AES-CTR decrypt=${"%.2f".format(decryptMs)} ms"
                                    )
                                }

                                synchronized(state.stateLock) {
                                    val targetBuf = state.rangeWindowBuffer
                                    if (targetBuf != null && writtenInWindow + plainSlice.size <= targetBuf.size) {
                                        System.arraycopy(plainSlice, 0, targetBuf, writtenInWindow, plainSlice.size)
                                        writtenInWindow += plainSlice.size
                                        state.rangeWindowAvailable = writtenInWindow
                                        state.notifyWaitersLocked()
                                    }
                                }
                                plainSlice.fill(0)
                            }
                        }
                        netBuf.fill(0)
                    }
                }

                // If this range started at 0 and covered the entire chunk, promote to full chunk cache
                if (alignedPlainStart == 0 && expectedPlainSize > 0 && writtenInWindow >= expectedPlainSize) {
                    val promoted = synchronized(state.stateLock) {
                        state.rangeWindowBuffer?.copyOf(expectedPlainSize)
                    }
                    if (promoted != null && wipeGeneration.get() == expectedGeneration) {
                        storeInRamSlidingWindow("${file.id}:$chunkIndex", promoted)
                    }
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastException = e
                resolvedLinks.remove("${file.id}:${chunk.chunkIndex}")
                Log.w(
                    TAG,
                    "Range download for chunk ${chunkIndex + 1} attempt $attempt/2 failed: ${e.message}"
                )
            }
        }

        val finalError = IOException(
            "Unable to stream byte range for chunk ${chunkIndex + 1}/${chunks.size} after refreshing link: ${lastException?.message}",
            lastException
        )
        synchronized(state.stateLock) {
            state.rangeError = finalError
            state.notifyWaitersLocked()
        }
    }

    private fun ensureProgressiveCapacityLocked(state: ProgressiveChunkState, requiredSize: Int) {
        if (requiredSize <= state.progressivePlainBuffer.size) return
        val newCap = maxOf(requiredSize, (state.progressivePlainBuffer.size * 3) / 2, 256 * 1024)
        val grown = state.progressivePlainBuffer.copyOf(newCap)
        state.progressivePlainBuffer.fill(0)
        state.progressivePlainBuffer = grown
    }

    /**
     * Fetches (or returns from the 36 MB RAM sliding window) the full decrypted plaintext bytes
     * for [chunkIndex]. Used by non-streaming document/image/PDF previews.
     */
    suspend fun getChunkPlaintextBytes(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int,
        isForegroundDemand: Boolean = true
    ): ByteArray = withContext(Dispatchers.IO) {
        val key = "${file.id}:$chunkIndex"

        synchronized(cacheLock) {
            ramWindowCache[key]?.let { cached ->
                return@withContext cached
            }
        }

        val startGen = wipeGeneration.get()
        val deferred = inFlightMutex.withLock {
            synchronized(cacheLock) {
                ramWindowCache[key]?.let { cached ->
                    return@withContext cached
                }
            }
            inFlightChunks[key]?.takeIf { it.isActive } ?: streamScope.async {
                try {
                    fetchAndDecryptChunkToRam(
                        file = file,
                        chunks = chunks,
                        chunkIndex = chunkIndex,
                        isForegroundDemand = isForegroundDemand,
                        expectedGeneration = startGen
                    )
                } finally {
                    inFlightChunks.remove(key)
                }
            }.also { inFlightChunks[key] = it }
        }

        deferred.await()
    }

    /**
     * Prefetches [chunkIndex] in the background into the 36 MB RAM sliding window.
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
        val existingState = progressiveStates[key]
        if (existingState != null && (existingState.isProgressiveComplete || existingState.progressiveJob?.isActive == true)) {
            return
        }

        val gen = wipeGeneration.get()
        startProgressiveChunkDownload(
            file = file,
            chunks = chunks,
            chunkIndex = chunkIndex,
            isForegroundDemand = false,
            expectedGeneration = gen
        )
    }

    private suspend fun fetchAndDecryptChunkToRam(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int,
        isForegroundDemand: Boolean,
        expectedGeneration: Long
    ): ByteArray {
        startProgressiveChunkDownload(
            file = file,
            chunks = chunks,
            chunkIndex = chunkIndex,
            isForegroundDemand = isForegroundDemand,
            expectedGeneration = expectedGeneration
        )
        val expectedPlainSize = getExpectedPlaintextChunkSize(file, chunks, chunkIndex)
        val state = getOrCreateProgressiveState(file, chunks, chunkIndex, expectedPlainSize)
        val key = "${file.id}:$chunkIndex"

        while (true) {
            coroutineContext.ensureActive()
            if (wipeGeneration.get() != expectedGeneration) {
                throw CancellationException("RAM stream session wiped")
            }
            synchronized(cacheLock) {
                ramWindowCache[key]?.let { return it }
            }
            val waitSignal = synchronized(state.stateLock) {
                state.progressiveError?.let { err ->
                    throw if (err is IOException) err else IOException(err.message, err)
                }
                if (state.isProgressiveComplete && state.progressivePlainBuffer.isNotEmpty()) {
                    return state.progressivePlainBuffer
                }
                state.updateSignal
            }
            waitSignal.await()
        }
    }

    /**
     * Resolves a fresh Telegram `file_path` for [chunk], deduplicating concurrent requests for
     * the same chunk and logging resolution time under `StreamTiming`.
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

        val mutex = linkResolutionMutexes.computeIfAbsent(linkKey) { Mutex() }
        return mutex.withLock {
            val nowLocked = System.currentTimeMillis()
            if (!forceRefresh) {
                val cached = resolvedLinks[linkKey]
                if (cached != null && (nowLocked - cached.resolvedAtMs) < LINK_TTL_MS) {
                    return@withLock cached
                }
            } else {
                resolvedLinks.remove(linkKey)
            }

            val resolveStartMs = SystemClock.elapsedRealtime()
            val primaryToken = credentialsManager.getBotToken()
                ?: throw IOException("No Telegram bot token configured")
            val loadBalancer = MultiBotLoadBalancer.getInstance(context)
            var currentToken = loadBalancer.acquireBotForChunk(primaryToken)

            try {
                val chunkChannelId = chunk.channelId.ifBlank {
                    file.channelId ?: credentialsManager.getChatId() ?: ""
                }
                val chunkMessageId = chunk.telegramMessageId
                var activeFileId = chunk.telegramFileId

                if ((activeFileId.isNullOrBlank() || forceRefresh) && chunkChannelId.isNotBlank() && chunkMessageId != null) {
                    val regen = repository.regenerateFileId(currentToken, chunkChannelId, chunkMessageId)
                    if (regen.isSuccess) {
                        activeFileId = regen.getOrThrow()
                        database.chunkDao().updateRemoteFileId(file.id, chunk.chunkIndex, activeFileId)
                    }
                }

                val targetFileId = activeFileId
                    ?: throw IOException("Missing Telegram file_id for chunk ${chunk.chunkIndex + 1}")

                var fileInfoResult = repository.getFileInfo(currentToken, targetFileId)

                if (fileInfoResult.isFailure) {
                    val rawEx = fileInfoResult.exceptionOrNull()
                    if (isLinkExpiredOrAuthError(rawEx) || forceRefresh) {
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
                    throw IOException("Telegram getFile failed: ${ex.message}", ex)
                }
                val filePath = remoteFile.filePath
                    ?: throw IOException("Telegram getFile returned empty file_path")

                loadBalancer.releaseBotForChunk(currentToken, chunk.size, true)

                val resolved = ResolvedTelegramLink(
                    token = currentToken,
                    fileId = activeFileId ?: targetFileId,
                    filePath = filePath,
                    resolvedAtMs = System.currentTimeMillis()
                )
                resolvedLinks[linkKey] = resolved

                val resolveElapsedMs = SystemClock.elapsedRealtime() - resolveStartMs
                Log.d(
                    TIMING_TAG,
                    "Time to resolve getFile link for chunk ${chunk.chunkIndex + 1}: $resolveElapsedMs ms (forceRefresh=$forceRefresh)"
                )

                resolved
            } catch (e: Throwable) {
                loadBalancer.releaseBotForChunk(currentToken, 0L, false)
                throw e
            }
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
     * Evicts oldest entries in the 36 MB RAM sliding window to make room for [incomingBytes].
     */
    private fun evictRamIfNeededForBytes(incomingBytes: Long, excludeKey: String? = null) {
        synchronized(cacheLock) {
            val iterator = ramWindowCache.entries.iterator()
            while (iterator.hasNext() && (currentRamBytes + incomingBytes > MAX_RAM_WINDOW_BYTES) && ramWindowCache.isNotEmpty()) {
                val eldest = iterator.next()
                if (eldest.key == excludeKey) continue
                val evictedBytes = eldest.value
                currentRamBytes = (currentRamBytes - evictedBytes.size).coerceAtLeast(0L)
                evictedBytes.fill(0)
                iterator.remove()
                progressiveStates.remove(eldest.key)?.let { st ->
                    synchronized(st.stateLock) { st.wipeLocked() }
                }
            }
        }
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

            val iterator = ramWindowCache.entries.iterator()
            while (iterator.hasNext() && (currentRamBytes + plainBytes.size > MAX_RAM_WINDOW_BYTES) && ramWindowCache.isNotEmpty()) {
                val eldest = iterator.next()
                val evictedBytes = eldest.value
                currentRamBytes = (currentRamBytes - evictedBytes.size).coerceAtLeast(0L)
                evictedBytes.fill(0)
                iterator.remove()
                progressiveStates.remove(eldest.key)?.let { st ->
                    synchronized(st.stateLock) { st.wipeLocked() }
                }
            }

            ramWindowCache[key] = plainBytes
            currentRamBytes += plainBytes.size
        }
    }

    /**
     * Computes expected plaintext size of [chunkIndex].
     */
    private fun getExpectedPlaintextChunkSize(
        file: FileEntity,
        chunks: List<ChunkEntity>,
        chunkIndex: Int
    ): Int {
        val chunk = chunks.getOrNull(chunkIndex) ?: return 0
        val key = "${file.id}:$chunkIndex"
        synchronized(cacheLock) {
            ramWindowCache[key]?.let { return it.size }
        }
        if (chunk.size <= 0L) return 0
        val rawSum = chunks.sumOf { it.size }
        val isEncryptedOverheadIncluded = file.isEncrypted &&
            file.size > 0L &&
            rawSum == file.size + (chunks.size.toLong() * CryptoStreamEngine.GCM_OVERHEAD_BYTES)
        val plainLong = if (isEncryptedOverheadIncluded) {
            (chunk.size - CryptoStreamEngine.GCM_OVERHEAD_BYTES).coerceAtLeast(0L)
        } else {
            chunk.size
        }
        return plainLong.toInt()
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

        val allHaveSizes = chunks.all { it.size > 0L }
        if (allHaveSizes) {
            var cumulative = 0L
            for (i in chunks.indices) {
                val cSize = getExpectedPlaintextChunkSize(file, chunks, i).toLong().takeIf { it > 0L }
                    ?: chunks[i].size
                if (globalPosition < cumulative + cSize || i == chunks.lastIndex) {
                    return i to (globalPosition - cumulative).coerceAtLeast(0L)
                }
                cumulative += cSize
            }
        }

        val estimatedChunkSize = if (file.totalChunks > 1 && file.size > 0L) {
            ((file.size + file.totalChunks - 1) / file.totalChunks).coerceAtLeast(1L)
        } else {
            EncryptedCredentialsManager.CHUNK_SIZE_BYTES
        }
        val idx = (globalPosition / estimatedChunkSize).toInt().coerceIn(0, chunks.lastIndex)
        val offset = (globalPosition - (idx * estimatedChunkSize)).coerceAtLeast(0L)
        return idx to offset
    }

    private fun recalculateAllocatedRamBytes(
        activeChunkIndex: Int,
        totalChunks: Int,
        isFetching: Boolean,
        fetchingChunkIndex: Int?,
        isRefreshingLink: Boolean = false,
        incrementRefreshedLinks: Boolean = false,
        statusLabel: String
    ) {
        val (ramBytes, cachedIndices) = synchronized(cacheLock) {
            var totalBytes = currentRamBytes
            for ((k, st) in progressiveStates) {
                if (!ramWindowCache.containsKey(k)) {
                    totalBytes += synchronized(st.stateLock) { st.allocatedBytesLocked() }
                }
            }
            val indices = (ramWindowCache.keys + progressiveStates.keys)
                .mapNotNull { key -> key.substringAfterLast(':', "").toIntOrNull() }
                .distinct()
                .sorted()
            totalBytes.coerceAtMost(MAX_RAM_WINDOW_BYTES) to indices
        }
        val prev = _bufferStats.value
        _bufferStats.value = EphemeralBufferStats(
            allocatedRamBytes = ramBytes,
            maxWindowBytes = MAX_RAM_WINDOW_BYTES,
            cachedChunkIndices = cachedIndices,
            activeChunkIndex = activeChunkIndex,
            totalChunks = totalChunks.coerceAtLeast(1),
            isFetchingChunk = isFetching,
            fetchingChunkIndex = fetchingChunkIndex,
            isRefreshingLink = isRefreshingLink,
            refreshedLinksCount = if (incrementRefreshedLinks) prev.refreshedLinksCount + 1 else prev.refreshedLinksCount,
            statusLabel = statusLabel
        )
    }

    /**
     * Immediately increments [wipeGeneration], cancels all in-flight chunk streams,
     * zero-fills every `ByteArray` in the RAM sliding window and progressive buffers,
     * and resets [bufferStats] to 0.0 MB (`allocatedRamBytes = 0L`).
     */
    fun wipeAllEphemeralBuffers(reason: String = "Viewer exited") {
        try {
            wipeGeneration.incrementAndGet()
            streamJob.cancelChildren()
            inFlightChunks.forEach { (_, deferred) -> deferred.cancel() }
            inFlightChunks.clear()

            progressiveStates.forEach { (_, st) ->
                synchronized(st.stateLock) {
                    st.wipeLocked()
                }
            }
            progressiveStates.clear()

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
                fetchingChunkIndex = null,
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
