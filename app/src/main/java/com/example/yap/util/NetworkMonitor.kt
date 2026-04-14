package com.example.yap.util

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission

class NetworkMonitor(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isOnline: Boolean
        @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        get() {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    )
        }
}