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
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
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
     * Computes the SHA-256 checksum from an InputStream up to [length] bytes.
     */
    fun computeSha256(input: InputStream, length: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
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

    /**
     * Formats bytes to human-readable string (e.g. 1.25 GB, 45.2 MB)
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
        if (digitGroups == 0) return "$bytes B"
        val formatted = bytes / 1024.0.pow(digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", formatted, units[digitGroups])
    }

    /**
     * Formats transfer speed (e.g. 2.4 MB/s)
     */
    fun formatSpeed(bytesPerSec: Long): String {
        return "${formatFileSize(bytesPerSec)}/s"
    }

    /**
     * Formats timestamp into clean date string.
     */
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
