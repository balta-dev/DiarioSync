package com.example.diariosync.ui.lista

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.diariosync.R
import com.example.diariosync.domain.model.CajaHistorial
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CajaHistorialAdapter(
    private val onExportClick: (CajaHistorial) -> Unit,
    private val onItemClick: (CajaHistorial) -> Unit
) : ListAdapter<CajaHistorial, CajaHistorialAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_jornada, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtFecha         = view.findViewById<TextView>(R.id.txtFechaJornada)
        private val txtCantOps       = view.findViewById<TextView>(R.id.txtCantOperaciones)
        private val txtVentas        = view.findViewById<TextView>(R.id.txtVentasJornada)
        private val txtCompras       = view.findViewById<TextView>(R.id.txtComprasJornada)
        private val btnExportar      = view.findViewById<ImageButton>(R.id.btnExportarCajaIndiv)

        fun bind(caja: CajaHistorial) {
            txtFecha.text    = caja.jornadaId.toFechaLegible()
            txtCantOps.text  = "Cerrada · ${caja.cantOperaciones} operaciones"
            txtVentas.text   = "$${caja.totalVentas}"
            txtCompras.text  = "$${caja.totalCompras}"

            btnExportar.setOnClickListener { onExportClick(caja) }
            itemView.setOnClickListener { onItemClick(caja) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<CajaHistorial>() {
        override fun areItemsTheSame(oldItem: CajaHistorial, newItem: CajaHistorial) =
            oldItem.jornadaId == newItem.jornadaId

        override fun areContentsTheSame(oldItem: CajaHistorial, newItem: CajaHistorial) =
            oldItem == newItem
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
}