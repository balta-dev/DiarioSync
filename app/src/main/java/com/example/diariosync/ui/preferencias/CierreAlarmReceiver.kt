package com.example.diariosync.ui.preferencias

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.diariosync.data.repository.OperacionRepository
import com.example.diariosync.util.CierreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CierreAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = OperacionRepository(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val operaciones = repository.getOperacionesActuales()
                CierreManager.ejecutar(context, operaciones)
            } catch (e: Exception) {
                android.util.Log.e("CierreAlarm", "Error: ${e.message}")
            }
        }
    }
}