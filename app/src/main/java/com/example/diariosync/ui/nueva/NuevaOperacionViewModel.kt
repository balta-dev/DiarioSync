package com.example.diariosync.ui.nueva

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.diariosync.data.repository.OperacionRepository
import com.example.diariosync.domain.model.Operacion
import com.example.diariosync.domain.model.TipoOperacion
import kotlinx.coroutines.launch

class NuevaOperacionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OperacionRepository(application)

    val producto = MutableLiveData("")
    val cantidad = MutableLiveData("")
    val precio = MutableLiveData("")
    val guardadoExitoso = MutableLiveData(false)
    val error = MutableLiveData<String?>()

    fun guardar(tipo: TipoOperacion, autor: String) {
        val prod = producto.value?.trim()
        val cant = cantidad.value?.toDoubleOrNull()
        val prec = precio.value?.toDoubleOrNull()

        when {
            prod.isNullOrEmpty()      -> { error.value = "Ingresá un producto"; return }
            cant == null || cant <= 0 -> { error.value = "Cantidad inválida"; return }
            prec == null || prec <= 0 -> { error.value = "Precio inválido"; return }
        }

        viewModelScope.launch {
            try {
                repository.registrar(tipo, prod!!, cant!!, prec!!, autor)
                guardadoExitoso.value = true
            } catch (e: Exception) {
                error.value = "Error al guardar: ${e.message}"
            }
        }
    }

    fun actualizar(operacion: Operacion, tipo: TipoOperacion) {
        val prod = producto.value?.trim()
        val cant = cantidad.value?.toDoubleOrNull()
        val prec = precio.value?.toDoubleOrNull()

        when {
            prod.isNullOrEmpty()      -> { error.value = "Ingresá un producto"; return }
            cant == null || cant <= 0 -> { error.value = "Cantidad inválida"; return }
            prec == null || prec <= 0 -> { error.value = "Precio inválido"; return }
        }

        viewModelScope.launch {
            try {
                repository.actualizar(
                    operacion.copy(
                        tipo = tipo,
                        producto = prod!!,
                        cantidad = cant!!,
                        precio = prec!!,
                        synced = false
                    )
                )
                guardadoExitoso.value = true
            } catch (e: Exception) {
                error.value = "Error al actualizar: ${e.message}"
            }
        }
    }

    fun resetEstado() {
        guardadoExitoso.value = false
        error.value = null
    }
}