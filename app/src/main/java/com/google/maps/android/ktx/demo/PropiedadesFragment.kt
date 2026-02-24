package com.google.maps.android.ktx.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText

data class PropiedadItem(
    val nombre: String,
    val direccion: String,
    val precio: String,
    val tipo: String
)

class PropiedadesFragment : Fragment() {

    private val propiedades = mutableListOf(
        PropiedadItem("Local Comercial Centro", "Av. Constitución 100, Monterrey", "$15,000 MXN/mes", "Renta"),
        PropiedadItem("Oficina San Pedro", "Blvd. Antonio L. Rodríguez 3000", "$25,000 MXN/mes", "Renta"),
        PropiedadItem("Bodega Industrial", "Carretera a Laredo km 12", "$2,500,000 MXN", "Venta"),
        PropiedadItem("Local en Plaza", "Plaza Fiesta San Agustín", "$18,000 MXN/mes", "Renta"),
        PropiedadItem("Terreno Comercial", "Av. Eugenio Garza Sada", "$8,000,000 MXN", "Venta")
    )

    private lateinit var adapter: PropiedadAdapter
    private var currentFilter = "Todas"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_propiedades, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_propiedades)
        val tabFilter = view.findViewById<TabLayout>(R.id.tab_filter)
        val btnAgregar = view.findViewById<ImageView>(R.id.btn_agregar_propiedad)

        adapter = PropiedadAdapter(getFilteredList()) { position ->
            val filtered = getFilteredList()
            val toRemove = filtered[position]
            propiedades.remove(toRemove)
            adapter.updateData(getFilteredList())
            Toast.makeText(requireContext(), "Propiedad eliminada", Toast.LENGTH_SHORT).show()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilter = tab?.text?.toString() ?: "Todas"
                adapter.updateData(getFilteredList())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnAgregar.setOnClickListener {
            showAddPropertyDialog()
        }
    }

    private fun getFilteredList(): List<Propiedad> = when (currentFilter) {
        "Renta" -> propiedades.filter { it.tipo == "Renta" }
        "Venta" -> propiedades.filter { it.tipo == "Venta" }
        else -> propiedades.toList()
    }

    private fun showAddPropertyDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_agregar_propiedad, null)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val toggleGroup = dialogView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggle_tipo)
        val etNombre = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_nombre)
        val etDireccion = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_direccion)
        val etPrecio = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_precio)
        val btnPublicar = dialogView.findViewById<android.widget.Button>(R.id.btn_publicar)

        // Seleccionar Venta por defecto
        toggleGroup.check(R.id.btn_venta)

        btnPublicar.setOnClickListener {
            val nombre = etNombre.text?.toString()?.trim() ?: ""
            val direccion = etDireccion.text?.toString()?.trim() ?: ""
            val precio = etPrecio.text?.toString()?.trim() ?: ""

            if (nombre.isEmpty() || direccion.isEmpty() || precio.isEmpty()) {
                Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tipoSeleccionado = when (toggleGroup.checkedButtonId) {
                R.id.btn_renta -> "Renta"
                else -> "Venta"
            }

            val nuevaPropiedad = Propiedad(
                nombre = nombre,
                direccion = direccion,
                precio = "$${precio} MXN${if (tipoSeleccionado == "Renta") "/mes" else ""}",
                tipo = tipoSeleccionado
            )

            propiedades.add(0, nuevaPropiedad)
            adapter.updateData(getFilteredList())
            dialog.dismiss()
            Toast.makeText(requireContext(), "Propiedad publicada", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    inner class PropiedadAdapter(
        private var items: List<Propiedad>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<PropiedadAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNombre: TextView = view.findViewById(R.id.tv_nombre)
            val tvPrecio: TextView = view.findViewById(R.id.tv_precio)
            val tvTipo: TextView = view.findViewById(R.id.tv_tipo)
            val btnEliminar: ImageView = view.findViewById(R.id.btn_eliminar)
        }

        fun updateData(newItems: List<Propiedad>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_propiedad, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvNombre.text = item.nombre
            holder.tvPrecio.text = "Precio: ${item.precio}"
            holder.tvTipo.text = if (item.tipo == "Renta") "Renta 🏠" else "Venta 🏢"
            holder.tvTipo.setBackgroundResource(
                if (item.tipo == "Renta") R.drawable.chip_renta_bg else R.drawable.chip_venta_bg
            )
            holder.btnEliminar.setOnClickListener { onDelete(holder.adapterPosition) }
        }

        override fun getItemCount() = items.size
    }
}
