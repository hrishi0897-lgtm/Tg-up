package com.example

import android.app.Application
import com.example.data.network.NetworkMonitor

class TeleVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register network change monitor to proactively evict stale OkHttp connections
        NetworkMonitor.register(this)
    }
}
