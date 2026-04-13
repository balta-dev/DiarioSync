package com.example.diariosync.ui.lista

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.diariosync.data.repository.OperacionRepository
import com.example.diariosync.domain.model.CajaHistorial
import com.example.diariosync.domain.model.Operacion
import com.example.diariosync.domain.model.TipoOperacion
import com.example.diariosync.util.DeviceIdProvider
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ListaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OperacionRepository(application)
    private val deviceId = DeviceIdProvider.getId(application)

    enum class OrdenarPor {
        RECIENTES, ANTIGUOS, MAYOR_MONTO, MENOR_MONTO
    }

    val operaciones: LiveData<List<Operacion>> =
        repository.operaciones.asLiveData()

    val historial: LiveData<List<Operacion>> =
        repository.historial.asLiveData()

    // ── Estas tres tienen que estar ANTES del init ──
    private val _operacionesVisibles = androidx.lifecycle.MutableLiveData<List<Operacion>>()
    val operacionesVisibles: LiveData<List<Operacion>> = _operacionesVisibles

    private val _scrollArriba = androidx.lifecycle.MutableLiveData<Boolean>()
    val scrollArriba: LiveData<Boolean> = _scrollArriba

    private var _forzarScroll = false
    private var ultimoTamanio = 0
    // ────────────────────────────────────────────────

    private var filtroTipo: Boolean? = null
    private var ordenUI: OrdenarPor = OrdenarPor.RECIENTES

    // Expone el código de agenda para mostrarlo en la UI
    fun getAgendaId(): String? = repository.getAgendaId()

    init {
        repository.iniciarEscuchaRemota()
        operaciones.observeForever { lista ->
            if (lista != null) procesarListaParaUI(lista)
        }
    }

    fun eliminarOperacion(operacion: Operacion) {
        viewModelScope.launch { repository.eliminar(operacion) }
    }

    fun cerrarCaja() {
        viewModelScope.launch { repository.cerrarCaja() }
    }

    fun vaciarHistorial() {
        viewModelScope.launch { repository.vaciarHistorialRemoto() }
    }

    /**
     * Sale de la agenda: limpia Firestore (si es el último), limpia Room,
     * y borra el agenda_id de prefs. El Fragment navega a AgendaFragment después.
     */
    fun salirDeAgenda(onCompletado: () -> Unit) {
        viewModelScope.launch {
            repository.salirDeAgenda(deviceId)
            onCompletado()
        }
    }

    val historialAgrupado: LiveData<List<CajaHistorial>> = repository.historial.map { lista ->
        lista.groupBy { it.jornadaId }
            .map { (id, ops) ->
                CajaHistorial(
                    jornadaId    = id,
                    operaciones  = ops,
                    totalVentas  = ops.filter { it.tipo == TipoOperacion.VENTA }.sumOf { it.total },
                    totalCompras = ops.filter { it.tipo == TipoOperacion.COMPRA }.sumOf { it.total }
                )
            }
    }.asLiveData()

    fun getFiltroTipo() = filtroTipo
    fun getOrdenUI() = ordenUI

    fun actualizarFiltros(soloVentas: Boolean?, orden: OrdenarPor) {
        filtroTipo = soloVentas
        ordenUI = orden
        _forzarScroll = true  // filtrar/ordenar siempre scrollea
        operaciones.value?.let { procesarListaParaUI(it) }
    }

    private fun procesarListaParaUI(lista: List<Operacion>) {
        var filtrada = when (filtroTipo) {
            true  -> lista.filter { it.tipo == TipoOperacion.VENTA }
            false -> lista.filter { it.tipo == TipoOperacion.COMPRA }
            else  -> lista
        }
        filtrada = when (ordenUI) {
            OrdenarPor.RECIENTES   -> filtrada.sortedByDescending { it.timestamp }
            OrdenarPor.ANTIGUOS    -> filtrada.sortedBy { it.timestamp }
            OrdenarPor.MAYOR_MONTO -> filtrada.sortedByDescending { it.total }
            OrdenarPor.MENOR_MONTO -> filtrada.sortedBy { it.total }
        }

        val debeScrollear = _forzarScroll || filtrada.size > ultimoTamanio
        ultimoTamanio = filtrada.size
        _forzarScroll = false

        _operacionesVisibles.value = filtrada
        if (debeScrollear) _scrollArriba.value = true
    }

    fun consumirScrollArriba() {
        _scrollArriba.value = false
    }

    suspend fun sincronizarManual(): OperacionRepository.ResultadoSync {
        return repository.sincronizarManual()
    }

}