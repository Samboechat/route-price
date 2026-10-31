@file:UseSerializers(BigDecimalSerializer::class)

package com.routeprice.core.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal

@Serializable
enum class PricingModel {
    /** Modelo A: tarifas comerciais por km, minuto, parada e espera. */
    COMMERCIAL,

    /** Modelo B: custos operacionais reais + margem de lucro. */
    OPERATING_COST,
}

@Serializable
enum class RoundingRule(val stepCents: Long) {
    NONE(1),
    UP_TO_50_CENTS(50),
    UP_TO_1_REAL(100),
    UP_TO_5_REAIS(500),
}

/** Parâmetros do Modelo A. Valores em reais. */
@Serializable
data class CommercialParams(
    val ratePerKm: BigDecimal = BigDecimal("2.50"),
    val ratePerMinute: BigDecimal = BigDecimal("0.40"),
    val stopFee: BigDecimal = BigDecimal("5.00"),
    val waitingRatePerMinute: BigDecimal = BigDecimal("0.50"),
    val freeWaitingMinutes: Int = 5,
    val minimumFare: BigDecimal = BigDecimal("25.00"),
    /** Ajuste comercial em %, aplicado sobre o serviço (positivo ou negativo). */
    val adjustmentPercent: BigDecimal = BigDecimal.ZERO,
)

/** Parâmetros do Modelo B que não dependem do veículo. */
@Serializable
data class CostParams(
    /** Preço por unidade de energia, por tipo de combustível. Sem valor padrão: o motorista informa. */
    val energyPrices: Map<FuelType, BigDecimal> = emptyMap(),
    /** Quanto vale uma hora de trabalho do motorista (custo do tempo). */
    val driverHourlyRate: BigDecimal = BigDecimal("30.00"),
    /** Margem de lucro sobre o preço do serviço, em % (0 a 90). */
    val profitMarginPercent: BigDecimal = BigDecimal("20"),
)

@Serializable
data class PricingSettings(
    val model: PricingModel = PricingModel.COMMERCIAL,
    val commercial: CommercialParams = CommercialParams(),
    val cost: CostParams = CostParams(),
    val rounding: RoundingRule = RoundingRule.UP_TO_1_REAL,
    val quoteValidityHours: Int = 24,
    /** Falso enquanto o motorista não revisar os parâmetros iniciais de exemplo. */
    val reviewedByDriver: Boolean = false,
)

/** Dados de uma viagem específica que entram no cálculo. */
@Serializable
data class TripPricingInput(
    val distanceMeters: Long,
    val durationSeconds: Long,
    val stopsCount: Int = 0,
    val waitingMinutes: Int = 0,
    val tolls: Money = Money.ZERO,
    val tollsSource: ValueSource = ValueSource.DRIVER_INFORMED,
    /** Pedágio existe na rota mas valor não foi confirmado nem informado. */
    val tollsPriceUnknown: Boolean = false,
    val extraExpenses: Money = Money.ZERO,
    /** Deslocamento sem passageiro (até o embarque e/ou retorno), em km. Só entra nos custos. */
    val deadheadKm: BigDecimal = BigDecimal.ZERO,
    val deadheadMinutes: Int = 0,
    /** Origem da distância e do tempo: EXTERNAL (Google Routes) ou DRIVER_INFORMED (manual). */
    val routeSource: ValueSource = ValueSource.EXTERNAL,
) {
    init {
        require(distanceMeters >= 0) { "distância negativa" }
        require(durationSeconds >= 0) { "duração negativa" }
        require(stopsCount >= 0) { "paradas negativas" }
        require(waitingMinutes >= 0) { "espera negativa" }
        require(deadheadKm >= BigDecimal.ZERO) { "deslocamento negativo" }
        require(deadheadMinutes >= 0) { "tempo de deslocamento negativo" }
        require(!tolls.isNegative()) { "pedágio negativo" }
        require(!extraExpenses.isNegative()) { "despesa negativa" }
    }
}

private fun Money.isNegative() = cents < 0

@Serializable
enum class LineKind {
    DISTANCE,
    TIME,
    STOPS,
    WAITING,
    COMMERCIAL_ADJUSTMENT,
    MINIMUM_FARE_COMPLEMENT,
    ENERGY,
    MAINTENANCE,
    WEAR,
    DRIVER_TIME,
    PROFIT_MARGIN,
    TOLLS,
    EXTRA_EXPENSES,
    SUSTAINABLE_COMPLEMENT,
    ROUNDING,
}

@Serializable
enum class QuantityUnit { KM, MINUTE, HOUR, STOP, ENERGY_UNIT, PERCENT }

/**
 * Uma linha da composição do preço. [quantity] × [rate] explica o cálculo quando aplicável.
 */
@Serializable
data class PriceLine(
    val kind: LineKind,
    val amount: Money,
    val source: ValueSource,
    val quantity: BigDecimal? = null,
    val unit: QuantityUnit? = null,
    val rate: BigDecimal? = null,
)

@Serializable
data class ModelBreakdown(val model: PricingModel, val lines: List<PriceLine>) {
    val total: Money get() = Money.sum(lines.map { it.amount })
}

@Serializable
enum class PricingWarning {
    NO_VEHICLE,
    MISSING_ENERGY_PRICE,
    MISSING_CONSUMPTION,
    MARGIN_CLAMPED,
    COMMERCIAL_BELOW_SUSTAINABLE,
    TOLLS_PRICE_UNKNOWN,
    ZERO_DISTANCE,
    PARAMETERS_NOT_REVIEWED,
}

@Serializable
data class PricingResult(
    val selectedModel: PricingModel,
    val commercial: ModelBreakdown,
    val operatingCost: ModelBreakdown,
    /** Custos totais com margem zero: abaixo disso o motorista paga para trabalhar. */
    val sustainableMinimum: Money,
    /** Linhas finais do preço sugerido, incluindo complemento e arredondamento. */
    val lines: List<PriceLine>,
    val warnings: Set<PricingWarning>,
) {
    val suggested: Money get() = Money.sum(lines.map { it.amount })
}
