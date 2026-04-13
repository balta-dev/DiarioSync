package com.example.diariosync.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.example.diariosync.data.local.AppDatabase
import com.example.diariosync.data.mapper.OperacionMapper.toEntity
import com.example.diariosync.data.mapper.OperacionMapper.toFirestoreMap
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class SyncWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val dao = AppDatabase.getInstance(ctx).operacionDao()

    // Lee la agenda activa del mismo prefs que usa OperacionRepository
    private val agendaId: String? =
        ctx.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
            .getString("agenda_id", null)

    override suspend fun doWork(): Result {
        if (agendaId == null) {
            android.util.Log.d("SyncWorker", "Sin agenda, saliendo")
            return Result.success()
        }

        val coleccion = FirebaseFirestore.getInstance()
            .collection("agendas")
            .document(agendaId)
            .collection("operaciones")

        val pendientes = dao.getPendientes()
        android.util.Log.d("SyncWorker", "Pendientes a subir: ${pendientes.size}")

        return try {
            pendientes.forEach { op ->
                android.util.Log.d("SyncWorker", "Subiendo: ${op.id}")
                coleccion.document(op.id).set(op.toFirestoreMap()).await()
                dao.marcarSynced(op.id)
                android.util.Log.d("SyncWorker", "Subido y marcado: ${op.id}")
            }

            val ultimaSync = dao.getTimestampUltimaSync() ?: 0L
            coleccion
                .whereGreaterThan("timestamp", ultimaSync)
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toEntity() }
                .forEach { dao.insertar(it) }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Error: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "sync_operaciones"

        fun encolar(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}