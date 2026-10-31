package com.routeprice.core.data.repository

import com.routeprice.core.data.remote.AutocompleteRequest
import com.routeprice.core.data.remote.CircleDto
import com.routeprice.core.data.remote.LatLngDto
import com.routeprice.core.data.remote.LocationBiasDto
import com.routeprice.core.data.remote.PlacesApi
import com.routeprice.core.data.remote.RouteMapper
import com.routeprice.core.data.remote.RoutesApi
import com.routeprice.core.data.system.AndroidGeocoder
import com.routeprice.core.domain.model.AppResult
import com.routeprice.core.domain.model.DomainError
import com.routeprice.core.domain.model.GeoPoint
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PlaceSuggestion
import com.routeprice.core.domain.model.RouteOption
import com.routeprice.core.domain.repository.ExternalServicesStatus
import com.routeprice.core.domain.repository.PlacesRepository
import com.routeprice.core.domain.repository.RouteRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

private inline fun <T> guard(status: ExternalServicesStatus, block: () -> AppResult<T>): AppResult<T> {
    if (!status.isMapsConfigured) return AppResult.Failure(DomainError.MissingApiKey)
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        AppResult.Failure(RouteMapper.error(t))
    }
}

class GoogleRouteRepository @Inject constructor(
    private val api: RoutesApi,
    private val status: ExternalServicesStatus,
) : RouteRepository {

    override suspend fun computeRoutes(
        origin: PlaceRef,
        destination: PlaceRef,
        stops: List<PlaceRef>,
    ): AppResult<List<RouteOption>> = guard(status) {
        val o = RouteMapper.waypoint(origin)
        val d = RouteMapper.waypoint(destination)
        val s = stops.map(RouteMapper::waypoint)
        if (o == null || d == null || s.any { it == null }) return@guard AppResult.Failure(DomainError.AddressNotFound)
        val routes = RouteMapper.toDomain(api.computeRoutes(RouteMapper.request(o, d, s.filterNotNull(), null)))
        if (routes.isEmpty()) AppResult.Failure(DomainError.RouteNotFound) else AppResult.Success(routes)
    }
}

class GooglePlacesRepository @Inject constructor(
    private val api: PlacesApi,
    private val geocoder: AndroidGeocoder,
    private val status: ExternalServicesStatus,
) : PlacesRepository {

    override suspend fun autocomplete(query: String, near: GeoPoint?, sessionToken: String): AppResult<List<PlaceSuggestion>> {
        if (query.trim().length < MIN_QUERY) return AppResult.Success(emptyList())
        return guard(status) {
            val bias = near?.let { LocationBiasDto(CircleDto(LatLngDto(it.latitude, it.longitude), BIAS_RADIUS_METERS)) }
            val response = api.autocomplete(AutocompleteRequest(input = query.trim(), sessionToken = sessionToken, locationBias = bias))
            AppResult.Success(
                response.suggestions.orEmpty().mapNotNull { it.placePrediction }.filter { it.placeId.isNotBlank() }.map {
                    PlaceSuggestion(
                        placeId = it.placeId,
                        primaryText = it.structuredFormat?.mainText?.text ?: it.text?.text.orEmpty(),
                        secondaryText = it.structuredFormat?.secondaryText?.text.orEmpty(),
                    )
                },
            )
        }
    }

    override suspend fun details(placeId: String, sessionToken: String): AppResult<PlaceRef> = guard(status) {
        val dto = api.details(placeId, sessionToken)
        val loc = dto.location ?: return@guard AppResult.Failure(DomainError.AddressNotFound)
        AppResult.Success(
            PlaceRef(
                label = dto.displayName?.text?.ifBlank { null } ?: dto.formattedAddress.orEmpty(),
                address = dto.formattedAddress.orEmpty(),
                location = GeoPoint(loc.latitude, loc.longitude),
                placeId = dto.id.ifBlank { placeId },
            ),
        )
    }

    /** Usa o geocodificador do próprio Android (sem custo de API) para o endereço de um ponto do mapa. */
    override suspend fun reverseGeocode(point: GeoPoint): AppResult<PlaceRef> = geocoder.reverse(point)

    private companion object {
        const val MIN_QUERY = 3
        const val BIAS_RADIUS_METERS = 50_000.0
    }
}
