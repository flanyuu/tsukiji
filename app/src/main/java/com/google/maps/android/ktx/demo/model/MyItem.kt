package com.google.maps.android.ktx.demo.model

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

data class MyItem(
    private val lat: Double,
    private val lng: Double,
    private val itemTitle: String,
    private val itemSnippet: String,
    val category: String = "all" // Valor por defecto si no se especifica
) : ClusterItem {

    constructor(position: LatLng, title: String, snippet: String, category: String = "all") : this(
        position.latitude,
        position.longitude,
        title,
        snippet,
        category
    )

    override fun getPosition(): LatLng = LatLng(lat, lng)
    override fun getTitle(): String = itemTitle
    override fun getSnippet(): String = itemSnippet
    override fun getZIndex(): Float = 0f
}