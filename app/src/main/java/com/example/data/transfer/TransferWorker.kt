package com.example.data.transfer

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FileStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resilient WorkManager Worker that triggers background resumption of pending,
 * paused, or failed transfers when network connectivity is verified.
 */
class TransferWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val transferManager = TransferManager.getInstance(applicationContext)

            // Look for any files interrupted mid-transfer (PENDING, PAUSED, UPLOADING, DOWNLOADING)
            val unfinishedFiles = db.fileDao().getFilesByStatus(
                listOf(
                    FileStatus.PENDING,
                    FileStatus.PAUSED,
                    FileStatus.UPLOADING,
                    FileStatus.DOWNLOADING
                )
            )

            for (file in unfinishedFiles) {
                // Resume upload or download depending on missing chunks
                val chunks = db.chunkDao().getChunksForFile(file.id)
                val hasUnuploadedChunks = chunks.any { !it.isUploaded }

                if (hasUnuploadedChunks) {
                    transferManager.startUpload(file.id)
                } else if (file.localPath == null) {
                    transferManager.startDownload(file.id)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "televault_network_resume_work"

        fun scheduleNetworkResume(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<TransferWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (e: Exception) {
                android.util.Log.w("TransferWorker", "Could not initialize WorkManager: ${e.message}")
            }
        }
    }
}
