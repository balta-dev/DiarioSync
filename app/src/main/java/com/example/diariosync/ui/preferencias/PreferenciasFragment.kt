package com.example.diariosync.ui.preferencias

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.diariosync.R
import com.example.diariosync.databinding.FragmentPreferenciasBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.jvm.java

class PreferenciasFragment : Fragment() {

    private var _binding: FragmentPreferenciasBinding? = null
    private val binding get() = _binding!!

    // Días seleccionados para cierre repetido (0=Lun, 6=Dom)
    private val diasSeleccionados = mutableSetOf<Int>()
    private var horaRepetido = 0
    private var minutoRepetido = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreferenciasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("device_prefs", Context.MODE_PRIVATE)

        // ── Perfil ────────────────────────────────────────────────────────────
        binding.etNombre.setText(prefs.getString("user_name", ""))
        binding.etCorreo.setText(prefs.getString("user_email", ""))

        binding.btnGuardarPerfil.setOnClickListener {
            val nombre = binding.etNombre.text.toString().trim()
            val correo = binding.etCorreo.text.toString().trim()
            var valido = true

            if (nombre.isEmpty()) { binding.tilNombre.error = "Ingresá un nombre"; valido = false }
            else binding.tilNombre.error = null

            if (correo.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                binding.tilCorreo.error = "Ingresá un correo válido"; valido = false
            } else binding.tilCorreo.error = null

            if (valido) {
                prefs.edit { putString("user_name", nombre); putString("user_email", correo) }
                // Si borró el correo, desactivar recibir correos
                if (correo.isEmpty()) {
                    prefs.edit { putBoolean("recibir_correos", false) }
                    binding.switchRecibirCorreos.isChecked = false
                }
                Snackbar.make(binding.root, "Perfil actualizado", Snackbar.LENGTH_SHORT).show()
            }
        }

        // ── Notificaciones ────────────────────────────────────────────────────
        binding.switchRecibirCorreos.isChecked = prefs.getBoolean("recibir_correos", true)
        val correoActual = prefs.getString("user_email", "") ?: ""
        binding.switchRecibirCorreos.isEnabled = correoActual.isNotEmpty()

        binding.etCorreo.addTextChangedListener {
            val tieneCorreo = it.toString().trim().isNotEmpty()
            binding.switchRecibirCorreos.isEnabled = tieneCorreo
            if (!tieneCorreo) binding.switchRecibirCorreos.isChecked = false
        }

        binding.switchRecibirCorreos.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("recibir_correos", isChecked) }
            val correo = prefs.getString("user_email", "") ?: return@setOnCheckedChangeListener
            lifecycleScope.launch {
                try {
                    val codigo = prefs.getString("agenda_id", null) ?: return@launch
                    val docRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("agendas").document(codigo)
                    val snap = docRef.get().await()
                    @Suppress("UNCHECKED_CAST")
                    val miembros = snap.get("miembros") as? List<Map<String, Any>> ?: return@launch
                    val nuevos = miembros.map {
                        if (it["correo"] == correo) mapOf("correo" to correo, "recibeCorreos" to isChecked)
                        else it
                    }
                    docRef.update("miembros", nuevos).await()
                } catch (e: Exception) {
                    android.util.Log.e("Prefs", "Error actualizando recibeCorreos: ${e.message}")
                }
            }
        }

        // ── Cierre programado ─────────────────────────────────────────────────
        val esModoRepetido = prefs.getBoolean("cierre_modo_repetido", false)
        if (esModoRepetido) {
            binding.toggleTipoCierre.check(R.id.btnCierreRepetido)
            binding.panelCierreUnico.visibility = View.GONE
            binding.panelCierreRepetido.visibility = View.VISIBLE
        } else {
            binding.toggleTipoCierre.check(R.id.btnCierreUnico)
        }

        // Restaurar fecha única guardada
        val fechaGuardada = prefs.getLong("cierre_unico_timestamp", 0L)
        if (fechaGuardada > 0) {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.tvFechaUnica.text = sdf.format(fechaGuardada)
        }

        // Restaurar hora repetida guardada
        horaRepetido = prefs.getInt("cierre_hora", 0)
        minutoRepetido = prefs.getInt("cierre_minuto", 0)
        binding.tvHoraRepetido.text = String.format("%02d:%02d", horaRepetido, minutoRepetido)

        // Restaurar días seleccionados
        val diasGuardados = prefs.getStringSet("cierre_dias", emptySet()) ?: emptySet()
        diasSeleccionados.addAll(diasGuardados.map { it.toInt() })
        actualizarVistasDias()

        // Toggle entre único y repetido
        binding.toggleTipoCierre.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val esRepetido = checkedId == R.id.btnCierreRepetido
            binding.panelCierreUnico.visibility = if (esRepetido) View.GONE else View.VISIBLE
            binding.panelCierreRepetido.visibility = if (esRepetido) View.VISIBLE else View.GONE
            prefs.edit { putBoolean("cierre_modo_repetido", esRepetido) }
        }

        // Elegir fecha única
        binding.btnElegirFecha.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                TimePickerDialog(requireContext(), { _, h, min ->
                    cal.set(y, m, d, h, min, 0)
                    cal.set(Calendar.SECOND, 0)
                    val ts = cal.timeInMillis
                    prefs.edit { putLong("cierre_unico_timestamp", ts) }
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    binding.tvFechaUnica.text = sdf.format(ts)
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Elegir hora repetida
        binding.btnElegirHora.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, min ->
                horaRepetido = h
                minutoRepetido = min
                prefs.edit { putInt("cierre_hora", h); putInt("cierre_minuto", min) }
                binding.tvHoraRepetido.text = String.format("%02d:%02d", h, min)
            }, horaRepetido, minutoRepetido, true).show()
        }

        // Click en días
        val vistaDias = listOf(binding.diaLun, binding.diaMar, binding.diaMie,
            binding.diaJue, binding.diaVie, binding.diaSab, binding.diaDom)
        vistaDias.forEachIndexed { i, tv ->
            tv.setOnClickListener {
                if (diasSeleccionados.contains(i)) diasSeleccionados.remove(i)
                else diasSeleccionados.add(i)
                prefs.edit { putStringSet("cierre_dias", diasSeleccionados.map { it.toString() }.toSet()) }
                actualizarVistasDias()
            }
        }

        // Activar/desactivar cierre programado
        val cierreActivo = prefs.getBoolean("cierre_activo", false)
        actualizarBotonCierre(cierreActivo)

        binding.btnActivarCierre.setOnClickListener {
            val activo = prefs.getBoolean("cierre_activo", false)
            if (activo) {
                cancelarAlarmaCierre()
                prefs.edit { putBoolean("cierre_activo", false) }
                actualizarBotonCierre(false)
                Snackbar.make(binding.root, "Cierre programado desactivado", Snackbar.LENGTH_SHORT).show()
            } else {
                val ok = programarAlarmaCierre(prefs)
                if (ok) {
                    prefs.edit { putBoolean("cierre_activo", true) }
                    actualizarBotonCierre(true)
                    Snackbar.make(binding.root, "Cierre programado activado", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        // ── Seguridad de Agenda ───────────────────────────────────────────────
        lifecycleScope.launch {
            try {
                val agendaId = prefs.getString("agenda_id", null) ?: return@launch
                val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("agendas").document(agendaId).get().await()
                val passActual = snap.getString("password") ?: ""
                binding.etPasswordSala.setText(passActual)
            } catch (e: Exception) {
                android.util.Log.e("Preferencias", "Error cargando password: ${e.message}")
            }
        }

        // ── Seguridad de Agenda ───────────────────────────────────────────────
        binding.btnGuardarPassword.setOnClickListener {
            val nuevaPass = binding.etPasswordSala.text.toString().trim()
            val agendaId = prefs.getString("agenda_id", null)
            val cambiadoPor = prefs.getString("user_name", "Un miembro") ?: "Un miembro"

            if (agendaId == null) return@setOnClickListener

            binding.btnGuardarPassword.isEnabled = false
            Snackbar.make(binding.root, "Actualizando contraseña...", Snackbar.LENGTH_SHORT).show()

            lifecycleScope.launch {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val docRef = db.collection("agendas").document(agendaId)

                    // 1. Actualizar contraseña en Firestore
                    docRef.update("password", nuevaPass).await()
                    Snackbar.make(binding.root, "Contraseña actualizada. Intentando avisar...", Snackbar.LENGTH_SHORT).show()

                    // 2. Obtener lista de miembros actual
                    val snap = docRef.get().await()
                    @Suppress("UNCHECKED_CAST")
                    val miembros = snap.get("miembros") as? List<Map<String, Any>> ?: emptyList()

                    // 3. Filtrar los que quieren recibir correos
                    val correosDestino = miembros.filter { it["recibeCorreos"] == true }
                        .mapNotNull { it["correo"] as? String }

                    android.util.Log.d("EmailJS_Debug", "Miembros encontrados: $miembros")
                    android.util.Log.d("EmailJS_Debug", "Correos a enviar: $correosDestino")

                    if (correosDestino.isEmpty()) {
                        android.util.Log.e("EmailJS_Error", "No se encontró ningún miembro con recibeCorreos=true o correos vacíos")
                        Snackbar.make(binding.root, "Nadie a quien avisar.", Snackbar.LENGTH_SHORT).show()
                    }

                    // 4. Mandar mails si hay a quien mandarle
                    if (correosDestino.isNotEmpty()) {
                        correosDestino.forEach { correo ->
                            mandarMailPasswordCambiada(correo, agendaId, nuevaPass, cambiadoPor)
                        }
                    }

                    binding.etPasswordSala.text?.clear()
                    if (correosDestino.isNotEmpty()) { Snackbar.make(binding.root, "Mail enviado/s correctamente", Snackbar.LENGTH_SHORT).show() }

                } catch (e: Exception) {
                    android.util.Log.e("Preferencias", "Error al cambiar password: ${e.message}")
                    Snackbar.make(binding.root, "Error al actualizar contraseña", Snackbar.LENGTH_SHORT).show()
                } finally {
                    binding.btnGuardarPassword.isEnabled = true
                }
            }
        }

        // ── Apariencia ────────────────────────────────────────────────────────
        binding.switchModoOscuro.isChecked = prefs.getBoolean("modo_oscuro", true)
        binding.switchModoOscuro.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("modo_oscuro", isChecked) }
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            activity?.recreate()
        }

        binding.btnVolver.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun actualizarVistasDias() {
        val vistas = listOf(binding.diaLun, binding.diaMar, binding.diaMie,
            binding.diaJue, binding.diaVie, binding.diaSab, binding.diaDom)
        vistas.forEachIndexed { i, tv ->
            val sel = diasSeleccionados.contains(i)
            tv.setBackgroundResource(if (sel) R.drawable.bg_badge_venta else R.drawable.bg_card_surface2)
            tv.setTextColor(ContextCompat.getColor(requireContext(),
                if (sel) R.color.ds_green else R.color.ds_text_secondary))
        }
    }

    private fun actualizarBotonCierre(activo: Boolean) {
        binding.btnActivarCierre.text = if (activo) "Desactivar cierre programado" else "Activar cierre programado"
        binding.btnActivarCierre.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(), if (activo) R.color.ds_red else R.color.ds_accent
        )
    }

    private fun programarAlarmaCierre(prefs: android.content.SharedPreferences): Boolean {
        val esRepetido = prefs.getBoolean("cierre_modo_repetido", false)
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                return false
            }
        }

        if (esRepetido) {
            if (diasSeleccionados.isEmpty()) {
                Snackbar.make(binding.root, "Elegí al menos un día", Snackbar.LENGTH_SHORT).show()
                return false
            }
            // Programar una alarma por cada día seleccionado
            diasSeleccionados.forEach { dia ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, dia + 2) // Calendar: 1=Dom, 2=Lun...
                    set(Calendar.HOUR_OF_DAY, horaRepetido)
                    set(Calendar.MINUTE, minutoRepetido)
                    set(Calendar.SECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1)
                }
                val intent = Intent(requireContext(), CierreAlarmReceiver::class.java)
                val pi = PendingIntent.getBroadcast(requireContext(), dia,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                    AlarmManager.INTERVAL_DAY * 7, pi)
            }
        } else {
            val ts = prefs.getLong("cierre_unico_timestamp", 0L)
            if (ts <= System.currentTimeMillis()) {
                Snackbar.make(binding.root, "La fecha tiene que ser futura", Snackbar.LENGTH_SHORT).show()
                return false
            }
            val intent = Intent(requireContext(), CierreAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(requireContext(), 999,
                intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi)
        }
        return true
    }

    private fun cancelarAlarmaCierre() {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancelar alarmas de días repetidos
        (0..6).forEach { dia ->
            val intent = Intent(requireContext(), CierreAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(requireContext(), dia,
                intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.cancel(pi)
        }
        // Cancelar alarma única
        val intent = Intent(requireContext(), CierreAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(requireContext(), 999,
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pi)
    }

    private suspend fun mandarMailPasswordCambiada(
        toEmail: String,
        agendaId: String,
        nuevaPassword: String,
        cambiadoPor: String
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // 1. Armamos el JSON según el formato exacto de EmailJS
            val json = org.json.JSONObject().apply {
                put("service_id", com.example.diariosync.BuildConfig.EMAILJS_SERVICE_ID)
                put("template_id", com.example.diariosync.BuildConfig.EMAILJS_TEMPLATE_ID_PASSWORDCAMBIO)
                put("user_id", com.example.diariosync.BuildConfig.EMAILJS_USER_ID)
                put("accessToken", com.example.diariosync.BuildConfig.EMAILJS_PRIVATE_KEY)
                put("template_params", org.json.JSONObject().apply {
                    put("to_email", toEmail)
                    put("agenda_id", agendaId)
                    put("nueva_password", if (nuevaPassword.isEmpty()) "Sin contraseña" else nuevaPassword)
                    put("cambiado_por", cambiadoPor)
                })
            }

            // 2. Usamos el formato de RequestBody que sabes que funciona
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = okhttp3.Request.Builder()
                .url("https://api.emailjs.com/api/v1.0/email/send")
                .post(body)
                .build()

            // 3. Ejecución
            val client = okhttp3.OkHttpClient()
            val response = client.newCall(request).execute()

            val responseBody = response.body?.string()
            android.util.Log.d("EmailJS", "Response (${response.code}): $responseBody")

            if (!response.isSuccessful) {
                android.util.Log.e("EmailJS", "Fallo al enviar: $responseBody")
                Snackbar.make(binding.root, "Fallo al enviar", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("EmailJS", "Error al mandar mail de password: ${e.message}")
            Snackbar.make(binding.root, "Ocurrió un problema", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}