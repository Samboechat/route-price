package com.routeprice.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude fora do intervalo" }
        require(longitude in -180.0..180.0) { "longitude fora do intervalo" }
    }
}

/**
 * Um local da viagem (embarque, parada ou destino).
 * [location] é nulo quando o motorista digitou o endereço sem confirmar no mapa (modo manual).
 */
@Serializable
data class PlaceRef(
    val label: String,
    val address: String = "",
    val location: GeoPoint? = null,
    val placeId: String? = null,
) {
    val isGeolocated: Boolean get() = location != null
    val displayText: String get() = label.ifBlank { address }
}

/** Sugestão de autocomplete de endereço. */
data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
)
