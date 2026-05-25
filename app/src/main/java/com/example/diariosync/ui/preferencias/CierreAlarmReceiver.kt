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

        // El truco de Android para que el OS no nos mate la corrutina de fondo a mitad de camino
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val operaciones = repository.getOperacionesActuales()

                // Corregido: Pasamos la lambda de progreso apuntando al Logcat
                CierreManager.ejecutar(context, operaciones) { progreso ->
                    android.util.Log.d("CierreAlarm", "[AUTOMÁTICO] -> $progreso")
                }

                android.util.Log.i("CierreAlarm", "Cierre automático finalizado con éxito.")
            } catch (e: Exception) {
                android.util.Log.e("CierreAlarm", "Error en el cierre automático: ${e.message}")
            } finally {
                // Le avisamos al OS que ya terminamos y puede liberar los recursos
                pendingResult.finish()
            }
        }
    }
}