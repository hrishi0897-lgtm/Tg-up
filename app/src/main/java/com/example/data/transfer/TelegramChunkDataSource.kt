package com.example.data.transfer

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Custom Media3 [DataSource] that streams video and audio files directly from Telegram chunks
 * via [EphemeralStreamManager]'s 20–50 MB RAM sliding window without saving files to disk.
 *
 * Also supports delegating side-loaded subtitle URIs (`content://` from the system document picker)
 * to [DefaultDataSource] so external `.srt` and `.vtt` files can be read directly through the
 * picker URI without copying them to storage.
 */
@OptIn(UnstableApi::class)
class TelegramChunkDataSource(
    private val context: Context?,
    private val file: FileEntity,
    private val chunks: List<ChunkEntity>,
    private val streamManager: EphemeralStreamManager
) : BaseDataSource(/* isNetwork = */ true) {

    class Factory(
        private val file: FileEntity,
        private val chunks: List<ChunkEntity>,
        private val streamManager: EphemeralStreamManager,
        private val context: Context? = null
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return TelegramChunkDataSource(
                context = context?.applicationContext,
                file = file,
                chunks = chunks,
                streamManager = streamManager
            )
        }
    }

    private var currentUri: Uri? = null
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened: Boolean = false
    private var fallbackDelegate: DataSource? = null

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme?.lowercase()
        if (scheme != "televault" && context != null) {
            // Side-loaded external subtitle URI (e.g., content:// from system document picker)
            val delegate = DefaultDataSource.Factory(context).createDataSource()
            fallbackDelegate = delegate
            currentUri = dataSpec.uri
            return delegate.open(dataSpec)
        }

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
        val delegate = fallbackDelegate
        if (delegate != null) {
            return delegate.read(buffer, offset, length)
        }

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

    override fun getUri(): Uri? = fallbackDelegate?.uri ?: currentUri

    override fun close() {
        val delegate = fallbackDelegate
        if (delegate != null) {
            fallbackDelegate = null
            currentUri = null
            delegate.close()
            return
        }

        currentUri = null
        bytesRemaining = 0L
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
