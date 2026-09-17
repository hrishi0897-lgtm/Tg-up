package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object ThumbnailUtil {
    private const val TAG = "ThumbnailUtil"
    const val MAX_THUMBNAIL_DIMENSION = 200
    private const val JPEG_QUALITY = 80

    fun isImage(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        return mimeType.startsWith("image/", ignoreCase = true)
    }

    fun isVideo(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        return mimeType.startsWith("video/", ignoreCase = true)
    }

    fun isMedia(mimeType: String?): Boolean {
        return isImage(mimeType) || isVideo(mimeType)
    }

    fun getThumbnailFile(context: Context, fileId: String): File {
        val dir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
        return File(dir, "${fileId}.jpg")
    }

    /**
     * Generates a downscaled thumbnail (max 200px) from a local source file (image or video).
     * Compresses to JPEG in the app's cache directory.
     */
    fun generateThumbnail(
        context: Context,
        sourceFile: File,
        mimeType: String,
        fileId: String
    ): File? {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return null
        val targetFile = getThumbnailFile(context, fileId)

        return try {
            val bitmap = when {
                isImage(mimeType) -> decodeSampledBitmapFromFile(sourceFile, MAX_THUMBNAIL_DIMENSION, MAX_THUMBNAIL_DIMENSION)
                isVideo(mimeType) -> extractVideoFrame(sourceFile)
                else -> null
            } ?: return null

            val scaled = scaleDown(bitmap, MAX_THUMBNAIL_DIMENSION)
            saveBitmapToJpeg(scaled, targetFile)

            if (scaled !== bitmap) {
                scaled.recycle()
            }
            bitmap.recycle()

            if (targetFile.exists() && targetFile.length() > 0L) {
                Log.i(TAG, "Generated thumbnail for $fileId (${sourceFile.name}): ${targetFile.length()} bytes")
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate thumbnail for $fileId: ${e.message}", e)
            null
        }
    }

    /**
     * Generates a downscaled thumbnail from a content:// or file:// Uri.
     */
    fun generateThumbnailFromUri(
        context: Context,
        uri: Uri,
        mimeType: String,
        fileId: String
    ): File? {
        val targetFile = getThumbnailFile(context, fileId)
        return try {
            val bitmap = when {
                isImage(mimeType) -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val original = BitmapFactory.decodeStream(stream)
                        original
                    }
                }
                isVideo(mimeType) -> extractVideoFrameFromUri(context, uri)
                else -> null
            } ?: return null

            val scaled = scaleDown(bitmap, MAX_THUMBNAIL_DIMENSION)
            saveBitmapToJpeg(scaled, targetFile)

            if (scaled !== bitmap) {
                scaled.recycle()
            }
            bitmap.recycle()

            if (targetFile.exists() && targetFile.length() > 0L) {
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate thumbnail from Uri for $fileId: ${e.message}", e)
            null
        }
    }

    private fun decodeSampledBitmapFromFile(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun extractVideoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            // Try extracting at 1 second, or fallback to first frame
            retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)
        } catch (e: Exception) {
            Log.w(TAG, "extractVideoFrame error: ${e.message}")
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun extractVideoFrameFromUri(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)
        } catch (e: Exception) {
            Log.w(TAG, "extractVideoFrameFromUri error: ${e.message}")
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val (targetWidth, targetHeight) = if (width >= height) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun saveBitmapToJpeg(bitmap: Bitmap, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.flush()
        }
    }
}
