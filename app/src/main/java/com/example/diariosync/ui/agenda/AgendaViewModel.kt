package com.example.diariosync.ui.agenda

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.diariosync.data.repository.OperacionRepository
import com.example.diariosync.util.DeviceIdProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AgendaViewModel(application: Application) : AndroidViewModel(application) {

    val repository = OperacionRepository(application)
    private val deviceId = DeviceIdProvider.getId(application)

    // Lee el correo guardado en prefs (ya lo guardó MainActivity al iniciar)
    private val correo: String
        get() = getApplication<Application>()
            .getSharedPreferences("device_prefs", android.content.Context.MODE_PRIVATE)
            .getString("user_email", "") ?: ""

    sealed class Estado {
        object Idle : Estado()
        object Cargando : Estado()
        object Exito : Estado()
        data class Error(val mensaje: String) : Estado()
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Idle)
    val estado: StateFlow<Estado> = _estado

    fun crearAgenda(codigo: String, password: String) {
        viewModelScope.launch {
            _estado.value = Estado.Cargando
            val result = repository.crearAgenda(codigo, password, deviceId, correo)
            _estado.value = result.fold(
                onSuccess = { Estado.Exito },
                onFailure = { Estado.Error(it.message ?: "Error desconocido") }
            )
        }
    }

    fun unirseAAgenda(codigo: String, password: String) {
        viewModelScope.launch {
            _estado.value = Estado.Cargando
            val result = repository.unirseAAgenda(codigo, password, deviceId, correo)
            _estado.value = result.fold(
                onSuccess = { Estado.Exito },
                onFailure = { Estado.Error(it.message ?: "Error desconocido") }
            )
        }
    }
}