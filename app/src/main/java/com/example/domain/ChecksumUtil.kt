package com.example.domain

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object ChecksumUtil {

    /**
     * Computes the SHA-256 checksum of an entire file.
     * Uses a buffered stream to handle large files without excessive memory usage.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(64 * 1024).use { input ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the SHA-256 checksum of a byte array.
     */
    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the SHA-256 checksum of a specific byte range within a file
     * without creating intermediate files or loading the entire range into memory.
     */
    fun computeSha256Range(file: File, offset: Long, length: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val buffer = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read == -1) break
                digest.update(buffer, 0, read)
                remaining -= read
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the SHA-256 checksum from an InputStream up to [length] bytes.
     */
    fun computeSha256(input: InputStream, length: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            digest.update(buffer, 0, read)
            remaining -= read
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private val dateFormatterThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())
        }
    }

    /**
     * Formats bytes to human-readable string (e.g. 1.25 GB, 45.2 MB).
     * Uses efficient threshold branches to avoid heavy log/pow math per composition.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes < 1024L * 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.1f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatBytes(bytes: Long): String = formatFileSize(bytes)

    /**
     * Formats transfer speed (e.g. 2.4 MB/s)
     */
    fun formatSpeed(bytesPerSec: Long): String {
        return "${formatFileSize(bytesPerSec)}/s"
    }

    /**
     * Formats estimated time remaining into a concise, human-readable string.
     * Examples: "45s left", "2m 15s left", "1h 10m left", "< 1s left"
     */
    fun formatEta(etaSeconds: Long?): String {
        if (etaSeconds == null) return "Estimating…"
        if (etaSeconds <= 0) return "Almost done"
        val hours = etaSeconds / 3600
        val minutes = (etaSeconds % 3600) / 60
        val seconds = etaSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m left"
            minutes > 0 -> "${minutes}m ${seconds}s left"
            else -> "${seconds}s left"
        }
    }

    /**
     * Formats timestamp into clean date string.
     * Reuses ThreadLocal SimpleDateFormat to avoid heavy object allocation and pattern parsing per item.
     */
    fun formatDate(timestamp: Long): String {
        val sdf = dateFormatterThreadLocal.get() ?: SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
