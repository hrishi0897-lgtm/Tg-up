package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager periodic worker that checks for a newer VAULT_INDEX every 15 minutes.
 * Requires NetworkType.CONNECTED.
 * Unobtrusive: does not post notifications or popups if vault is up-to-date.
 */
class VaultSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "tele_vault_periodic_sync"
        private const val TAG = "VaultSyncWorker"

        fun schedule(context: Context) {
            try {
                val creds = com.example.data.local.EncryptedCredentialsManager(context)
                val requiredNetwork = if (creds.isWifiOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(requiredNetwork)
                    .build()

                val request = PeriodicWorkRequestBuilder<VaultSyncWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
                Log.i(TAG, "Scheduled 15-minute periodic VaultSyncWorker ($requiredNetwork)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule VaultSyncWorker: ${e.message}", e)
            }
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
                Log.i(TAG, "Cancelled periodic VaultSyncWorker")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel VaultSyncWorker: ${e.message}", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Periodic background sync started by WorkManager...")
        val syncManager = VaultSyncManager.getInstance(applicationContext)
        return try {
            val result = syncManager.syncVault(onlyIfNewer = true, isManual = false)
            if (result.isSuccess) {
                val syncData = result.getOrThrow()
                Log.i(TAG, "Periodic sync finished: ${syncData.summary}")
                Result.success()
            } else {
                Log.w(TAG, "Periodic sync attempt failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Periodic sync exception: ${e.message}", e)
            Result.retry()
        }
    }
}
