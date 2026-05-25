package com.example.diariosync.util

import android.content.Context
import com.example.diariosync.BuildConfig
import com.example.diariosync.data.repository.OperacionRepository
import com.example.diariosync.domain.model.Operacion
import com.example.diariosync.domain.model.TipoOperacion
import com.example.diariosync.export.ExcelExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CierreManager {

    suspend fun ejecutar(context: Context, operaciones: List<Operacion>) {
        val repository = OperacionRepository(context)

        // 1. Cerrar caja
        repository.cerrarCaja()

        // 2. Generar Excel
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val nombre = "${fecha}_cierre_caja"
        val listaCronologica = operaciones.sortedBy { it.timestamp }
        val bytes = ExcelExporter.exportarComoBytes(listaCronologica)
        android.util.Log.d("CierreManager", "Excel generado: ${bytes.size} bytes")

        // 3. Subir a Dropbox
        val excelUrl = subirADropbox(context, bytes, nombre, repository.getAgendaId() ?: "sin_sala")
        android.util.Log.d("CierreManager", "URL obtenida: $excelUrl")

        // 4. Mandar mails
        val correosDestino = repository.getCorreosMiembros()
        if (excelUrl != null && correosDestino.isNotEmpty()) {
            val nf = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
            val ingresos = operaciones.filter { it.tipo == TipoOperacion.VENTA }.sumOf { it.total }
            val egresos = operaciones.filter { it.tipo == TipoOperacion.COMPRA }.sumOf { it.total }
            val agendaId = repository.getAgendaId() ?: "—"
            val cerradoPor = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
                .getString("user_name", "Desconocido") ?: "Desconocido"
            val fechaLegible = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())

            correosDestino.forEach { correo ->
                mandarMailCierre(
                    toEmail = correo,
                    agendaId = agendaId,
                    fecha = fechaLegible,
                    cerradoPor = cerradoPor,
                    totalIngresos = nf.format(ingresos),
                    totalEgresos = nf.format(egresos),
                    cantOps = operaciones.size.toString(),
                    excelUrl = excelUrl
                )
            }
        }
    }

    private suspend fun conseguirAccessTokenValido(context: Context): String? =
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("dropbox", Context.MODE_PRIVATE)
                val expiresAt = prefs.getLong("expires_at", 0L)
                val cachedToken = prefs.getString("access_token", null)

                if (cachedToken != null && System.currentTimeMillis() < expiresAt - 60_000) {
                    return@withContext cachedToken
                }

                val request = Request.Builder()
                    .url("https://api.dropbox.com/oauth2/token")
                    .post(
                        FormBody.Builder()
                            .add("grant_type", "refresh_token")
                            .add("refresh_token", BuildConfig.DROPBOX_REFRESH_TOKEN)
                            .add("client_id", BuildConfig.DROPBOX_APP_KEY)
                            .add("client_secret", BuildConfig.DROPBOX_APP_SECRET)
                            .build()
                    )
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                android.util.Log.d("CierreManager", "Refresh response (${response.code}): $body")
                val json = JSONObject(body)

                val newToken = json.optString("access_token").takeIf { it.isNotEmpty() } ?: return@withContext null
                val expiresIn = json.optLong("expires_in", 14400L)

                prefs.edit()
                    .putString("access_token", newToken)
                    .putLong("expires_at", System.currentTimeMillis() + expiresIn * 1000)
                    .apply()

                newToken
            } catch (e: Exception) {
                android.util.Log.e("CierreManager", "Error al refrescar token: ${e.message}")
                null
            }
        }

    private suspend fun subirADropbox(context: Context, bytes: ByteArray, nombre: String, agendaId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val path = "/cierres/$agendaId/$nombre.xlsx"
                val token = conseguirAccessTokenValido(context) ?: return@withContext null

                val uploadRequest = Request.Builder()
                    .url("https://content.dropboxapi.com/2/files/upload")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Dropbox-API-Arg", """{"path":"$path","mode":"overwrite","autorename":false}""")
                    .addHeader("Content-Type", "application/octet-stream")
                    .post(bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()

                val uploadResponse = OkHttpClient().newCall(uploadRequest).execute()
                if (!uploadResponse.isSuccessful) {
                    android.util.Log.e("CierreManager", "Upload failed: ${uploadResponse.body?.string()}")
                    return@withContext null
                }

                val shareJson = JSONObject().apply {
                    put("path", path)
                    put("settings", JSONObject().apply { put("requested_visibility", "public") })
                }
                val shareRequest = Request.Builder()
                    .url("https://api.dropboxapi.com/2/sharing/create_shared_link_with_settings")
                    .addHeader("Authorization", "Bearer $token")
                    .post(shareJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val shareResponse = OkHttpClient().newCall(shareRequest).execute()
                val shareBody = shareResponse.body?.string() ?: return@withContext null
                android.util.Log.d("CierreManager", "Share response (${shareResponse.code}): $shareBody")

                val json = try { JSONObject(shareBody) } catch (e: Exception) { return@withContext null }

                val url = if (shareResponse.isSuccessful) {
                    json.getString("url")
                } else if (shareResponse.code == 409) {
                    val listJson = JSONObject().apply { put("path", path) }
                    val listBody = OkHttpClient().newCall(
                        Request.Builder()
                            .url("https://api.dropboxapi.com/2/sharing/list_shared_links")
                            .addHeader("Authorization", "Bearer $token")
                            .post(listJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                            .build()
                    ).execute().body?.string()

                    val existingUrl = JSONObject(listBody ?: "{}")
                        .optJSONArray("links")?.optJSONObject(0)?.optString("url")

                    if (existingUrl != null) {
                        OkHttpClient().newCall(
                            Request.Builder()
                                .url("https://api.dropboxapi.com/2/sharing/revoke_shared_link")
                                .addHeader("Authorization", "Bearer $token")
                                .post(JSONObject().apply { put("url", existingUrl) }.toString()
                                    .toRequestBody("application/json".toMediaTypeOrNull()))
                                .build()
                        ).execute()
                    }

                    val retryResponse = OkHttpClient().newCall(
                        Request.Builder()
                            .url("https://api.dropboxapi.com/2/sharing/create_shared_link_with_settings")
                            .addHeader("Authorization", "Bearer $token")
                            .post(shareJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                            .build()
                    ).execute()
                    JSONObject(retryResponse.body?.string() ?: "{}").optString("url")
                } else {
                    android.util.Log.e("CierreManager", "Share error: $shareBody")
                    null
                } ?: return@withContext null

                url.replace("dl=0", "dl=1")
            } catch (e: Exception) {
                android.util.Log.e("CierreManager", "Error: ${e.message}")
                null
            }
        }

    private suspend fun mandarMailCierre(
        toEmail: String, agendaId: String, fecha: String, cerradoPor: String,
        totalIngresos: String, totalEgresos: String, cantOps: String, excelUrl: String
    ) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("service_id", BuildConfig.EMAILJS_SERVICE_ID)
                put("template_id", BuildConfig.EMAILJS_TEMPLATE_ID_CIERRECAJA)
                put("user_id", BuildConfig.EMAILJS_USER_ID)
                put("accessToken", BuildConfig.EMAILJS_PRIVATE_KEY)
                put("template_params", JSONObject().apply {
                    put("to_email", toEmail)
                    put("agenda_id", agendaId)
                    put("fecha", fecha)
                    put("cerrado_por", cerradoPor)
                    put("total_ingresos", totalIngresos)
                    put("total_egresos", totalEgresos)
                    put("cant_operaciones", cantOps)
                    put("excel_url", excelUrl)
                })
            }

            val response = OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.emailjs.com/api/v1.0/email/send")
                    .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
            ).execute()
            android.util.Log.d("CierreManager", "Mail response (${response.code}): ${response.body?.string()}")
        } catch (e: Exception) {
            android.util.Log.e("CierreManager", "Error al mandar mail: ${e.message}")
        }
    }
}