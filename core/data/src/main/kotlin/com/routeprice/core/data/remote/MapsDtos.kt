package com.routeprice.core.data.remote

import kotlinx.serialization.Serializable

// ---------- Google Routes API (directions/v2:computeRoutes) ----------

@Serializable
data class LatLngDto(val latitude: Double, val longitude: Double)

@Serializable
data class LocationDto(val latLng: LatLngDto)

@Serializable
data class WaypointDto(
    val placeId: String? = null,
    val location: LocationDto? = null,
    val address: String? = null,
)

@Serializable
data class VehicleInfoDto(val emissionType: String)

@Serializable
data class RouteModifiersDto(
    val vehicleInfo: VehicleInfoDto? = null,
    val avoidTolls: Boolean = false,
)

@Serializable
data class ComputeRoutesRequest(
    val origin: WaypointDto,
    val destination: WaypointDto,
    val intermediates: List<WaypointDto> = emptyList(),
    val travelMode: String = "DRIVE",
    val routingPreference: String = "TRAFFIC_AWARE",
    val computeAlternativeRoutes: Boolean = false,
    val extraComputations: List<String> = listOf("TOLLS"),
    val routeModifiers: RouteModifiersDto? = null,
    val languageCode: String = "pt-BR",
    val regionCode: String = "BR",
    val units: String = "METRIC",
)

@Serializable
data class MoneyDto(val currencyCode: String = "", val units: String? = null, val nanos: Int? = null)

@Serializable
data class TollInfoDto(val estimatedPrice: List<MoneyDto>? = null)

@Serializable
data class TravelAdvisoryDto(val tollInfo: TollInfoDto? = null)

@Serializable
data class PolylineDto(val encodedPolyline: String? = null)

@Serializable
data class RouteLegDto(val distanceMeters: Long? = null, val duration: String? = null)

@Serializable
data class RouteDto(
    val distanceMeters: Long? = null,
    val duration: String? = null,
    val polyline: PolylineDto? = null,
    val legs: List<RouteLegDto>? = null,
    val travelAdvisory: TravelAdvisoryDto? = null,
    val description: String? = null,
    val routeLabels: List<String>? = null,
)

@Serializable
data class ComputeRoutesResponse(val routes: List<RouteDto>? = null)

// ---------- Places API (New) ----------

@Serializable
data class CircleDto(val center: LatLngDto, val radius: Double)

@Serializable
data class LocationBiasDto(val circle: CircleDto)

@Serializable
data class AutocompleteRequest(
    val input: String,
    val sessionToken: String,
    val languageCode: String = "pt-BR",
    val includedRegionCodes: List<String> = listOf("br"),
    val locationBias: LocationBiasDto? = null,
)

@Serializable
data class TextDto(val text: String = "")

@Serializable
data class StructuredFormatDto(val mainText: TextDto? = null, val secondaryText: TextDto? = null)

@Serializable
data class PlacePredictionDto(
    val placeId: String = "",
    val text: TextDto? = null,
    val structuredFormat: StructuredFormatDto? = null,
)

@Serializable
data class SuggestionDto(val placePrediction: PlacePredictionDto? = null)

@Serializable
data class AutocompleteResponse(val suggestions: List<SuggestionDto>? = null)

@Serializable
data class DisplayNameDto(val text: String = "")

@Serializable
data class PlaceDetailsDto(
    val id: String = "",
    val formattedAddress: String? = null,
    val location: LatLngDto? = null,
    val displayName: DisplayNameDto? = null,
)
