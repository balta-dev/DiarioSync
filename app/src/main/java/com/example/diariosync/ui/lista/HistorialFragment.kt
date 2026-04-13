    package com.example.diariosync.ui.lista

    import android.app.AlertDialog
    import android.os.Bundle
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.TextView
    import androidx.fragment.app.Fragment
    import androidx.fragment.app.viewModels
    import androidx.recyclerview.widget.LinearLayoutManager
    import com.example.diariosync.R
    import com.example.diariosync.databinding.FragmentHistorialBinding
    import com.example.diariosync.domain.model.Operacion
    import com.example.diariosync.domain.model.TipoOperacion
    import com.example.diariosync.export.ExcelExporter
    import com.google.android.material.snackbar.Snackbar
    import java.text.NumberFormat
    import java.time.LocalDate
    import java.time.format.DateTimeFormatter
    import java.util.Locale

    class HistorialFragment : Fragment() {
        private var _binding: FragmentHistorialBinding? = null
        private val binding get() = _binding!!
        private val nf = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
        private val viewModel: ListaViewModel by viewModels()
        private lateinit var cajaAdapter: CajaHistorialAdapter

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View {
            _binding = FragmentHistorialBinding.inflate(inflater, container, false)
            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            cajaAdapter = CajaHistorialAdapter(
                onExportClick = { caja ->
                    val listaCronologica = caja.operaciones.sortedBy { it.timestamp }
                    val uri = ExcelExporter.exportar(requireContext(), listaCronologica, "caja_${caja.jornadaId}")
                    ExcelExporter.compartir(requireContext(), uri)
                },
                onItemClick = { caja ->
                    val dialogView = layoutInflater.inflate(R.layout.dialog_detalle_jornada, null)

                    // Formateamos el texto de forma más visual
                    val detalleStr = caja.operaciones.joinToString("\n") { op ->
                        val simbolo = if (op.tipo == TipoOperacion.VENTA) "↑" else "↓"
                        "$simbolo ${op.producto.padEnd(15)} x${op.cantidad.formatCompact()}  ${nf.format(op.total)}"
                    }

                    dialogView.findViewById<TextView>(R.id.tvTituloDetalle).text = "Caja: ${caja.jornadaId.toFechaLegible()}"
                    dialogView.findViewById<TextView>(R.id.tvCuerpoDetalle).text = detalleStr

                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DiarioSync_MaterialAlertDialog)
                        .setView(dialogView)
                        .setPositiveButton("Entendido", null)
                        .show()
                }
            )

            binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerView.adapter = cajaAdapter

            // Fix 5: el contador "X cajas" se actualiza observando la lista real
            viewModel.historialAgrupado.observe(viewLifecycleOwner) { cajas ->
                cajaAdapter.submitList(cajas)

                val cant = cajas?.size ?: 0
                binding.txtCantJornadas.text = when (cant) {
                    0    -> "Sin cajas"
                    1    -> "1 caja"
                    else -> "$cant cajas"
                }
            }

            binding.btnExportarHistorial.setOnClickListener {
                // 1. Obtenemos las cajas que ya están cargadas en el ViewModel y que el Adapter usa
                val cajasActuales = viewModel.historialAgrupado.value

                // 2. Aplanamos: de List<CajaHistorial> a List<Operacion>
                val todasLasOperaciones = cajasActuales?.flatMap { it.operaciones }
                    ?.sortedBy { it.timestamp } ?: emptyList()

                // 3. Verificamos la lista resultante
                if (todasLasOperaciones.isNotEmpty()) {
                    val uri = ExcelExporter.exportar(requireContext(), todasLasOperaciones, "historial_completo_diariosync")
                    ExcelExporter.compartir(requireContext(), uri)
                } else {
                    // Si entra acá es porque realmente no hay datos en el historialAgrupado
                    Snackbar.make(binding.root, "No hay operaciones para exportar", Snackbar.LENGTH_SHORT).show()
                }
            }

            binding.btnVaciarTodo.setOnClickListener {
                val dialogView = layoutInflater.inflate(R.layout.dialog_vaciar_historial, null)

                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DiarioSync_MaterialAlertDialog)
                    .setView(dialogView)
                    .setPositiveButton("Borrar Historial") { _, _ ->
                        viewModel.vaciarHistorial()
                        Snackbar.make(binding.root, "Historial vaciado", Snackbar.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        fun String.toFechaLegible(): String {
            return try {
                // Suponiendo que el ID es "2026-04-09"
                val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val outputFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "AR"))

                val fecha = LocalDate.parse(this, inputFormatter)
                fecha.format(outputFormatter).uppercase() // .uppercase() para el "ABR"
            } catch (e: Exception) {
                this // Si falla, devolvemos el original para no romper la app
            }
        }

        fun Double.formatCompact(): String {
            return if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()
        }
    }