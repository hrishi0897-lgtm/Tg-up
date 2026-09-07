package com.example.domain

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import java.io.File

/**
 * Utility helper for large file storage capacity checks, network constraints,
 * and system power optimization status.
 */
object StorageUtil {

    private const val TAG = "StorageUtil"
    // Minimum safety buffer (100MB) left unallocated on device storage
    private const val SAFETY_HEADROOM_BYTES = 100 * 1024 * 1024L

    /**
     * Retrieves the available free space on the internal/data partition in bytes.
     */
    fun getAvailableStorageBytes(context: Context): Long {
        return try {
            val path = context.filesDir ?: context.cacheDir ?: File("/data")
            val stat = StatFs(path.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect available storage via StatFs: ${e.message}")
            Long.MAX_VALUE // Fail open if stat fails unexpectedly
        }
    }

    /**
     * Checks if there is enough storage for an upload operation.
     * Upload needs room for the cached staging file + its transient chunk files.
     * Required space ≈ fileSize + CHUNK_SIZE + 100MB safety buffer.
     * If staging file is already cached on disk, only the chunk file + safety headroom is needed.
     */
    fun checkStorageForUpload(context: Context, fileSize: Long, stagingFileExists: Boolean): StorageCheckResult {
        val freeBytes = getAvailableStorageBytes(context)
        val neededBytes = if (stagingFileExists) {
            (20 * 1024 * 1024L) + SAFETY_HEADROOM_BYTES
        } else {
            fileSize + (20 * 1024 * 1024L) + SAFETY_HEADROOM_BYTES
        }

        return if (freeBytes >= neededBytes) {
            StorageCheckResult(isSufficient = true, freeBytes = freeBytes, neededBytes = neededBytes)
        } else {
            val freeFmt = ChecksumUtil.formatBytes(freeBytes)
            val neededFmt = ChecksumUtil.formatBytes(neededBytes)
            StorageCheckResult(
                isSufficient = false,
                freeBytes = freeBytes,
                neededBytes = neededBytes,
                errorMessage = "Not enough storage to upload file. Free: $freeFmt, Required: $neededFmt."
            )
        }
    }

    /**
     * Checks if there is enough storage for a download operation.
     * During download, both the downloaded chunks (fileSize) and the final reassembled
     * file (fileSize) exist simultaneously in storage until temp chunks are cleared.
     * Required space = (2 * fileSize) + 100MB safety headroom.
     */
    fun checkStorageForDownload(context: Context, fileSize: Long): StorageCheckResult {
        val freeBytes = getAvailableStorageBytes(context)
        val neededBytes = (fileSize * 2) + SAFETY_HEADROOM_BYTES

        return if (freeBytes >= neededBytes) {
            StorageCheckResult(isSufficient = true, freeBytes = freeBytes, neededBytes = neededBytes)
        } else {
            val freeFmt = ChecksumUtil.formatBytes(freeBytes)
            val neededFmt = ChecksumUtil.formatBytes(neededBytes)
            StorageCheckResult(
                isSufficient = false,
                freeBytes = freeBytes,
                neededBytes = neededBytes,
                errorMessage = "Not enough storage. Downloading and reassembling this file requires ~$neededFmt (chunks + final file), but only $freeFmt is available."
            )
        }
    }

    /**
     * Determines whether the active network connection is Wi-Fi (or unmetered Ethernet).
     */
    fun isConnectedToWifi(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check WiFi status: ${e.message}")
            false
        }
    }

    /**
     * Checks if the app is currently ignoring battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Builds an Intent to prompt the user to exempt TeleVault from battery optimizations / Doze
     * so long-running transfers (100+ chunks) are never throttled or killed by the OS.
     */
    fun createBatteryOptimizationIntent(context: Context): Intent {
        return Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Fallback Intent to open the general Battery Optimization settings screen if direct prompt is disallowed.
     */
    fun createBatterySettingsFallbackIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}

data class StorageCheckResult(
    val isSufficient: Boolean,
    val freeBytes: Long,
    val neededBytes: Long,
    val errorMessage: String? = null
)
