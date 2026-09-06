package com.example.domain

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Manages saving downloaded and reassembled files into the device's public Downloads/TGC directory.
 * Adheres strictly to modern Android Scoped Storage using MediaStore.Downloads (API 29+).
 * Does not require legacy WRITE_EXTERNAL_STORAGE permissions.
 */
object DownloadStorageManager {

    private const val TAG = "DownloadStorageManager"
    const val TGC_FOLDER_NAME = "TGC"
    val RELATIVE_DOWNLOAD_PATH: String
        get() {
            val base = try {
                Environment.DIRECTORY_DOWNLOADS
            } catch (_: Throwable) {
                null
            }
            return "${if (!base.isNullOrBlank()) base else "Download"}/$TGC_FOLDER_NAME"
        }

    /**
     * Saves a verified, reassembled source file into the public Downloads/TGC directory.
     * On Android 10+ (API 29+), uses MediaStore.Downloads with RELATIVE_PATH set to "Download/TGC"
     * and IS_PENDING flags to ensure atomic visibility.
     * Duplicate file names are handled safely without overwriting.
     *
     * @return The MediaStore or public file content Uri.
     */
    fun saveToDownloadsTgc(
        context: Context,
        fileName: String,
        mimeType: String,
        sourceFile: File
    ): Uri {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DOWNLOAD_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val insertedUri = resolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Failed to insert file into MediaStore Downloads/$TGC_FOLDER_NAME")

            try {
                resolver.openOutputStream(insertedUri)?.use { outStream ->
                    sourceFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                    outStream.flush()
                } ?: throw IllegalStateException("Failed to open output stream for MediaStore URI: $insertedUri")

                // Mark file as complete (no longer pending) so it shows up immediately in Downloads app
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(insertedUri, contentValues, null, null)

                Log.i(TAG, "Successfully saved $fileName to MediaStore Downloads/$TGC_FOLDER_NAME (URI: $insertedUri)")
                return insertedUri
            } catch (e: Exception) {
                try {
                    resolver.delete(insertedUri, null, null)
                } catch (delEx: Exception) {
                    Log.w(TAG, "Failed to clean up pending MediaStore row: ${delEx.message}")
                }
                throw e
            }
        } else {
            // Android 9 and lower fallback
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val tgcDir = File(downloadsDir, TGC_FOLDER_NAME).apply { mkdirs() }
            val destFile = getUniqueFile(tgcDir, fileName)

            sourceFile.copyTo(destFile, overwrite = true)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(mimeType),
                null
            )

            Log.i(TAG, "Successfully saved $fileName to ${destFile.absolutePath}")
            return Uri.fromFile(destFile)
        }
    }

    /**
     * Determines a unique file handle if a file with the same name already exists in the destination folder.
     */
    fun getUniqueFile(directory: File, originalName: String): File {
        var file = File(directory, originalName)
        if (!file.exists()) return file

        val nameWithoutExt = file.nameWithoutExtension
        val extWithDot = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        var count = 1

        while (file.exists()) {
            file = File(directory, "$nameWithoutExt ($count)$extWithDot")
            count++
        }
        return file
    }
}
