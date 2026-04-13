package com.example.diariosync.ui.lista

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.diariosync.R
import com.example.diariosync.databinding.ItemOperacionBinding
import com.example.diariosync.domain.model.Operacion
import com.example.diariosync.domain.model.TipoOperacion
import java.text.NumberFormat
import java.util.Locale

class OperacionAdapter(
    private val onLongClick: (Operacion) -> Unit
) : ListAdapter<Operacion, OperacionAdapter.ViewHolder>(DiffCallback) {

    private val nf = NumberFormat.getCurrencyInstance(Locale("es", "AR"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOperacionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val operacion = getItem(position)
        holder.bind(operacion)
        holder.itemView.setOnLongClickListener {
            onLongClick(operacion)
            true
        }
    }

    inner class ViewHolder(private val binding: ItemOperacionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(op: Operacion) {
            val esVenta = op.tipo == TipoOperacion.VENTA

            // Ícono y fondo del badge según tipo
            binding.tvTipoIcono.text = if (esVenta) "↑" else "↓"
            binding.tvTipoIcono.setBackgroundResource(
                if (esVenta) R.drawable.bg_badge_venta else R.drawable.bg_badge_compra
            )
            binding.tvTipoIcono.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (esVenta) R.color.ds_green else R.color.ds_red
                )
            )

            // Datos
            binding.tvProducto.text = op.producto
            binding.tvDetalle.text = "${op.cantidad.formatCompact()} x ${nf.format(op.precio)}"
            binding.tvTotal.text    = nf.format(op.total)
            binding.tvTotal.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (esVenta) R.color.ds_green else R.color.ds_red
                )
            )

            /////////////// LÓGICA DE SYNC ANIMADA ////////////

            // 1. Cancelamos cualquier animación que haya quedado de un ítem anterior
            binding.tvSync.animate().cancel()

            // 2. Recuperamos el estado anterior que guardamos en la View
            val estabaSincronizado = binding.tvSync.tag as? Boolean ?: true

            if (op.synced) {
                // Caso: Está sincronizado
                if (!estabaSincronizado) {
                    // ACABA DE SINCRONIZAR: Brillo verde y fade out
                    binding.tvSync.setBackgroundResource(R.drawable.bg_dot_green)
                    binding.tvSync.alpha = 1f // Lo hacemos visible de golpe
                    binding.tvSync.post {
                        binding.tvSync.animate()
                            .alpha(0f)
                            .setDuration(1500)
                            .start()
                    }
                } else {
                    // YA ESTABA SINCRONIZADO: Totalmente invisible
                    binding.tvSync.alpha = 0f
                }
            } else {
                // Caso: No sincronizado (Ámbar sólido)
                binding.tvSync.setBackgroundResource(R.drawable.bg_dot_amber)
                binding.tvSync.alpha = 1f
            }

            // 3. Actualizamos el tag con el estado actual
            binding.tvSync.tag = op.synced

            //////////////////////

            android.util.Log.d("DEBUG_SYNC", "Item: ${op.producto} | Synced en objeto: ${op.synced}")
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Operacion>() {
        override fun areItemsTheSame(a: Operacion, b: Operacion) = a.id == b.id
        override fun areContentsTheSame(a: Operacion, b: Operacion) = a == b
    }

    fun Double.formatCompact(): String {
        return if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()
    }
}