package com.google.maps.android.ktx.demo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.card.MaterialCardView
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.ktx.awaitMap
import com.google.maps.android.ktx.awaitMapLoad
import com.google.maps.android.ktx.cameraMoveStartedEvents
import com.google.maps.android.ktx.cameraIdleEvents
import com.google.maps.android.ktx.demo.model.MyItem
import kotlinx.coroutines.launch
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private var heatmapProvider: HeatmapTileProvider? = null
    private var allBusinessLocations = mutableListOf<LatLng>()
    private var allBusinessItems = mutableListOf<MyItem>()
    private var heatmapOverlay: TileOverlay? = null
    private var googleMap: GoogleMap? = null
    private var suggestionMarkers = mutableListOf<Marker>()

    private val businessCategories = mapOf(
        "all" to "Todos",
        "restaurant" to "Restaurantes",
        "retail" to "Tiendas",
        "service" to "Servicios",
        "entertainment" to "Entretenimiento"
    )

    private val newBusinessCategories = mapOf(
        "restaurant" to "🍽️ Restaurante",
        "retail" to "🛍️ Tienda",
        "service" to "🔧 Servicio",
        "entertainment" to "🎬 Entretenimiento"
    )

    private lateinit var guardadosFragment: GuardadosFragment
    private lateinit var propiedadesFragment: PropiedadesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isRestore = savedInstanceState != null

        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            setContentView(R.layout.activity_main)

            val container = findViewById<ConstraintLayout>(R.id.main_container)
            applyInsets(container)

            setupBottomNavigation()
            setupFilterFab()

            if (BuildConfig.MAPS_API_KEY.isEmpty()) {
                Toast.makeText(this, "Falta API key de Google Maps", Toast.LENGTH_LONG).show()
                Log.e(TAG, "API Key está vacía")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en onCreate inicial: ${e.message}", e)
            Toast.makeText(this, "Error al inicializar: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment

        if (mapFragment == null) {
            Log.e(TAG, "MapFragment no encontrado")
            Toast.makeText(this, "Error: MapFragment no encontrado", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                    googleMap = mapFragment.awaitMap()
                    Log.d(TAG, "Mapa inicializado correctamente")

                    if (!isRestore) {
                        googleMap?.awaitMapLoad()
                        Log.d(TAG, "Mapa cargado completamente")
                    }

                    try {
                        loadBusinessDataFallback()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cargando datos: ${e.message}", e)
                        Toast.makeText(
                            this@MainActivity,
                            "Error cargando datos, generando datos de muestra",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (allBusinessLocations.isEmpty()) {
                            generateSampleBusinessData()
                        }
                    }

                    Log.d(TAG, "Total de ubicaciones cargadas: ${allBusinessLocations.size}")

                    googleMap?.let { map ->
                        if (allBusinessLocations.isNotEmpty()) {
                            setupHeatmap(map, allBusinessLocations)
                            centerMapOnData(map)
                            setupFilterChips(map)
                            setupNewBusinessChips()
                            setupFindLocationButton()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "No se pudieron cargar datos",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    launch {
                        googleMap?.cameraMoveStartedEvents()?.collect {
                            Log.d(TAG, "Camera moved - reason $it")
                        }
                    }
                    launch {
                        googleMap?.cameraIdleEvents()?.collect {
                            Log.d(TAG, "Camera is idle.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en inicialización del mapa: ${e.message}", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error al cargar el mapa: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupFragments() {
        guardadosFragment = GuardadosFragment()
        propiedadesFragment = PropiedadesFragment()
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()

        findViewById<FrameLayout>(R.id.fragment_container).visibility = View.VISIBLE
        findViewById<ConstraintLayout>(R.id.map_container).visibility = View.GONE
    }

    private fun showMap() {
        findViewById<FrameLayout>(R.id.fragment_container).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.map_container).visibility = View.VISIBLE
    }

    private fun setupFilterFab() {
        val fab = findViewById<FloatingActionButton>(R.id.fab_filters)
        val filterCard = findViewById<MaterialCardView>(R.id.filter_card)

        fab.setOnClickListener {
            if (filterCard.visibility == View.VISIBLE) {
                filterCard.visibility = View.GONE
            } else {
                filterCard.visibility = View.VISIBLE
            }
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        setupFragments()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> {
                    showMap()
                    true
                }
                R.id.nav_saved -> {
                    showFragment(guardadosFragment)
                    true
                }
                R.id.nav_propiedades -> {
                    showFragment(propiedadesFragment)
                    true
                }
                else -> false
            }
        }

        bottomNav.selectedItemId = R.id.nav_map
    }

    fun saveCurrentLocation(title: String, category: String, score: Double) {
        googleMap?.cameraPosition?.target?.let { location ->
            val savedLocation = SavedLocation(
                position = location,
                title = title,
                description = "Puntuación: $score",
                category = category,
                score = score,
                timestamp = System.currentTimeMillis()
            )
            guardadosFragment.addSavedLocation(savedLocation)
            Toast.makeText(this, "Ubicación guardada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSaveDialog(title: String, category: String, score: Double) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Guardar ubicación")
        builder.setMessage("¿Deseas guardar esta ubicación en tus favoritos?")

        builder.setPositiveButton("Guardar") { dialog, _ ->
            saveCurrentLocation(title, category, score)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun setupNewBusinessChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_new_business)

        newBusinessCategories.forEach { (key, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                tag = key
                setChipBackgroundColorResource(android.R.color.white)
                setTextColor(getColor(android.R.color.darker_gray))
                chipStrokeWidth = 2f
                chipStrokeColor = getColorStateList(android.R.color.darker_gray)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        setChipBackgroundColorResource(android.R.color.holo_orange_light)
                        setTextColor(getColor(android.R.color.white))
                    } else {
                        setChipBackgroundColorResource(android.R.color.white)
                        setTextColor(getColor(android.R.color.darker_gray))
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupFindLocationButton() {
        val button = findViewById<MaterialButton>(R.id.btn_find_location)
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_new_business)

        button.setOnClickListener {
            val selectedChipId = chipGroup.checkedChipId
            if (selectedChipId == View.NO_ID) {
                Toast.makeText(this, "Por favor selecciona un tipo de negocio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedChip = chipGroup.findViewById<Chip>(selectedChipId)
            val category = selectedChip?.tag as? String ?: return@setOnClickListener

            findBestLocations(category)
        }
    }

    private fun findBestLocations(category: String) {
        googleMap?.let { map ->
            suggestionMarkers.forEach { it.remove() }
            suggestionMarkers.clear()
            setupCustomInfoWindow(map)

            Toast.makeText(this, "Analizando mejores ubicaciones para $category en esta área...", Toast.LENGTH_SHORT).show()

            val visibleRegion = map.projection.visibleRegion
            val bounds = visibleRegion.latLngBounds

            Log.d(TAG, "Buscando en área visible: ${bounds.southwest} a ${bounds.northeast}")

            val visibleBusinesses = allBusinessItems.filter { item ->
                bounds.contains(item.position)
            }

            val sameCategory = visibleBusinesses.filter { it.category == category }

            if (visibleBusinesses.isEmpty()) {
                Toast.makeText(this, "No hay negocios en esta área. Mueve el mapa a otra zona.", Toast.LENGTH_SHORT).show()
                return
            }

            if (sameCategory.isEmpty()) {
                Toast.makeText(this, "No hay negocios de tipo $category en esta área. ¡Oportunidad perfecta!", Toast.LENGTH_LONG).show()
                val centerLat = (bounds.northeast.latitude + bounds.southwest.latitude) / 2
                val centerLng = (bounds.northeast.longitude + bounds.southwest.longitude) / 2
                val centerLocation = LatLng(centerLat, centerLng)

                val marker = map.addMarker(
                    MarkerOptions()
                        .position(centerLocation)
                        .title("TOCAME PARA GUARDARME\nUbicación Sugerida #1")
                        .snippet("Excelente oportunidad: No hay competencia en esta área\nÍndice de oportunidad: 100.0")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
                marker?.tag = LocationSuggestion(centerLocation, 100.0, "Excelente oportunidad: No hay competencia en esta área")
                marker?.let { suggestionMarkers.add(it) }
                marker?.showInfoWindow()

                map.setOnInfoWindowClickListener { clickedMarker ->
                    val suggestion = clickedMarker.tag as? LocationSuggestion
                    suggestion?.let {
                        showSaveDialog(clickedMarker.title ?: "Ubicación", category, it.score)
                    }
                }
                return
            }

            val bestLocations = findOptimalLocationsInBounds(bounds, sameCategory, visibleBusinesses)

            if (bestLocations.isEmpty()) {
                Toast.makeText(this, "No se encontraron ubicaciones óptimas", Toast.LENGTH_SHORT).show()
                return
            }

            bestLocations.take(5).forEachIndexed { index, location ->
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(location.position)
                        .title("TOCAME PARA GUARDARME\nUbicación Sugerida #${index + 1}")
                        .snippet("${location.reason}\nÍndice de oportunidad: ${String.format("%.1f", location.score)}")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                )
                marker?.tag = location
                marker?.let { suggestionMarkers.add(it) }
            }

            map.setOnInfoWindowClickListener { clickedMarker ->
                val suggestion = clickedMarker.tag as? LocationSuggestion
                suggestion?.let {
                    saveCurrentLocation(clickedMarker.title ?: "Ubicación", category, it.score)
                }
            }

            val bestLocation = bestLocations.first()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(bestLocation.position, 13f))
            suggestionMarkers.firstOrNull()?.showInfoWindow()

            Toast.makeText(this, "Se encontraron ${bestLocations.size} ubicaciones óptimas. Mostrando las mejores 5.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupCustomInfoWindow(map: GoogleMap) {
        map.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null

            override fun getInfoContents(marker: Marker): View {
                val view = layoutInflater.inflate(R.layout.custom_info_window, null)
                val titleView = view.findViewById<TextView>(R.id.title)
                val snippetView = view.findViewById<TextView>(R.id.snippet)
                titleView.text = marker.title
                snippetView.text = marker.snippet
                return view
            }
        })
    }

    private fun findOptimalLocationsInBounds(
        bounds: LatLngBounds,
        sameCategory: List<MyItem>,
        allBusinesses: List<MyItem>
    ): List<LocationSuggestion> {
        val suggestions = mutableListOf<LocationSuggestion>()
        if (allBusinesses.isEmpty()) return suggestions

        val gridSize = 15
        val latStep = (bounds.northeast.latitude - bounds.southwest.latitude) / gridSize
        val lngStep = (bounds.northeast.longitude - bounds.southwest.longitude) / gridSize

        for (i in 0..gridSize) {
            for (j in 0..gridSize) {
                val lat = bounds.southwest.latitude + (latStep * i)
                val lng = bounds.southwest.longitude + (lngStep * j)
                val candidateLocation = LatLng(lat, lng)
                val score = calculateLocationScore(candidateLocation, sameCategory, allBusinesses)
                if (score > 0) {
                    suggestions.add(LocationSuggestion(
                        position = candidateLocation,
                        score = score,
                        reason = generateReason(score, sameCategory.size)
                    ))
                }
            }
        }

        return suggestions.sortedByDescending { it.score }
    }

    private fun calculateLocationScore(
        location: LatLng,
        sameCategory: List<MyItem>,
        allBusinesses: List<MyItem>
    ): Double {
        val nearestCompetitor = sameCategory.minOfOrNull {
            calculateDistance(location, it.position)
        } ?: Double.MAX_VALUE

        val nearestBusiness = allBusinesses.minOfOrNull {
            calculateDistance(location, it.position)
        } ?: Double.MAX_VALUE

        val nearbyBusinessCount = allBusinesses.count {
            calculateDistance(location, it.position) < 2.0
        }

        val nearbyCompetitors = sameCategory.count {
            calculateDistance(location, it.position) < 1.0
        }

        var score = 0.0
        score += min(nearestCompetitor * 10, 50.0)
        score += max(30.0 - nearestBusiness * 5, 0.0)
        score += min(nearbyBusinessCount * 2.0, 20.0)
        score -= nearbyCompetitors * 10.0

        return max(score, 0.0)
    }

    private fun calculateDistance(pos1: LatLng, pos2: LatLng): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(pos2.latitude - pos1.latitude)
        val dLng = Math.toRadians(pos2.longitude - pos1.longitude)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(pos1.latitude)) *
                cos(Math.toRadians(pos2.latitude)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun generateReason(score: Double, totalCompetitors: Int): String {
        return when {
            score > 70 -> "Excelente ubicación: Baja competencia, alta actividad comercial"
            score > 50 -> "Buena ubicación: Balance entre competencia y tráfico"
            score > 30 -> "Ubicación aceptable: Zona emergente con potencial"
            else -> "Ubicación con oportunidades de crecimiento"
        }
    }

    private fun loadBusinessDataFallback() {
        Log.d(TAG, "Iniciando carga de datos...")
        try {
            generateSampleBusinessData()
            if (allBusinessLocations.isEmpty()) {
                Toast.makeText(this, "No hay datos para mostrar", Toast.LENGTH_LONG).show()
            } else {
                Log.d(TAG, "Total de negocios cargados: ${allBusinessLocations.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en loadBusinessDataFallback: ${e.message}", e)
            Toast.makeText(this, "Error al cargar datos: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateSampleBusinessData() {
        val random = java.util.Random()
        val categories = listOf("restaurant", "retail", "service", "entertainment")

        val majorCities = listOf(
            LatLng(25.6866, -100.3161) to "Monterrey",
            LatLng(25.4232, -100.9903) to "Saltillo",
            LatLng(19.4326, -99.1332) to "Ciudad de México",
            LatLng(40.7128, -74.0060) to "Nueva York",
            LatLng(34.0522, -118.2437) to "Los Ángeles",
            LatLng(51.5074, -0.1278) to "Londres",
            LatLng(48.8566, 2.3522) to "París",
            LatLng(35.6762, 139.6503) to "Tokio"
        )

        var totalGenerated = 0
        majorCities.forEach { (center, cityName) ->
            val numPoints = 80 + random.nextInt(41)
            for (i in 0 until numPoints) {
                val lat = center.latitude + (random.nextDouble() - 0.5) * 0.1
                val lng = center.longitude + (random.nextDouble() - 0.5) * 0.1
                val location = LatLng(lat, lng)
                allBusinessLocations.add(location)
                val category = categories[random.nextInt(categories.size)]
                allBusinessItems.add(MyItem(location, "Negocio en $cityName", "Categoría: $category", category))
                totalGenerated++
            }
        }
        Log.d(TAG, "Generados $totalGenerated negocios en ${majorCities.size} ciudades")
    }

    private fun centerMapOnData(map: GoogleMap) {
        if (allBusinessLocations.isEmpty()) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2F))
            return
        }
        try {
            val boundsBuilder = LatLngBounds.Builder()
            allBusinessLocations.forEach { boundsBuilder.include(it) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
        } catch (e: Exception) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2F))
        }
    }

    private fun setupHeatmap(map: GoogleMap, locations: List<LatLng>) {
        heatmapOverlay?.remove()
        if (locations.isEmpty()) return

        try {
            val colors = intArrayOf(
                Color.rgb(0, 255, 0),
                Color.rgb(255, 255, 0),
                Color.rgb(255, 165, 0),
                Color.rgb(255, 0, 0)
            )
            val gradient = Gradient(colors, floatArrayOf(0.0f, 0.3f, 0.6f, 1.0f))
            val provider = HeatmapTileProvider.Builder()
                .data(locations)
                .gradient(gradient)
                .radius(50)
                .opacity(0.7)
                .build()
            heatmapProvider = provider
            heatmapOverlay = map.addTileOverlay(TileOverlayOptions().tileProvider(provider))
        } catch (e: Exception) {
            Log.e(TAG, "Error creando el mapa de calor: ${e.message}", e)
        }
    }

    private fun setupFilterChips(map: GoogleMap) {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_filters)

        businessCategories.forEach { (key, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = key == "all"
                tag = key
                setChipBackgroundColorResource(android.R.color.white)
                setTextColor(getColor(android.R.color.darker_gray))
                chipStrokeWidth = 2f
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        setChipBackgroundColorResource(android.R.color.holo_orange_light)
                        setTextColor(getColor(android.R.color.white))
                    } else {
                        setChipBackgroundColorResource(android.R.color.white)
                        setTextColor(getColor(android.R.color.darker_gray))
                    }
                }
            }
            chipGroup.addView(chip)
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                group.check(group.getChildAt(0).id)
                return@setOnCheckedStateChangeListener
            }
            val selectedChip = group.findViewById<Chip>(checkedIds.first())
            val category = selectedChip?.tag as? String ?: "all"
            filterBusinesses(map, category)
        }
    }

    private fun filterBusinesses(map: GoogleMap, category: String) {
        val filteredLocations = if (category == "all") {
            allBusinessLocations
        } else {
            allBusinessItems.filter { it.category == category }.map { it.position }
        }
        setupHeatmap(map, filteredLocations)
        Toast.makeText(this, "Mostrando: ${filteredLocations.size} negocios", Toast.LENGTH_SHORT).show()
    }

    data class LocationSuggestion(
        val position: LatLng,
        val score: Double,
        val reason: String
    )

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        fun applyInsets(container: View) {
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                val innerPadding = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom)
                insets
            }
        }
    }
}