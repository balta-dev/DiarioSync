package com.example.diariosync.data.mapper

import com.example.diariosync.data.local.OperacionEntity
import com.example.diariosync.domain.model.Operacion
import com.example.diariosync.domain.model.TipoOperacion

object OperacionMapper {

    fun OperacionEntity.toDomain() = Operacion(
        id        = id,
        tipo      = TipoOperacion.valueOf(tipo),
        producto  = producto,
        cantidad  = cantidad,
        precio    = precio,
        timestamp = timestamp,
        deviceId  = deviceId,
        synced    = synced,
        activa    = activa,   // <--- NUEVO
        jornadaId = jornadaId // <--- NUEVO
    )

    fun Operacion.toEntity() = OperacionEntity(
        id        = id,
        tipo      = tipo.name,
        producto  = producto,
        cantidad  = cantidad,
        precio    = precio,
        timestamp = timestamp,
        deviceId  = deviceId,
        synced    = synced,
        activa    = activa,
        jornadaId = jornadaId
    )

    fun OperacionEntity.toFirestoreMap(): Map<String, Any> = mapOf(
        "id"        to id,
        "tipo"      to tipo,
        "producto"  to producto,
        "cantidad"  to cantidad,
        "precio"    to precio,
        "timestamp" to timestamp,
        "deviceId"  to deviceId,
        "synced"    to synced,
        "activa"    to activa,
        "jornadaId" to jornadaId
    )

    fun Map<String, Any>.toEntity() = OperacionEntity(
        id        = this["id"] as String,
        tipo      = this["tipo"] as String,
        producto  = this["producto"] as String,
        cantidad  = (this["cantidad"] as Number).toDouble(),
        precio    = (this["precio"] as Number).toDouble(),
        timestamp = (this["timestamp"] as Number).toLong(),
        deviceId  = this["deviceId"] as String,
        synced    = true,
        activa    = this["activa"] as? Boolean ?: true,
        jornadaId = this["jornadaId"] as? String ?: ""
    )
}