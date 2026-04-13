package com.example.diariosync

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network

class DiarioSyncApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                android.util.Log.d("Connectivity", "Red disponible, encolando worker")
                com.example.diariosync.sync.SyncWorker.encolar(applicationContext)
            }
        })
    }
}