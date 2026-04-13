package com.example.diariosync.domain.model

data class CajaHistorial(
    val jornadaId: String,
    val operaciones: List<Operacion>,
    val totalVentas: Double,
    val totalCompras: Double
) {
    val cantOperaciones: Int get() = operaciones.size
}