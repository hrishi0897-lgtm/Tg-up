package com.example

import android.app.Application
import com.example.data.network.NetworkMonitor
import com.example.data.transfer.EphemeralStreamManager

class TeleVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Startup crash recovery: purge any orphaned ephemeral stream buffers
        EphemeralStreamManager.clearOrphanedEphemeralBuffers(this)
        // Register network change monitor to proactively evict stale OkHttp connections
        NetworkMonitor.register(this)
    }
}
