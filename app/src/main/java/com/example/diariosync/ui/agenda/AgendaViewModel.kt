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

    sealed class Estado {
        object Idle : Estado()
        object Cargando : Estado()
        object Exito : Estado()
        data class Error(val mensaje: String) : Estado()
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Idle)
    val estado: StateFlow<Estado> = _estado

    fun crearAgenda(codigo: String) {
        viewModelScope.launch {
            _estado.value = Estado.Cargando
            val result = repository.crearAgenda(codigo, deviceId)
            _estado.value = result.fold(
                onSuccess = { Estado.Exito },
                onFailure = { Estado.Error(it.message ?: "Error desconocido") }
            )
        }
    }

    fun unirseAAgenda(codigo: String) {
        viewModelScope.launch {
            _estado.value = Estado.Cargando
            val result = repository.unirseAAgenda(codigo, deviceId)
            _estado.value = result.fold(
                onSuccess = { Estado.Exito },
                onFailure = { Estado.Error(it.message ?: "Error desconocido") }
            )
        }
    }
}