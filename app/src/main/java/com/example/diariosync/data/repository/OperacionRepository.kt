package com.example.diariosync.data.repository

import android.content.Context
import com.example.diariosync.data.local.AppDatabase
import com.example.diariosync.data.local.OperacionEntity
import com.example.diariosync.data.mapper.OperacionMapper.toDomain
import com.example.diariosync.data.mapper.OperacionMapper.toEntity
import com.example.diariosync.data.mapper.OperacionMapper.toFirestoreMap
import com.example.diariosync.domain.model.Operacion
import com.example.diariosync.domain.model.TipoOperacion
import com.example.diariosync.sync.SyncWorker
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class OperacionRepository(private val context: Context) {

    private val dao by lazy { AppDatabase.getInstance(context).operacionDao() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val prefs by lazy { context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE) }

    // ── Agenda ────────────────────────────────────────────────────────────────

    fun getAgendaId(): String? = prefs.getString("agenda_id", null)
    fun tieneAgenda(): Boolean = getAgendaId() != null

    private fun setAgendaId(id: String) =
        prefs.edit().putString("agenda_id", id.trim().uppercase()).apply()

    private fun clearAgendaId() =
        prefs.edit().remove("agenda_id").apply()

    private fun operacionesRef(): CollectionReference {
        val id = getAgendaId() ?: error("No hay agenda activa")
        return firestore.collection("agendas").document(id).collection("operaciones")
    }

    /**
     * Crea agenda nueva. Limpia Room antes de arrancar con pizarra en blanco.
     * Falla si el código ya existe en Firestore.
     */
    suspend fun crearAgenda(codigo: String, password: String, deviceId: String, correo: String): Result<Unit> = runCatching {
        val norm = codigo.trim().uppercase()
        require(norm.length >= 4) { "El código debe tener al menos 4 caracteres" }

        val docRef = firestore.collection("agendas").document(norm)
        check(!docRef.get().await().exists()) { "Ya existe una agenda con ese código" }

        docRef.set(mapOf(
            "creadaEn"     to com.google.firebase.Timestamp.now(),
            "password"     to password,
            "dispositivos" to listOf(deviceId),
            "miembros" to listOf(mapOf("correo" to correo, "recibeCorreos" to true))
        )).await()

        dao.borrarTodo()
        setAgendaId(norm)
    }

    /**
     * Une el dispositivo a agenda existente. Limpia Room y deja que
     * iniciarEscuchaRemota() repopule con los datos de la agenda.
     * Falla si el código no existe.
     */
    suspend fun unirseAAgenda(codigo: String, password: String, deviceId: String, correo: String): Result<Unit> = runCatching {
        val norm = codigo.trim().uppercase()
        val docRef = firestore.collection("agendas").document(norm)
        val snap = docRef.get().await()

        check(snap.exists()) { "No existe ninguna agenda con ese código" }

        // ── VALIDACIÓN DE SEGURIDAD ──
        // Obtenemos la contraseña guardada. Si es null (sala vieja), asumimos "" (sin clave)
        val passGuardada = snap.getString("password") ?: ""

        // Validamos solo si la sala tiene una contraseña establecida
        if (passGuardada.isNotEmpty() && passGuardada != password) {
            throw Exception("Contraseña incorrecta") // Esto detiene el proceso y retorna un Failure
        }
        // ─────────────────────────────

        val dispositivos = snap.get("dispositivos") as? List<*> ?: emptyList<String>()
        if (!dispositivos.contains(deviceId)) {
            docRef.update("dispositivos", FieldValue.arrayUnion(deviceId)).await()
        }

        // Agregar correo si no está ya en la lista
        val miembros = snap.get("miembros") as? List<Map<String, Any>> ?: emptyList()
        val yaEsta = miembros.any { it["correo"] == correo }
        if (!yaEsta) {
            docRef.update("miembros", FieldValue.arrayUnion(mapOf("correo" to correo, "recibeCorreos" to true))).await()
        }

        dao.borrarTodo()
        setAgendaId(norm)
    }

    /**
     * Sale de la agenda actual. Limpia Room y prefs.
     * Si no quedan dispositivos en la sala, borra la agenda de Firestore.
     */
    suspend fun salirDeAgenda(deviceId: String) {
        val codigo = getAgendaId() ?: return
        try {
            val docRef = firestore.collection("agendas").document(codigo)
            docRef.update("dispositivos", FieldValue.arrayRemove(deviceId)).await()

            val snap = docRef.get().await()
            val restantes = snap.get("dispositivos") as? List<*> ?: emptyList<String>()
            if (restantes.isEmpty()) {
                // Borrar subcolección y documento
                val ops = docRef.collection("operaciones").get().await()
                val batch = firestore.batch()
                ops.documents.forEach { batch.delete(it.reference) }
                batch.delete(docRef)
                batch.commit().await()
            }
        } catch (_: Exception) { }

        dao.borrarTodo()
        clearAgendaId()
    }

    suspend fun getCorreosMiembros(): List<String> {
        val codigo = getAgendaId() ?: return emptyList()
        return try {
            val snap = firestore.collection("agendas").document(codigo).get().await()
            @Suppress("UNCHECKED_CAST")
            val miembros = snap.get("miembros") as? List<Map<String, Any>> ?: emptyList()
            miembros.filter { it["recibeCorreos"] == true }
                .mapNotNull { it["correo"] as? String }
        } catch (e: Exception) { emptyList() }
    }

    // ── Flows locales (Room) ──────────────────────────────────────────────────

    val operaciones: Flow<List<Operacion>> =
        dao.observarActivas().map { it.map { e -> e.toDomain() } }

    val historial: Flow<List<Operacion>> =
        dao.observarHistorial().map { it.map { e -> e.toDomain() } }

    // ── Escucha remota ────────────────────────────────────────────────────────

    fun iniciarEscuchaRemota() {
        if (!tieneAgenda()) return

        operacionesRef().addSnapshotListener { snapshots, e ->
            if (e != null) return@addSnapshotListener

            // --- ESTE ES EL FILTRO CLAVE ---
            // Si los datos vienen del caché local (hasPendingWrites), NO los procesamos.
            // Solo queremos lo que el servidor confirmó.
            if (snapshots != null && snapshots.metadata.hasPendingWrites()) {
                return@addSnapshotListener
            }

            snapshots?.documentChanges?.forEach { dc ->
                val entity = dc.document.data.toEntity()
                CoroutineScope(Dispatchers.IO).launch {
                    when (dc.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> dao.insertar(entity)
                        DocumentChange.Type.REMOVED  -> dao.eliminarPorId(dc.document.id)
                    }
                }
            }
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    suspend fun registrar(
        tipo: TipoOperacion,
        producto: String,
        cantidad: Double,
        precio: Double,
        autor: String
    ) {
        val hoyId = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        val entity = OperacionEntity(
            tipo      = tipo.name,
            producto  = producto,
            cantidad  = cantidad,
            precio    = precio,
            deviceId  = autor,
            jornadaId = hoyId,
            activa    = true,
            synced    = false // Nace en amarillo
        )

        // 1. Guardar solo en Room
        dao.insertar(entity)

        // operacionesRef().document(entity.id).set(entity.toFirestoreMap())
        // ^^^ Esta línea hacía que Firebase "engañara" al Listener.

        // 3. Encolar el Worker (Él hará el .set() cuando haya red de verdad)
        SyncWorker.encolar(context)
    }

    suspend fun eliminar(operacion: Operacion) {
        dao.eliminarPorId(operacion.id)
        operacionesRef().document(operacion.id).delete()
    }

    suspend fun actualizar(operacion: Operacion) {
        val entity = operacion.toEntity()
        dao.actualizar(entity)
        operacionesRef().document(operacion.id).set(entity.toFirestoreMap())
        SyncWorker.encolar(context)
    }

    suspend fun cerrarCaja() {
        dao.cerrarJornadaLocal()
        val activas = operacionesRef().whereEqualTo("activa", true).get().await()
        val batch = firestore.batch()
        activas.documents.forEach { batch.update(it.reference, "activa", false) }
        batch.commit().await()
    }

    suspend fun getOperacionesActuales(): List<Operacion> {
        return dao.getActivas().map { it.toDomain() }
    }

    suspend fun vaciarHistorialRemoto() {
        dao.borrarHistorialLocal()
        val inactivas = operacionesRef().whereEqualTo("activa", false).get().await()
        val batch = firestore.batch()
        inactivas.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    suspend fun sincronizarManual(): ResultadoSync {
        val pendientes = dao.getPendientes()

        if (pendientes.isEmpty()) return ResultadoSync.SIN_PENDIENTES

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val hayRed = connectivityManager.activeNetwork != null

        if (!hayRed) return ResultadoSync.SIN_RED_CON_PENDIENTES

        return try {
            val coleccion = operacionesRef()
            val resultado = withTimeoutOrNull(5000L) {
                pendientes.forEach { op ->
                    coleccion.document(op.id).set(op.toFirestoreMap()).await()
                    dao.marcarSynced(op.id)
                }
                ResultadoSync.TODO_AL_DIA
            }
            resultado ?: ResultadoSync.SIN_RED_CON_PENDIENTES
        } catch (e: Exception) {
            ResultadoSync.SIN_RED_CON_PENDIENTES
        }
    }

    enum class ResultadoSync {
        SIN_RED_CON_PENDIENTES, SIN_PENDIENTES, TODO_AL_DIA
    }
}