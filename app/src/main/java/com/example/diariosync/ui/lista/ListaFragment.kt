package com.example.diariosync.ui.lista

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.diariosync.BuildConfig
import com.example.diariosync.R
import com.example.diariosync.data.repository.OperacionRepository
import com.example.diariosync.databinding.FragmentListaBinding
import com.example.diariosync.domain.model.Operacion
import com.example.diariosync.domain.model.TipoOperacion
import com.example.diariosync.export.ExcelExporter
import com.example.diariosync.ui.agenda.AgendaFragment
import com.example.diariosync.ui.nueva.NuevaOperacionFragment
import com.example.diariosync.ui.preferencias.PreferenciasFragment
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
import androidx.core.content.edit
import com.example.diariosync.util.CierreManager

class ListaFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ListaViewModel by viewModels()
    private val nf = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
    private val adapter = OperacionAdapter { operacion ->
        mostrarDialogoEdicion(operacion)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Mostrar código de sala en el footer
        binding.txtCodigoSala.text = viewModel.getAgendaId() ?: "—"

        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                val resultado = viewModel.sincronizarManual()
                binding.swipeRefresh.isRefreshing = false
                when (resultado) {
                    OperacionRepository.ResultadoSync.SIN_RED_CON_PENDIENTES ->
                        Snackbar.make(binding.root, "Sin conexión", Snackbar.LENGTH_SHORT).show()
                    OperacionRepository.ResultadoSync.TODO_AL_DIA ->
                        Snackbar.make(binding.root, "Sincronizado", Snackbar.LENGTH_SHORT).show()
                    OperacionRepository.ResultadoSync.SIN_PENDIENTES -> { /* silencio */ }
                }
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewModel.operacionesVisibles.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista) {
                // Este callback se ejecuta DESPUÉS de que el adapter termina de difusionar
                if (viewModel.scrollArriba.value == true) {
                    binding.recyclerView.scrollToPosition(0)
                    viewModel.consumirScrollArriba()
                }
            }

            // Actualizar totales del summary strip
            val totalVentas = lista.filter { it.tipo.name == "VENTA" }.sumOf { it.total }
            val totalCompras = lista.filter { it.tipo.name == "COMPRA" }.sumOf { it.total }
            val nf = java.text.NumberFormat.getCurrencyInstance(Locale("es", "AR"))
            binding.txtTotalVentas.text = nf.format(totalVentas)
            binding.txtTotalCompras.text = nf.format(totalCompras)
        }

        binding.btnNuevaOperacion.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NuevaOperacionFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnExportar.setOnClickListener {
            viewModel.actualizarFiltros(null, ListaViewModel.OrdenarPor.RECIENTES)
            val lista = viewModel.operaciones.value
            if (lista.isNullOrEmpty()) {
                Snackbar.make(binding.root, "No hay operaciones para exportar", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val listaCronologica = lista.sortedBy { it.timestamp }
                val uri = ExcelExporter.exportar(requireContext(), listaCronologica, "operaciones_actuales")
                ExcelExporter.compartir(requireContext(), uri)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error al exportar: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }

        binding.btnCerrarCaja.setOnClickListener {
            viewModel.actualizarFiltros(null, ListaViewModel.OrdenarPor.RECIENTES)
            val listaActual = viewModel.operaciones.value
            if (listaActual.isNullOrEmpty()) {
                Snackbar.make(binding.root, "Nada que cerrar", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Cálculos rápidos
            val ventas = listaActual.filter { it.tipo == TipoOperacion.VENTA }.sumOf { it.cantidad * it.precio }
            val compras = listaActual.filter { it.tipo == TipoOperacion.COMPRA }.sumOf { it.cantidad * it.precio }
            val cant = listaActual.size

            val dialogView = layoutInflater.inflate(R.layout.dialog_cerrar_caja, null)

            dialogView.findViewById<TextView>(R.id.tvCantOperaciones).text = "$cant OPERACIONES"
            dialogView.findViewById<TextView>(R.id.tvResumenVentas).text =  "Ingresos:  ${nf.format(ventas)}"
            dialogView.findViewById<TextView>(R.id.tvResumenCompras).text = "Egresos: ${nf.format(compras)}"

            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DiarioSync_MaterialAlertDialog)
                .setView(dialogView)
                .setPositiveButton("Cerrar Caja") { _, _ ->
                    ejecutarLogicaCierre(listaActual)
                }
                .setNegativeButton("Cancelar", null)
                .show()
            dialog.window?.setDimAmount(0.6f)
        }

        binding.btnHistorial.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HistorialFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.logoMark.setOnClickListener {
            mostrarDialogoCreditos()
        }

        binding.btnFiltrar.setOnClickListener {
            mostrarDialogoFiltros()
        }

        binding.btnPreferencias.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PreferenciasFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnSalirSala.setOnClickListener {
            // 1. Inflamos el layout personalizado
            val dialogView = layoutInflater.inflate(R.layout.dialog_salir_sala, null)

            // 2. Personalizamos el texto dinámicamente si es necesario
            val agendaId = viewModel.getAgendaId()
            dialogView.findViewById<TextView>(R.id.tvCuerpoSalir).text =
                "Vas a desconectarte de la agenda \"$agendaId\". Los datos locales se borrarán permanentemente de este dispositivo."

            // 3. Creamos el Material Dialog
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DiarioSync_MaterialAlertDialog)
                .setView(dialogView)
                .setPositiveButton("Salir y Borrar") { _, _ ->
                    viewModel.salirDeAgenda {
                        activity?.runOnUiThread {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, AgendaFragment())
                                .commit()
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
            dialog.window?.setDimAmount(0.8f)
        }

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            // ESTE ES EL TRUCO: Controlamos el dibujo del ítem
            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                // Definimos el tope (por ejemplo, el 20% del ancho del RecyclerView)
                val maxWidth = recyclerView.width * 0.2f

                // Limitamos el valor de dX (el desplazamiento horizontal)
                val limitedDX = when {
                    dX > maxWidth -> maxWidth  // Tope hacia la derecha
                    dX < -maxWidth -> -maxWidth // Tope hacia la izquierda
                    else -> dX
                }

                super.onChildDraw(c, recyclerView, viewHolder, limitedDX, dY, actionState, isCurrentlyActive)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val operacion = adapter.currentList[position]

                val dialogView = layoutInflater.inflate(R.layout.dialog_eliminar, null)
                val tvCuerpo = dialogView.findViewById<TextView>(R.id.tvCuerpoEliminar)
                tvCuerpo.text = "Vas a quitar '${operacion.producto}' de la lista."

                val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DiarioSync_MaterialAlertDialog)
                    .setView(dialogView)
                    .setPositiveButton("Eliminar") { _, _ ->
                        viewModel.eliminarOperacion(operacion)
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        // IMPORTANTE: Al cancelar, reseteamos la vista del ítem
                        adapter.notifyItemChanged(position)
                    }.setOnCancelListener {
                        adapter.notifyItemChanged(position)
                    }
                    .show()
                dialog.window?.setDimAmount(0.6f)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun mostrarDialogoEdicion(operacion: Operacion) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_editar_operacion, null)

        // Seteamos los datos de referencia en la mini card
        dialogView.findViewById<TextView>(R.id.tvRefProducto).text = operacion.producto
        dialogView.findViewById<TextView>(R.id.tvRefMonto).text = nf.format(operacion.cantidad * operacion.precio)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DiarioSync_MaterialAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Modificar") { _, _ ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, NuevaOperacionFragment.paraEditar(operacion))
                    .addToBackStack(null)
                    .commit()
            }
            .setNeutralButton("Cerrar", null)
            .show()

        dialog.window?.setDimAmount(0.6f)
    }

    //líneas comentadas para que no exporte automáticamente
    private fun ejecutarLogicaCierre(lista: List<Operacion>) {
        lifecycleScope.launch {
            try {
                Snackbar.make(binding.root, "Cerrando caja...", Snackbar.LENGTH_SHORT).show()
                CierreManager.ejecutar(requireContext(), lista)
                Snackbar.make(binding.root, "Caja cerrada y mails enviados", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error al cerrar caja: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarDialogoCreditos() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_credits, null)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DiarioSync_MaterialAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .create()

        // Configurar el click para ir a tu web
        dialogView.findViewById<LinearLayout>(R.id.btnVisitarWeb).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://balta-dev.github.io"))
            startActivity(intent)
            dialog.dismiss() // Cerramos el diálogo al navegar
        }

        dialog.window?.setDimAmount(0.6f)
        dialog.show()
    }

    private fun mostrarDialogoFiltros() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_filtros, null)
        val chipGroupTipo = dialogView.findViewById<ChipGroup>(R.id.chipGroupTipo)
        val chipGroupOrden = dialogView.findViewById<ChipGroup>(R.id.chipGroupOrden)

        // 1. RECUPERAR ESTADO ACTUAL (Para que el diálogo no sea "mentiroso")
        when (viewModel.getFiltroTipo()) {
            true -> chipGroupTipo.check(R.id.chipSoloVentas)
            false -> chipGroupTipo.check(R.id.chipSoloCompras)
            else -> chipGroupTipo.check(R.id.chipTodos)
        }

        when (viewModel.getOrdenUI()) {
            ListaViewModel.OrdenarPor.ANTIGUOS -> chipGroupOrden.check(R.id.chipAntiguos)
            ListaViewModel.OrdenarPor.MAYOR_MONTO -> chipGroupOrden.check(R.id.chipMayorMonto)
            ListaViewModel.OrdenarPor.MENOR_MONTO -> chipGroupOrden.check(R.id.chipMenorMonto)
            else -> chipGroupOrden.check(R.id.chipRecientes)
        }

        // Lógica en tiempo real
        val applyFilters = {
            val soloVentas = when (chipGroupTipo.checkedChipId) {
                R.id.chipSoloVentas -> true
                R.id.chipSoloCompras -> false
                else -> null
            }
            val ordenElegido = when (chipGroupOrden.checkedChipId) {
                R.id.chipAntiguos -> ListaViewModel.OrdenarPor.ANTIGUOS
                R.id.chipMayorMonto -> ListaViewModel.OrdenarPor.MAYOR_MONTO
                R.id.chipMenorMonto -> ListaViewModel.OrdenarPor.MENOR_MONTO
                else -> ListaViewModel.OrdenarPor.RECIENTES
            }
            viewModel.actualizarFiltros(soloVentas, ordenElegido)
        }

        // Listeners para que sea instantáneo
        chipGroupTipo.setOnCheckedStateChangeListener { _, _ -> applyFilters() }
        chipGroupOrden.setOnCheckedStateChangeListener { _, _ -> applyFilters() }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DiarioSync_MaterialAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Listo", null)
            .setNeutralButton("Quitar filtros") { _, _ ->
                // Reset completo al estado inicial
                viewModel.actualizarFiltros(null, ListaViewModel.OrdenarPor.RECIENTES)
            }
            .show()
        dialog.window?.setDimAmount(0.5f)
    }
}