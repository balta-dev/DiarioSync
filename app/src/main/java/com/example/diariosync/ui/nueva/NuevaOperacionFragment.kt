package com.example.diariosync.ui.nueva

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.diariosync.R
import com.example.diariosync.databinding.FragmentNuevaOperacionBinding
import com.example.diariosync.domain.model.Operacion
import com.example.diariosync.domain.model.TipoOperacion
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.util.Locale

class NuevaOperacionFragment : Fragment() {

    private var _binding: FragmentNuevaOperacionBinding? = null
    private val binding get() = _binding!!

    // Mantenemos el ViewModel específico para manejar el estado de la transacción
    private val viewModel: NuevaOperacionViewModel by viewModels()
    private val nf = NumberFormat.getCurrencyInstance(Locale("es", "AR"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNuevaOperacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configuración inicial: Venta por defecto y color inicial
        binding.toggleTipo.check(R.id.btnVenta)
        actualizarEstadoToggle(esVenta = true)

        // Listener del Toggle para cambiar colores dinámicamente
        binding.toggleTipo.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                actualizarEstadoToggle(esVenta = checkedId == R.id.btnVenta)
                actualizarTotal()
            }
        }

        // Reconstruir la Operacion si venimos en modo edición
        val operacionEnEdicion: Operacion? = arguments?.getString(ARG_OPERACION_ID)?.let { id ->
            Operacion(
                id        = id,
                tipo      = TipoOperacion.valueOf(arguments!!.getString(ARG_OPERACION_TIPO)!!),
                producto  = arguments!!.getString(ARG_OPERACION_PROD)!!,
                cantidad  = arguments!!.getDouble(ARG_OPERACION_CANT),
                precio    = arguments!!.getDouble(ARG_OPERACION_PRECIO),
                timestamp = arguments!!.getLong(ARG_OPERACION_TS),
                deviceId  = arguments!!.getString(ARG_OPERACION_DEV)!!,
                synced    = arguments!!.getBoolean(ARG_OPERACION_SYNCED),
                activa    = arguments!!.getBoolean(ARG_OPERACION_ACTIVA),
                jornadaId = arguments!!.getString(ARG_OPERACION_JORNADA)!!
            )
        }

// Pre-popular campos si editamos
        operacionEnEdicion?.let { op ->
            binding.etProducto.setText(op.producto)
            binding.etCantidad.setText(op.cantidad.toString())
            binding.etPrecio.setText(op.precio.toString())

            val esVenta = op.tipo == TipoOperacion.VENTA
            binding.toggleTipo.check(if (esVenta) R.id.btnVenta else R.id.btnCompra)
            actualizarEstadoToggle(esVenta)
            actualizarTotal()
        }

        // Observadores de LiveData (del primer código, esenciales para la lógica)
        setupObservers()

        // Cálculo de total automático con doAfterTextChanged (más limpio que TextWatcher)
        binding.etCantidad.doAfterTextChanged { actualizarTotal() }
        binding.etPrecio.doAfterTextChanged { actualizarTotal() }

        binding.btnGuardar.setOnClickListener {
            if (operacionEnEdicion != null) {
                ejecutarActualizacion(operacionEnEdicion)
            } else {
                ejecutarGuardado()
            }
        }

        // Si tienes el botón volver en el XML
        binding.btnVolver?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupObservers() {
        viewModel.guardadoExitoso.observe(viewLifecycleOwner) { exito ->
            if (exito) {
                //Toast.makeText(requireContext(), "Guardado correctamente", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
                viewModel.resetEstado()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.resetEstado()
            }
        }
    }

    private fun ejecutarGuardado() {
        val producto = binding.etProducto.text.toString().trim()
        val cantStr = binding.etCantidad.text.toString().trim()
        val precioStr = binding.etPrecio.text.toString().trim()

        if (producto.isEmpty() || cantStr.isEmpty() || precioStr.isEmpty()) {
            Snackbar.make(binding.root, "Completa todos los campos", Snackbar.LENGTH_SHORT).show()
            return
        }

        val tipo = if (binding.toggleTipo.checkedButtonId == R.id.btnVenta)
            TipoOperacion.VENTA else TipoOperacion.COMPRA

        val prefs = requireContext().getSharedPreferences("device_prefs", android.content.Context.MODE_PRIVATE)
        val nombreUsuario = prefs.getString("user_name", "Desconocido") ?: "Desconocido"

        // Seteamos los valores al ViewModel y disparamos el guardado
        viewModel.producto.value = producto
        viewModel.cantidad.value = cantStr
        viewModel.precio.value = precioStr
        viewModel.guardar(tipo, nombreUsuario)
    }

    private fun ejecutarActualizacion(original: Operacion) {
        val producto  = binding.etProducto.text.toString().trim()
        val cantStr   = binding.etCantidad.text.toString().trim()
        val precioStr = binding.etPrecio.text.toString().trim()

        if (producto.isEmpty() || cantStr.isEmpty() || precioStr.isEmpty()) {
            Snackbar.make(binding.root, "Completa todos los campos", Snackbar.LENGTH_SHORT).show()
            return
        }

        val tipo = if (binding.toggleTipo.checkedButtonId == R.id.btnVenta)
            TipoOperacion.VENTA else TipoOperacion.COMPRA

        viewModel.producto.value = producto
        viewModel.cantidad.value = cantStr
        viewModel.precio.value   = precioStr
        viewModel.actualizar(original, tipo)
    }

    private fun actualizarEstadoToggle(esVenta: Boolean) {
        val colorVenta = ContextCompat.getColor(requireContext(), R.color.ds_green)
        val colorCompra = ContextCompat.getColor(requireContext(), R.color.ds_red)
        val colorDimVenta = ContextCompat.getColor(requireContext(), R.color.ds_green_dim)
        val colorDimCompra = ContextCompat.getColor(requireContext(), R.color.ds_red_dim)
        val colorNeutral = ContextCompat.getColor(requireContext(), R.color.ds_surface2)
        val colorTextNeutral = ContextCompat.getColor(requireContext(), R.color.ds_text_secondary)
        val colorBorderNeutral = ContextCompat.getColor(requireContext(), R.color.ds_border2)

        if (esVenta) {
            // Estilo Venta Activo
            binding.btnVenta.backgroundTintList = ColorStateList.valueOf(colorDimVenta)
            binding.btnVenta.setTextColor(colorVenta)
            binding.btnVenta.strokeColor = ColorStateList.valueOf(colorVenta)

            // Estilo Compra Inactivo
            binding.btnCompra.backgroundTintList = ColorStateList.valueOf(colorNeutral)
            binding.btnCompra.setTextColor(colorTextNeutral)
            binding.btnCompra.strokeColor = ColorStateList.valueOf(colorBorderNeutral)
        } else {
            // Estilo Compra Activo
            binding.btnCompra.backgroundTintList = ColorStateList.valueOf(colorDimCompra)
            binding.btnCompra.setTextColor(colorCompra)
            binding.btnCompra.strokeColor = ColorStateList.valueOf(colorCompra)

            // Estilo Venta Inactivo
            binding.btnVenta.backgroundTintList = ColorStateList.valueOf(colorNeutral)
            binding.btnVenta.setTextColor(colorTextNeutral)
            binding.btnVenta.strokeColor = ColorStateList.valueOf(colorBorderNeutral)
        }
    }

    private fun actualizarTotal() {
        val cant = binding.etCantidad.text.toString().toDoubleOrNull() ?: 0.0
        val precio = binding.etPrecio.text.toString().toDoubleOrNull() ?: 0.0
        val total = cant * precio

        val esVenta = binding.toggleTipo.checkedButtonId == R.id.btnVenta

        if (total == 0.0) {
            binding.tvTotalPreview.text = "Total: ${nf.format(0.0)}"
            binding.tvTotalPreview.setTextColor(ContextCompat.getColor(requireContext(), R.color.ds_text_secondary))
        } else {
            binding.tvTotalPreview.text = "Total: ${nf.format(total)}"
            binding.tvTotalPreview.setTextColor(
                ContextCompat.getColor(requireContext(), if (esVenta) R.color.ds_green else R.color.ds_red)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_OPERACION_ID      = "op_id"
        private const val ARG_OPERACION_TIPO    = "op_tipo"
        private const val ARG_OPERACION_PROD    = "op_producto"
        private const val ARG_OPERACION_CANT    = "op_cantidad"
        private const val ARG_OPERACION_PRECIO  = "op_precio"
        private const val ARG_OPERACION_TS      = "op_timestamp"
        private const val ARG_OPERACION_DEV     = "op_deviceId"
        private const val ARG_OPERACION_SYNCED  = "op_synced"
        private const val ARG_OPERACION_ACTIVA  = "op_activa"
        private const val ARG_OPERACION_JORNADA = "op_jornadaId"

        fun paraEditar(operacion: Operacion): NuevaOperacionFragment {
            return NuevaOperacionFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_OPERACION_ID,      operacion.id)
                    putString(ARG_OPERACION_TIPO,    operacion.tipo.name)
                    putString(ARG_OPERACION_PROD,    operacion.producto)
                    putDouble(ARG_OPERACION_CANT,    operacion.cantidad)
                    putDouble(ARG_OPERACION_PRECIO,  operacion.precio)
                    putLong  (ARG_OPERACION_TS,      operacion.timestamp)
                    putString(ARG_OPERACION_DEV,     operacion.deviceId)
                    putBoolean(ARG_OPERACION_SYNCED, operacion.synced)
                    putBoolean(ARG_OPERACION_ACTIVA, operacion.activa)
                    putString(ARG_OPERACION_JORNADA, operacion.jornadaId)
                }
            }
        }
    }
}