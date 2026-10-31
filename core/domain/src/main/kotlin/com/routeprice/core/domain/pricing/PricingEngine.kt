package com.routeprice.core.domain.pricing

import com.routeprice.core.domain.model.CommercialParams
import com.routeprice.core.domain.model.CostParams
import com.routeprice.core.domain.model.LineKind
import com.routeprice.core.domain.model.ModelBreakdown
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PriceLine
import com.routeprice.core.domain.model.PricingModel
import com.routeprice.core.domain.model.PricingResult
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.PricingWarning
import com.routeprice.core.domain.model.QuantityUnit
import com.routeprice.core.domain.model.RoundingRule
import com.routeprice.core.domain.model.TripPricingInput
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.model.Vehicle
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Motor de precificação, independente de interface e de Android.
 *
 * Regras gerais (detalhadas em docs/MOTOR_DE_PRECOS.md):
 * - Cada linha é arredondada para centavos (meio para cima) e o total é a soma das linhas,
 *   então a composição mostrada ao passageiro sempre fecha com o valor final.
 * - Pedágios e despesas extras são repasse: entram uma única vez, no fim, sem margem nem ajuste.
 * - Combustível, manutenção e desgaste só existem no Modelo B; as tarifas por km do Modelo A
 *   já representam esses custos para o motorista, então os dois nunca são somados.
 * - O preço sugerido nunca fica abaixo do preço mínimo sustentável (custos com margem zero).
 */
class PricingEngine @Inject constructor() {

    fun calculate(input: TripPricingInput, settings: PricingSettings, vehicle: Vehicle?): PricingResult {
        val warnings = linkedSetOf<PricingWarning>()
        if (!settings.reviewedByDriver) warnings += PricingWarning.PARAMETERS_NOT_REVIEWED
        if (input.distanceMeters == 0L) warnings += PricingWarning.ZERO_DISTANCE
        if (input.tollsPriceUnknown) warnings += PricingWarning.TOLLS_PRICE_UNKNOWN

        val passThrough = passThroughLines(input)
        val commercial = commercialBreakdown(input, settings.commercial, vehicle, passThrough)
        val operating = operatingBreakdown(input, settings.cost, vehicle, passThrough, warnings)
        val sustainableMinimum = Money.sum(
            operating.lines.filter { it.kind != LineKind.PROFIT_MARGIN }.map { it.amount },
        )

        val selected = if (settings.model == PricingModel.COMMERCIAL) commercial else operating
        val lines = selected.lines.toMutableList()
        var total = selected.total

        if (total < sustainableMinimum) {
            if (settings.model == PricingModel.COMMERCIAL) warnings += PricingWarning.COMMERCIAL_BELOW_SUSTAINABLE
            lines += PriceLine(LineKind.SUSTAINABLE_COMPLEMENT, sustainableMinimum - total, ValueSource.ESTIMATED)
            total = sustainableMinimum
        }

        val rounded = roundUp(total, settings.rounding)
        if (rounded != total) {
            lines += PriceLine(LineKind.ROUNDING, rounded - total, ValueSource.DRIVER_INFORMED)
        }

        return PricingResult(
            selectedModel = settings.model,
            commercial = commercial,
            operatingCost = operating,
            sustainableMinimum = sustainableMinimum,
            lines = lines,
            warnings = warnings,
        )
    }

    private fun commercialBreakdown(
        input: TripPricingInput,
        params: CommercialParams,
        vehicle: Vehicle?,
        passThrough: List<PriceLine>,
    ): ModelBreakdown {
        val multiplier = vehicle?.rateMultiplier?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
        val km = input.kilometers()
        val minutes = input.tripMinutes()
        val kmRate = params.ratePerKm * multiplier
        val minuteRate = params.ratePerMinute * multiplier

        val service = mutableListOf(
            PriceLine(LineKind.DISTANCE, money(km * kmRate), DRIVER, km, QuantityUnit.KM, kmRate),
            PriceLine(
                LineKind.TIME,
                // uma única divisão no fim evita perda de precisão (segundos × R$/min ÷ 60)
                money((BigDecimal(input.durationSeconds) * minuteRate).divide(SIXTY, MC)),
                DRIVER,
                minutes.setScale(2, RoundingMode.HALF_UP),
                QuantityUnit.MINUTE,
                minuteRate,
            ),
        )
        if (input.stopsCount > 0) {
            val stops = BigDecimal(input.stopsCount)
            service += PriceLine(
                LineKind.STOPS, money(stops * params.stopFee), DRIVER, stops, QuantityUnit.STOP, params.stopFee,
            )
        }
        val billableWaiting = (input.waitingMinutes - params.freeWaitingMinutes.coerceAtLeast(0)).coerceAtLeast(0)
        if (billableWaiting > 0) {
            val waiting = BigDecimal(billableWaiting)
            service += PriceLine(
                LineKind.WAITING,
                money(waiting * params.waitingRatePerMinute),
                DRIVER,
                waiting,
                QuantityUnit.MINUTE,
                params.waitingRatePerMinute,
            )
        }

        val serviceTotal = Money.sum(service.map { it.amount })
        val adjustmentPercent = params.adjustmentPercent.coerceIn(MIN_ADJUSTMENT, MAX_ADJUSTMENT)
        if (adjustmentPercent.signum() != 0) {
            val adjustment = money(serviceTotal.toBigDecimal() * adjustmentPercent.divide(HUNDRED))
            if (!adjustment.isZero) {
                service += PriceLine(
                    LineKind.COMMERCIAL_ADJUSTMENT, adjustment, DRIVER, adjustmentPercent, QuantityUnit.PERCENT,
                )
            }
        }

        val adjustedTotal = Money.sum(service.map { it.amount })
        val minimumFare = money(params.minimumFare.max(BigDecimal.ZERO))
        if (adjustedTotal < minimumFare) {
            service += PriceLine(LineKind.MINIMUM_FARE_COMPLEMENT, minimumFare - adjustedTotal, DRIVER)
        }
        return ModelBreakdown(PricingModel.COMMERCIAL, service + passThrough)
    }

    private fun operatingBreakdown(
        input: TripPricingInput,
        params: CostParams,
        vehicle: Vehicle?,
        passThrough: List<PriceLine>,
        warnings: MutableSet<PricingWarning>,
    ): ModelBreakdown {
        val totalKm = input.kilometers() + input.deadheadKm
        val costs = mutableListOf<PriceLine>()

        if (vehicle == null) {
            warnings += PricingWarning.NO_VEHICLE
        } else {
            energyLine(totalKm, params, vehicle, warnings)?.let { costs += it }
            if (vehicle.maintenancePerKm > BigDecimal.ZERO) {
                costs += PriceLine(
                    LineKind.MAINTENANCE,
                    money(totalKm * vehicle.maintenancePerKm),
                    ESTIMATED,
                    totalKm,
                    QuantityUnit.KM,
                    vehicle.maintenancePerKm,
                )
            }
            if (vehicle.wearPerKm > BigDecimal.ZERO) {
                costs += PriceLine(
                    LineKind.WEAR, money(totalKm * vehicle.wearPerKm), ESTIMATED, totalKm, QuantityUnit.KM,
                    vehicle.wearPerKm,
                )
            }
        }

        val totalSeconds = BigDecimal(input.durationSeconds) +
            BigDecimal(input.waitingMinutes + input.deadheadMinutes) * SIXTY
        val hours = totalSeconds.divide(SECONDS_PER_HOUR, MC)
        if (params.driverHourlyRate > BigDecimal.ZERO) {
            costs += PriceLine(
                LineKind.DRIVER_TIME,
                money((totalSeconds * params.driverHourlyRate).divide(SECONDS_PER_HOUR, MC)),
                DRIVER,
                hours.setScale(2, RoundingMode.HALF_UP),
                QuantityUnit.HOUR,
                params.driverHourlyRate,
            )
        }

        val costTotal = Money.sum(costs.map { it.amount })
        val requestedMargin = params.profitMarginPercent
        val margin = requestedMargin.coerceIn(BigDecimal.ZERO, MAX_MARGIN)
        if (margin.compareTo(requestedMargin) != 0) warnings += PricingWarning.MARGIN_CLAMPED
        if (margin.signum() > 0 && costTotal.isPositive) {
            // Margem sobre o preço: preço = custo / (1 − m). Assim o lucro é m% do preço do serviço.
            val divisor = BigDecimal.ONE - margin.divide(HUNDRED)
            val servicePrice = money(costTotal.toBigDecimal().divide(divisor, MC))
            costs += PriceLine(
                LineKind.PROFIT_MARGIN, servicePrice - costTotal, DRIVER, margin, QuantityUnit.PERCENT,
            )
        }
        return ModelBreakdown(PricingModel.OPERATING_COST, costs + passThrough)
    }

    private fun energyLine(
        totalKm: BigDecimal,
        params: CostParams,
        vehicle: Vehicle,
        warnings: MutableSet<PricingWarning>,
    ): PriceLine? {
        val price = params.energyPrices[vehicle.fuelType]
        val consumption = vehicle.consumptionKmPerUnit
        if (consumption <= BigDecimal.ZERO) warnings += PricingWarning.MISSING_CONSUMPTION
        if (price == null || price <= BigDecimal.ZERO) warnings += PricingWarning.MISSING_ENERGY_PRICE
        if (consumption <= BigDecimal.ZERO || price == null || price <= BigDecimal.ZERO) return null
        val units = totalKm.divide(consumption, MC)
        return PriceLine(
            LineKind.ENERGY,
            money((totalKm * price).divide(consumption, MC)),
            ESTIMATED,
            units.setScale(2, RoundingMode.HALF_UP),
            QuantityUnit.ENERGY_UNIT,
            price,
        )
    }

    private fun passThroughLines(input: TripPricingInput): List<PriceLine> = buildList {
        if (input.tolls.isPositive) add(PriceLine(LineKind.TOLLS, input.tolls, input.tollsSource))
        if (input.extraExpenses.isPositive) add(PriceLine(LineKind.EXTRA_EXPENSES, input.extraExpenses, DRIVER))
    }

    companion object {
        private val MC = MathContext.DECIMAL64
        private val HUNDRED = BigDecimal(100)
        private val SIXTY = BigDecimal(60)
        private val SECONDS_PER_HOUR = BigDecimal(3600)
        private val THOUSAND = BigDecimal(1000)
        private val MAX_MARGIN = BigDecimal(90)
        private val MIN_ADJUSTMENT = BigDecimal(-90)
        private val MAX_ADJUSTMENT = BigDecimal(300)
        private val DRIVER = ValueSource.DRIVER_INFORMED
        private val ESTIMATED = ValueSource.ESTIMATED

        private fun money(value: BigDecimal) = Money.of(value)

        private fun TripPricingInput.kilometers(): BigDecimal = BigDecimal(distanceMeters).divide(THOUSAND)

        private fun TripPricingInput.tripMinutes(): BigDecimal = BigDecimal(durationSeconds).divide(SIXTY, MC)

        /** Arredonda para cima até o próximo múltiplo do passo da regra. */
        fun roundUp(value: Money, rule: RoundingRule): Money {
            val step = rule.stepCents
            if (step <= 1 || value.cents <= 0 || value.cents % step == 0L) return value
            return Money((value.cents / step + 1) * step)
        }
    }
}
