package com.example.data.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.domain.ChecksumUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TransferService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationTimeMs = 0L
    private var lastNotifiedStatus: com.example.data.local.entity.FileStatus? = null

    companion object {
        private const val TAG = "TransferService"
        const val CHANNEL_ID = "televault_transfers"
        const val CHANNEL_NAME = "TeleVault Transfers"
        const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE_STATUS = "com.example.televault.UPDATE_STATUS"
        const val ACTION_OPEN_TRANSFERS = "com.example.televault.OPEN_TRANSFERS"
        const val EXTRA_NAVIGATE_TO = "extra_navigate_to"
        const val DESTINATION_TRANSFERS = "transfers"
        const val EXTRA_MESSAGE = "extra_message"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val initialNotification = buildNotification("TeleVault Transfer Engine Active", 0, 0, "Transfer service running…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        observeTransfers()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null || wakeLock?.isHeld == false) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "TeleVault::TransferWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L) // 24-hour safe acquisition
                }
                Log.d(TAG, "Acquired WakeLock for long-running transfer")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Released WakeLock (no active transfers)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
    }

    private fun observeTransfers() {
        val transferManager = TransferManager.getInstance(applicationContext)
        serviceScope.launch {
            transferManager.transfers.collectLatest { transfersMap ->
                val activeList = transfersMap.values.filter {
                    it.status == com.example.data.local.entity.FileStatus.UPLOADING ||
                            it.status == com.example.data.local.entity.FileStatus.DOWNLOADING
                }

                if (activeList.isNotEmpty()) {
                    acquireWakeLock()
                    val active = activeList.first()
                    val percent = (active.progressFraction * 100).toInt()
                    val speed = if (active.speedBytesPerSec > 0) ChecksumUtil.formatSpeed(active.speedBytesPerSec) else "Calculating…"
                    val actionLabel = if (active.isUpload) "Uploading" else "Downloading"

                    val totalTransfers = transfersMap.size
                    val title = if (totalTransfers > 1) {
                        val activeIndex = (transfersMap.values.indexOf(active) + 1).coerceIn(1, totalTransfers)
                        "$actionLabel $activeIndex of $totalTransfers files — $percent%"
                    } else {
                        "$actionLabel ${active.fileName} — $percent%"
                    }

                    val content = if (active.activeConcurrentChunks > 1) {
                        "${active.activeConcurrentChunks} chunks uploading (${active.completedChunksCount}/${active.totalChunks} done) · $speed"
                    } else {
                        "Chunk ${active.currentChunk} of ${active.totalChunks} · $speed"
                    }

                    val updatedNotification = buildNotification(
                        title = title,
                        progress = percent,
                        maxProgress = 100,
                        content = content,
                        isOngoing = true
                    )
                    val now = android.os.SystemClock.elapsedRealtime()
                    val isStatusChange = (percent == 100) || (active.status != lastNotifiedStatus)
                    if (isStatusChange || (now - lastNotificationTimeMs >= 500L)) {
                        lastNotificationTimeMs = now
                        lastNotifiedStatus = active.status
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }
                } else {
                    val failedTransfer = transfersMap.values.firstOrNull { it.status == com.example.data.local.entity.FileStatus.FAILED }
                    val hasPaused = transfersMap.values.any { it.status == com.example.data.local.entity.FileStatus.PAUSED }
                    releaseWakeLock()
                    if (failedTransfer != null) {
                        val failedNotification = buildNotification(
                            title = "Transfer Failed: ${failedTransfer.fileName}",
                            progress = 0,
                            maxProgress = 0,
                            content = failedTransfer.errorMessage ?: "Tap to view and retry transfer",
                            isOngoing = false
                        )
                        notificationManager.notify(NOTIFICATION_ID, failedNotification)
                    } else if (hasPaused) {
                        val pausedNotification = buildNotification(
                            title = "Transfers Paused",
                            progress = 0,
                            maxProgress = 0,
                            content = "Tap to view and resume pending transfers",
                            isOngoing = false
                        )
                        notificationManager.notify(NOTIFICATION_ID, pausedNotification)
                    } else {
                        // Truly idle: detach foreground and stop service cleanly
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        maxProgress: Int,
        content: String,
        isOngoing: Boolean = true
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_TRANSFERS
            putExtra(EXTRA_NAVIGATE_TO, DESTINATION_TRANSFERS)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (maxProgress > 0) {
            builder.setProgress(maxProgress, progress, false)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for TeleVault chunked uploads and downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // State updates are driven reactively through observeTransfers()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
