package com.example.diariosync.ui.agenda

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import com.example.diariosync.R
import com.example.diariosync.databinding.FragmentAgendaBinding
import com.example.diariosync.ui.lista.ListaFragment
import kotlinx.coroutines.launch

class AgendaFragment : Fragment() {

    private var _binding: FragmentAgendaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AgendaViewModel by viewModels()

    private var modoUnirseArriba = false
    private val DURACION_ANIM = 350L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAgendaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwitchLogic()
        setupObservers()
        setupButtons()
    }

    private fun setupSwitchLogic() {
        val focusListener = View.OnFocusChangeListener { vista, hasFocus ->
            if (hasFocus) {
                // Evaluamos de qué tarjeta viene el foco
                val esDeUnirse = vista.id == R.id.etCodigoUnirse || vista.id == R.id.etPasswordUnirse

                // Ejecutamos la animación si hace falta
                if (esDeUnirse && !modoUnirseArriba) {
                    switchLayouts(unirseArriba = true)
                } else if (!esDeUnirse && modoUnirseArriba) {
                    switchLayouts(unirseArriba = false)
                }

                // Llamamos al mini scroll para hacer visible el botón correcto
                val botonAMostrar = if (esDeUnirse) binding.btnUnirseAgenda else binding.btnCrearAgenda
                enfocarBoton(botonAMostrar)
            }
        }

        // Asignamos el mismo listener a todos los campos
        binding.etCodigoCrear.onFocusChangeListener = focusListener
        binding.etPasswordCrear.onFocusChangeListener = focusListener
        binding.etCodigoUnirse.onFocusChangeListener = focusListener
        binding.etPasswordUnirse.onFocusChangeListener = focusListener
    }

    private fun enfocarBoton(boton: View) {
        // Le damos 300ms de margen para que la animación y el teclado terminen de acomodar la pantalla
        binding.nestedScrollView.postDelayed({
            binding.nestedScrollView.smoothScrollTo(0, 200)
        }, 100)
    }

    /**
     * Lógica de posiciones:
     *
     * El layout dibuja las vistas en este orden (translationY = 0 para todas):
     *   [cardCrear]       top = 0
     *   [separador]       top = cardCrear.bottom
     *   [cardUnirse]      top = separador.bottom
     *
     * Cuando queremos "unirseArriba":
     *   cardUnirse  → debe quedar donde estaba cardCrear  → translationY = -(alturaCrear + alturaSep)
     *   separador   → debe quedar ENTRE las dos cards animadas → translationY = alturaUnirse - alturaCrear
     *                 (se corre hacia abajo desde su top original, que ya estaba bajo cardCrear)
     *   cardCrear   → debe quedar donde estaba cardUnirse → translationY = +(alturaUnirse + alturaSep)
     *
     * De esta forma el separador siempre tiene el mismo espacio arriba y abajo.
     */
    private fun switchLayouts(unirseArriba: Boolean) {
        if (modoUnirseArriba == unirseArriba) return
        modoUnirseArriba = unirseArriba

        binding.layoutCards.doOnLayout {
            val alturaCrear   = binding.cardCrear.height
            val alturaSep     = binding.layoutSeparador.height
            val alturaUnirse  = binding.cardUnirse.height

            val interp = FastOutSlowInInterpolator()

            if (unirseArriba) {
                // cardUnirse sube al top
                binding.cardUnirse.animate()
                    .translationY(-(alturaCrear + alturaSep).toFloat())
                    .setDuration(DURACION_ANIM).setInterpolator(interp).start()

                // separador queda centrado entre cardUnirse (ahora arriba) y cardCrear (ahora abajo)
                // Su posición base ya está en (alturaCrear). Necesita moverse a (alturaUnirse).
                // Entonces: translationY = alturaUnirse - alturaCrear
                binding.layoutSeparador.animate()
                    .translationY((alturaUnirse - alturaCrear).toFloat())
                    .setDuration(DURACION_ANIM).setInterpolator(interp).start()

                // cardCrear baja al fondo
                binding.cardCrear.animate()
                    .translationY((alturaUnirse + alturaSep).toFloat())
                    .setDuration(DURACION_ANIM).setInterpolator(interp).start()

                binding.tvSeparador.text = "o crea tu propia agenda"
            } else {
                // Todo vuelve a cero
                binding.cardUnirse.animate()
                    .translationY(0f)
                    .setDuration(DURACION_ANIM).setInterpolator(interp).start()

                binding.layoutSeparador.animate()
                    .translationY(0f)
                    .setDuration(DURACION_ANIM).setInterpolator(interp).start()

                binding.cardCrear.animate()
                    .translationY(0f)
                    .setDuration(DURACION_ANIM).setInterpolator(interp).start()

                binding.tvSeparador.text = "o sumate a alguien más"
            }

            //binding.nestedScrollView.smoothScrollTo(0, 0)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.estado.collect { estado ->
                when (estado) {
                    is AgendaViewModel.Estado.Idle -> Unit

                    is AgendaViewModel.Estado.Cargando -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.btnCrearAgenda.isEnabled = false
                        binding.btnUnirseAgenda.isEnabled = false
                    }

                    is AgendaViewModel.Estado.Exito -> navegarALista()

                    is AgendaViewModel.Estado.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnCrearAgenda.isEnabled = true
                        binding.btnUnirseAgenda.isEnabled = true
                        if (modoUnirseArriba) {
                            binding.tilCodigoUnirse.error = estado.mensaje
                        } else {
                            binding.tilCodigoCrear.error = estado.mensaje
                        }
                    }
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnCrearAgenda.setOnClickListener {
            val codigo = binding.etCodigoCrear.text.toString().trim()
            val password = binding.etPasswordCrear.text.toString().trim()
            if (codigo.length < 4) {
                binding.tilCodigoCrear.error = "Mínimo 4 caracteres"
                return@setOnClickListener
            }
            binding.tilCodigoCrear.error = null
            binding.tilCodigoUnirse.error = null
            viewModel.crearAgenda(codigo, password)
        }

        binding.btnUnirseAgenda.setOnClickListener {
            val codigo = binding.etCodigoUnirse.text.toString().trim()
            val password = binding.etPasswordUnirse.text.toString().trim()
            if (codigo.isEmpty()) {
                binding.tilCodigoUnirse.error = "Ingresá el código"
                return@setOnClickListener
            }
            binding.tilCodigoCrear.error = null
            binding.tilCodigoUnirse.error = null
            viewModel.unirseAAgenda(codigo, password)
        }
    }

    private fun navegarALista() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ListaFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}