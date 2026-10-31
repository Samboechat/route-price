package com.routeprice.feature.trip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.routeprice.core.designsystem.theme.RpTheme
import com.routeprice.core.domain.model.GeoPoint
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.RouteOption
import com.routeprice.core.domain.util.PolylineCodec

private val BRAZIL_CENTER = LatLng(-15.78, -47.93)
private const val DEFAULT_ZOOM = 3.5f
private const val SINGLE_POINT_ZOOM = 14f
private const val BOUNDS_PADDING_PX = 120

private fun GeoPoint.latLng() = LatLng(latitude, longitude)

/**
 * Mapa com embarque, paradas, destino e as rotas (selecionada em destaque, alternativas em cinza).
 * Só é exibido quando a chave da Maps Platform está configurada.
 */
@Composable
fun TripMap(
    origin: PlaceRef?,
    stops: List<PlaceRef?>,
    destination: PlaceRef?,
    routes: List<RouteOption>,
    selectedRouteId: String?,
    darkTheme: Boolean,
    onMapClick: (GeoPoint) -> Unit,
    onRouteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(BRAZIL_CENTER, DEFAULT_ZOOM) }
    var loaded by remember { mutableStateOf(false) }
    val decoded = remember(routes) { routes.associate { r -> r.id to r.encodedPolyline?.let { PolylineCodec.decode(it).map { p -> p.latLng() } }.orEmpty() } }
    val points = listOfNotNull(origin?.location) + stops.mapNotNull { it?.location } + listOfNotNull(destination?.location)

    LaunchedEffect(loaded, points, decoded, selectedRouteId) {
        if (!loaded) return@LaunchedEffect
        val all = points.map { it.latLng() } + decoded[selectedRouteId].orEmpty()
        when {
            all.size >= 2 -> {
                val bounds = LatLngBounds.builder().apply { all.forEach { include(it) } }.build()
                runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING_PX)) }
            }
            all.size == 1 -> runCatching { camera.animate(CameraUpdateFactory.newLatLngZoom(all.first(), SINGLE_POINT_ZOOM)) }
        }
    }

    val routeColor = RpTheme.extended.mapRoute
    val altColor = RpTheme.extended.mapRouteAlternative
    val style = remember(darkTheme) {
        if (darkTheme) runCatching { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) }.getOrNull() else null
    }

    Box(modifier.clip(MaterialTheme.shapes.large)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(mapStyleOptions = style, isBuildingEnabled = false),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
            onMapClick = { onMapClick(GeoPoint(it.latitude, it.longitude)) },
            onMapLoaded = { loaded = true },
        ) {
            routes.filter { it.id != selectedRouteId }.forEach { r ->
                Polyline(points = decoded[r.id].orEmpty(), color = altColor, width = 12f, clickable = true, onClick = { onRouteClick(r.id) })
            }
            routes.firstOrNull { it.id == selectedRouteId }?.let { r ->
                Polyline(points = decoded[r.id].orEmpty(), color = routeColor, width = 16f, zIndex = 1f)
            }
            origin?.location?.let {
                Marker(state = rememberUpdatedMarkerState(it.latLng()), title = "Embarque", snippet = origin.displayText,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            }
            stops.forEachIndexed { i, stop ->
                stop?.location?.let {
                    Marker(state = rememberUpdatedMarkerState(it.latLng()), title = "Parada ${i + 1}", snippet = stop.displayText,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                }
            }
            destination?.location?.let {
                Marker(state = rememberUpdatedMarkerState(it.latLng()), title = "Destino", snippet = destination.displayText,
                    icon = BitmapDescriptorFactory.defaultMarker(hue(routeColor.toArgb())))
            }
        }
    }
}

private fun hue(argb: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv[0]
}
