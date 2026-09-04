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
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.domain.ChecksumUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TransferService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val CHANNEL_ID = "televault_transfers"
        const val CHANNEL_NAME = "TeleVault Transfers"
        const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE_STATUS = "com.example.televault.UPDATE_STATUS"
        const val EXTRA_MESSAGE = "extra_message"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val initialNotification = buildNotification("TeleVault Transfer Engine Active", 0, 0, "")
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

    private fun observeTransfers() {
        val transferManager = TransferManager.getInstance(applicationContext)
        serviceScope.launch {
            transferManager.transfers.collectLatest { transfersMap ->
                val activeList = transfersMap.values.filter {
                    it.status == com.example.data.local.entity.FileStatus.UPLOADING ||
                            it.status == com.example.data.local.entity.FileStatus.DOWNLOADING
                }

                if (activeList.isNotEmpty()) {
                    val active = activeList.first()
                    val percent = (active.progressFraction * 100).toInt()
                    val speed = ChecksumUtil.formatSpeed(active.speedBytesPerSec)
                    val actionLabel = if (active.isUpload) "Uploading" else "Downloading"
                    val content = "$actionLabel ${active.fileName} (Chunk ${active.currentChunk}/${active.totalChunks}) · $percent% · $speed"

                    val updatedNotification = buildNotification(
                        title = "$actionLabel in progress",
                        progress = percent,
                        maxProgress = 100,
                        content = content
                    )
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                } else {
                    // No active transfers running, check if anything is paused or failed
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
            }
        }
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        maxProgress: Int,
        content: String
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
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
            .setOngoing(true)
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
        val msg = intent?.getStringExtra(EXTRA_MESSAGE)
        if (!msg.isNullOrBlank()) {
            val notification = buildNotification("TeleVault Transfer Service", 0, 0, msg)
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
