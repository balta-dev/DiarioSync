package com.example.diariosync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OperacionDao {

    @Query("SELECT * FROM operaciones ORDER BY timestamp ASC")
    fun observarTodas(): Flow<List<OperacionEntity>>

    @Query("SELECT * FROM operaciones WHERE synced = 0")
    suspend fun getPendientes(): List<OperacionEntity>

    // REPLACE en lugar de IGNORE: si baja un dato remoto con el mismo ID, lo pisa
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(op: OperacionEntity)

    @Query("UPDATE operaciones SET synced = 1 WHERE id = :id")
    suspend fun marcarSynced(id: String)

    @Query("SELECT MAX(timestamp) FROM operaciones WHERE synced = 1")
    suspend fun getTimestampUltimaSync(): Long?

    @Query("DELETE FROM operaciones WHERE id = :id")
    suspend fun eliminarPorId(id: String)

    @Query("SELECT * FROM operaciones WHERE activa = 1 ORDER BY timestamp ASC")
    fun observarActivas(): Flow<List<OperacionEntity>>

    @Query("UPDATE operaciones SET activa = 0 WHERE activa = 1")
    suspend fun cerrarJornadaLocal()

    @Query("SELECT * FROM operaciones WHERE activa = 0 ORDER BY timestamp DESC")
    fun observarHistorial(): Flow<List<OperacionEntity>>

    @Query("SELECT * FROM operaciones WHERE activa = 0 ORDER BY timestamp DESC")
    fun getOperacionesCerradas(): Flow<List<OperacionEntity>>

    @Query("SELECT * FROM operaciones WHERE activa = 1")
    suspend fun getActivas(): List<OperacionEntity>

    @Query("DELETE FROM operaciones WHERE activa = 0")
    suspend fun borrarHistorialLocal()

    // Limpia todo Room — se llama al crear/unirse a una agenda nueva
    @Query("DELETE FROM operaciones")
    suspend fun borrarTodo()

    @Update
    suspend fun actualizar(op: OperacionEntity)
}