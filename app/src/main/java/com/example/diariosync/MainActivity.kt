package com.example.diariosync

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.diariosync.data.repository.OperacionRepository
import com.example.diariosync.databinding.ActivityMainBinding
import com.example.diariosync.ui.agenda.AgendaFragment
import com.example.diariosync.ui.lista.ListaFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: OperacionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Si esto tarda mucho, el usuario ve la pantalla blanca/negra 1 segundo
        repository = OperacionRepository(this)

        if (savedInstanceState == null) {
            // Ejecutamos la lógica de navegación después de que la UI se asiente
            binding.root.post {
                iniciarFlujo()
            }
        }

        //mostrarDialogoUpdate("1.2","null")
        revisarActualizaciones()
    }

    private fun revisarActualizaciones() {
        lifecycleScope.launch {
            val versionActual = BuildConfig.VERSION_NAME
            val (hayUpdate, url, tagName) = UpdateChecker.hayNuevaVersion(versionActual)

            if (hayUpdate && tagName != null && url != null) {
                mostrarDialogoUpdate(tagName, url)
            }
        }
    }

    private fun mostrarDialogoUpdate(tagName: String, url: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_actualizar, null)
        dialogView.findViewById<TextView>(R.id.tvCuerpoUpdate).text =
            "Está disponible la nueva actualización v$tagName para DiarioSync. ¿Querés descargarla?"

        com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this, R.style.ThemeOverlay_DiarioSync_MaterialAlertDialog
        )
            .setView(dialogView)
            .setPositiveButton("Descargar") { _, _ ->
                // En lugar de abrir el navegador, bajamos el APK
                iniciarDescarga(url, "DiarioSync_v$tagName.apk")
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }
    /**
     * Flujo de entrada:
     *   1. ¿Tiene nombre? → no  → Dialog bienvenida → continúa
     *   2. ¿Tiene agenda? → no  → AgendaFragment
     *                     → sí  → ListaFragment
     */
    private fun iniciarFlujo() {
        // 1. Cargamos el fondo primero (Lista o Agenda)
        verificarAgenda()

        // 2. Si es la primera vez (no hay nombre), disparamos el diálogo encima
        if (getNombreUsuario() == null) {
            // Usamos post para asegurar que el fragment ya se esté transicionando
            binding.root.post {
                mostrarDialogoBienvenida()
            }
        }
    }

    private fun verificarAgenda() {
        if (repository.tieneAgenda()) {
            navegarA(ListaFragment())
        } else {
            navegarA(AgendaFragment())
        }
    }

    private fun navegarA(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun getNombreUsuario(): String? =
        getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
            .getString("user_name", null)

    private fun mostrarDialogoBienvenida() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_bienvenida, null)
        val etNombre = dialogView.findViewById<TextInputEditText>(R.id.etNombreUsuario)
        val tilNombre = dialogView.findViewById<TextInputLayout>(R.id.tilNombre)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this, R.style.ThemeOverlay_DiarioSync_MaterialAlertDialog
        )
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Comenzar", null)
            .create()

        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(R.drawable.bg_dialog_redondeado)
            window.setDimAmount(0.8f) // Bien oscuro para que resalte el diálogo
        }

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            if (nombre.isNotEmpty()) {
                getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
                    .edit { putString("user_name", nombre) }
                dialog.dismiss()
            } else {
                tilNombre.error = "Por favor, ingresá un nombre"
            }
        }
    }

    private fun iniciarDescarga(url: String, fileName: String) {
        val destinationFile = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)

        // Si ya existe uno viejo con el mismo nombre, lo borramos
        if (destinationFile.exists()) destinationFile.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Descargando actualización")
            .setDescription(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destinationFile))

        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        // Escuchamos cuando termine la descarga
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    instalarApk(destinationFile)
                    unregisterReceiver(this)
                }
            }
        }

        // IMPORTANTE: En Android 14+ se necesita RECEIVER_EXPORTED para el DownloadManager
        registerReceiver(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) RECEIVER_EXPORTED else 0
        )
    }

    private fun instalarApk(file: File) {
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.provider", // Usa tu applicationId dinámico
            file
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            startActivity(installIntent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Error al abrir el instalador", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}