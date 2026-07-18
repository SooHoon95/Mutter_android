package com.efreedom.mutter.core.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 네트워크 연결 상태 관찰(iOS AppFoundation NetworkMonitor 대응). ConnectivityManager 기반. */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isConnected.value = true
        }

        override fun onLost(network: Network) {
            _isConnected.value = hasInternet()
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
        _isConnected.value = hasInternet()
    }

    private fun hasInternet(): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
