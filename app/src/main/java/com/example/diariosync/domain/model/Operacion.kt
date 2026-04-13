package com.example.diariosync.domain.model

// domain/model/Operacion.kt
data class Operacion(
    val id: String,
    val tipo: TipoOperacion,
    val producto: String,
    val cantidad: Double,
    val precio: Double,
    val timestamp: Long,
    val deviceId: String,
    val synced: Boolean,
    val activa: Boolean, // <--- NUEVO
    val jornadaId: String // <--- NUEVO
) {
    val total: Double get() = cantidad * precio
}

enum class TipoOperacion { COMPRA, VENTA }