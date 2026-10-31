package com.routeprice.core.data.remote

import com.routeprice.core.domain.model.DomainError
import com.routeprice.core.domain.model.FuelType
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.RouteLeg
import com.routeprice.core.domain.model.RouteOption
import com.routeprice.core.domain.model.TollInfo
import com.routeprice.core.domain.model.ValueSource
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.math.BigDecimal

/** Conversões puras entre o formato da API e o domínio (testáveis sem Android). */
object RouteMapper {

    fun waypoint(place: PlaceRef): WaypointDto? = when {
        place.placeId != null -> WaypointDto(placeId = place.placeId)
        place.location != null -> WaypointDto(location = LocationDto(LatLngDto(place.location!!.latitude, place.location!!.longitude)))
        else -> null
    }

    fun request(origin: WaypointDto, destination: WaypointDto, stops: List<WaypointDto>, fuel: FuelType?) =
        ComputeRoutesRequest(
            origin = origin,
            destination = destination,
            intermediates = stops,
            // A API não oferece rotas alternativas quando há paradas intermediárias.
            computeAlternativeRoutes = stops.isEmpty(),
            routeModifiers = RouteModifiersDto(vehicleInfo = VehicleInfoDto(emissionType(fuel))),
        )

    private fun emissionType(fuel: FuelType?) = when (fuel) {
        FuelType.DIESEL -> "DIESEL"
        FuelType.ELECTRIC -> "ELECTRIC"
        else -> "GASOLINE"
    }

    fun toDomain(response: ComputeRoutesResponse): List<RouteOption> =
        response.routes.orEmpty().mapIndexedNotNull { index, dto ->
            val distance = dto.distanceMeters ?: return@mapIndexedNotNull null
            RouteOption(
                id = "route-$index",
                distanceMeters = distance,
                durationSeconds = parseDuration(dto.duration),
                encodedPolyline = dto.polyline?.encodedPolyline,
                legs = dto.legs.orEmpty().map { RouteLeg(it.distanceMeters ?: 0, parseDuration(it.duration)) },
                tolls = toll(dto.travelAdvisory?.tollInfo),
                description = dto.description.orEmpty(),
                isDefault = index == 0,
                source = ValueSource.EXTERNAL,
            )
        }

    /** "1234s" ou "12.5s" → segundos. */
    fun parseDuration(raw: String?): Long {
        val text = raw?.trim()?.removeSuffix("s") ?: return 0
        return text.toBigDecimalOrNull()?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong() ?: 0
    }

    /**
     * Sem tollInfo: a fonte não informou pedágio. Com tollInfo sem preço em BRL: há pedágio de valor desconhecido.
     * Nunca inventamos o valor.
     */
    fun toll(info: TollInfoDto?): TollInfo {
        if (info == null) return TollInfo.NotReported
        val brl = info.estimatedPrice.orEmpty().firstOrNull { it.currencyCode == "BRL" } ?: return TollInfo.PriceUnknown
        val units = brl.units?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val nanos = BigDecimal(brl.nanos ?: 0).movePointLeft(9)
        val amount = Money.of(units + nanos)
        return if (amount.isPositive) TollInfo.Estimated(amount) else TollInfo.NotReported
    }

    fun error(t: Throwable): DomainError = when (t) {
        is HttpException -> when (t.code()) {
            401, 403 -> DomainError.QuotaOrKeyRejected
            404 -> DomainError.RouteNotFound
            429 -> DomainError.QuotaOrKeyRejected
            else -> DomainError.Http(t.code())
        }
        is InterruptedIOException -> DomainError.Timeout
        is IOException -> DomainError.Offline
        is SerializationException -> DomainError.Unknown
        else -> DomainError.Unknown
    }
}
