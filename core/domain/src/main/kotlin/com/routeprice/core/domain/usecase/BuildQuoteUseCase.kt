package com.routeprice.core.domain.usecase

import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.RouteSummary
import com.routeprice.core.domain.model.TollInfo
import com.routeprice.core.domain.model.TripPricingInput
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.model.Vehicle
import com.routeprice.core.domain.model.snapshot
import com.routeprice.core.domain.pricing.PricingEngine
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

interface TimeProvider {
    fun nowMillis(): Long
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** Tudo que o motorista definiu para uma viagem antes do cálculo. */
data class QuoteRequest(
    val origin: PlaceRef,
    val destination: PlaceRef,
    val stops: List<PlaceRef>,
    val route: RouteSummary,
    val vehicle: Vehicle?,
    val waitingMinutes: Int = 0,
    val extraExpenses: Money = Money.ZERO,
    /** Valor de pedágio digitado pelo motorista; substitui o estimado pela rota. */
    val manualTolls: Money? = null,
    val deadheadKm: BigDecimal = BigDecimal.ZERO,
    val deadheadMinutes: Int = 0,
    val passengerName: String = "",
    val notes: String = "",
    val negotiatedPrice: Money? = null,
    val status: QuoteStatus = QuoteStatus.DRAFT,
    /** Quando presente, recalcula um orçamento existente mantendo id e data de criação. */
    val existing: TripQuote? = null,
)

class BuildQuoteUseCase @Inject constructor(
    private val engine: PricingEngine,
    private val time: TimeProvider,
) {
    operator fun invoke(request: QuoteRequest, settings: PricingSettings, driver: DriverProfile): TripQuote {
        val input = pricingInput(request)
        val result = engine.calculate(input, settings, request.vehicle)
        val now = time.nowMillis()
        val existing = request.existing
        return TripQuote(
            id = existing?.id ?: UUID.randomUUID().toString(),
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
            origin = request.origin,
            destination = request.destination,
            stops = request.stops,
            route = request.route,
            vehicle = request.vehicle?.snapshot(),
            driver = driver,
            input = input,
            settings = settings,
            result = result,
            negotiatedPrice = request.negotiatedPrice?.takeIf { it != result.suggested },
            validUntilMillis = now + settings.quoteValidityHours.coerceIn(1, MAX_VALIDITY_HOURS) * HOUR_MILLIS,
            status = request.status,
            passengerName = request.passengerName.trim(),
            notes = request.notes.trim(),
            isDemo = existing?.isDemo ?: false,
        )
    }

    fun pricingInput(request: QuoteRequest): TripPricingInput {
        val routeTolls = request.route.tolls
        val (tolls, source, unknown) = when {
            request.manualTolls != null -> Triple(request.manualTolls, ValueSource.DRIVER_INFORMED, false)
            routeTolls is TollInfo.Estimated -> Triple(routeTolls.amount, ValueSource.EXTERNAL, false)
            routeTolls is TollInfo.PriceUnknown -> Triple(Money.ZERO, ValueSource.DRIVER_INFORMED, true)
            else -> Triple(Money.ZERO, ValueSource.DRIVER_INFORMED, false)
        }
        return TripPricingInput(
            distanceMeters = request.route.distanceMeters,
            durationSeconds = request.route.durationSeconds,
            stopsCount = request.stops.size,
            waitingMinutes = request.waitingMinutes.coerceAtLeast(0),
            tolls = tolls,
            tollsSource = source,
            tollsPriceUnknown = unknown,
            extraExpenses = request.extraExpenses,
            deadheadKm = request.deadheadKm.max(BigDecimal.ZERO),
            deadheadMinutes = request.deadheadMinutes.coerceAtLeast(0),
            routeSource = request.route.source,
        )
    }

    private companion object {
        const val HOUR_MILLIS = 3_600_000L
        const val MAX_VALIDITY_HOURS = 24 * 30
    }
}

/** Cria uma cópia de um orçamento como novo rascunho (duplicação de viagem). */
class DuplicateQuoteUseCase @Inject constructor(private val time: TimeProvider) {
    operator fun invoke(source: TripQuote): TripQuote {
        val now = time.nowMillis()
        val validity = source.validUntilMillis - source.updatedAtMillis
        return source.copy(
            id = UUID.randomUUID().toString(),
            createdAtMillis = now,
            updatedAtMillis = now,
            validUntilMillis = now + validity.coerceAtLeast(0),
            status = QuoteStatus.DRAFT,
        )
    }
}
