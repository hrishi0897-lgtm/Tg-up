package com.example.data.transfer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Custom Media3 [DataSource] that streams video and audio files directly from Telegram chunks
 * via [EphemeralStreamManager]'s 20–50 MB RAM sliding window without saving files to disk.
 *
 * Supports arbitrary byte-range seeking (`DataSpec.position` & `DataSpec.length`) across
 * 18 MB chunk boundaries, enabling instant MP4/MKV/WebM/MP3/FLAC/WAV header probing and
 * timeline scrubbing for both plaintext and AES-256-GCM encrypted files.
 */
@OptIn(UnstableApi::class)
class TelegramChunkDataSource(
    private val file: FileEntity,
    private val chunks: List<ChunkEntity>,
    private val streamManager: EphemeralStreamManager
) : BaseDataSource(/* isNetwork = */ true) {

    class Factory(
        private val file: FileEntity,
        private val chunks: List<ChunkEntity>,
        private val streamManager: EphemeralStreamManager
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return TelegramChunkDataSource(file, chunks, streamManager)
        }
    }

    private var currentUri: Uri? = null
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened: Boolean = false

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        currentUri = dataSpec.uri

        val totalLength = streamManager.computeTotalPlaintextSize(file, chunks)
        if (totalLength <= 0L) {
            throw IOException("Cannot open zero-length or unindexed cloud stream for ${file.name}")
        }

        readPosition = dataSpec.position
        if (readPosition < 0L || readPosition > totalLength) {
            throw IOException("Requested byte position $readPosition out of bounds (total=$totalLength)")
        }

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            minOf(dataSpec.length, (totalLength - readPosition).coerceAtLeast(0L))
        } else {
            (totalLength - readPosition).coerceAtLeast(0L)
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = minOf(length.toLong(), bytesRemaining).toInt()
        val bytesRead = try {
            runBlocking(Dispatchers.IO) {
                streamManager.readStreamRange(
                    file = file,
                    chunks = chunks,
                    globalPosition = readPosition,
                    buffer = buffer,
                    offset = offset,
                    length = bytesToRead
                )
            }
        } catch (e: Exception) {
            throw IOException("Zero-download stream read error at byte $readPosition: ${e.message}", e)
        }

        if (bytesRead == -1) {
            bytesRemaining = 0L
            return C.RESULT_END_OF_INPUT
        }

        readPosition += bytesRead
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining = (bytesRemaining - bytesRead).coerceAtLeast(0L)
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        bytesRemaining = 0L
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
