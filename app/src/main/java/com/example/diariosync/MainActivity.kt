package com.example.diariosync

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.diariosync.data.repository.OperacionRepository
import com.example.diariosync.databinding.ActivityMainBinding
import com.example.diariosync.ui.agenda.AgendaFragment
import com.example.diariosync.ui.lista.ListaFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}