package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.example.data.remote.TelegramRepository
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Proactively monitors network changes (e.g. switching between Wi-Fi and mobile data,
 * cell tower handoffs, reconnecting after brief drops or airplane mode) and immediately
 * evicts stale/dead sockets from the OkHttp connection pool.
 */
object NetworkMonitor {
    private const val TAG = "NetworkMonitor"
    private val isRegistered = AtomicBoolean(false)
    private var lastActiveNetwork: Network? = null

    fun register(context: Context) {
        if (!isRegistered.compareAndSet(false, true)) {
            Log.d(TAG, "NetworkMonitor is already registered")
            return
        }

        val appContext = context.applicationContext
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            Log.w(TAG, "ConnectivityManager not available; cannot monitor network changes")
            return
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val previousNetwork = lastActiveNetwork
                lastActiveNetwork = network
                val isNetworkSwitch = previousNetwork != null && previousNetwork != network

                Log.i(
                    TAG,
                    "Active network available: $network (switched=$isNetworkSwitch, previous=$previousNetwork). " +
                    "Proactively evicting OkHttp connection pool to prevent stale connection reuse."
                )
                TelegramRepository.evictSharedConnectionPool("Network available / switch (to $network)")
            }

            override fun onLost(network: Network) {
                Log.i(
                    TAG,
                    "Active network lost: $network. Evicting OkHttp connection pool."
                )
                if (lastActiveNetwork == network) {
                    lastActiveNetwork = null
                }
                TelegramRepository.evictSharedConnectionPool("Network lost ($network)")
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.d(TAG, "Network capabilities changed: $network | internet=$hasInternet, validated=$isValidated")
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
            Log.i(TAG, "Registered ConnectivityManager default network callback successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register default network callback: ${e.message}", e)
        }
    }
}
