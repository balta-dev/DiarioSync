package com.example.diariosync.util

import android.content.Context
import java.util.UUID

object DeviceIdProvider {

    private const val PREFS_NAME = "device_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    fun getId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            newId
        }
    }
}