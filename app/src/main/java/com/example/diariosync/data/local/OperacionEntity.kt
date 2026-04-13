package com.example.diariosync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "operaciones")
data class OperacionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val tipo: String,
    val producto: String,
    val cantidad: Double,
    val precio: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,
    val synced: Boolean = false,
    val jornadaId: String, // <--- NUEVO: Para agrupar operaciones
    val activa: Boolean = true // <--- NUEVO: Para saber si es la caja actual
)