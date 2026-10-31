package com.routeprice.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DriverProfile(
    val name: String = "",
    val phone: String = "",
    val city: String = "",
    val vehicleDescriptionForPassenger: String = "",
) {
    val isComplete: Boolean get() = name.isNotBlank() && phone.isNotBlank()
}

@Serializable
enum class QuoteStatus { DRAFT, SENT, ACCEPTED, DECLINED }

/** Resumo da rota escolhida, guardado junto ao orçamento. */
@Serializable
data class RouteSummary(
    val distanceMeters: Long,
    val durationSeconds: Long,
    val encodedPolyline: String? = null,
    val source: ValueSource,
    val tolls: TollInfo = TollInfo.NotReported,
    val description: String = "",
)

/**
 * Orçamento completo e autocontido: guarda cópias da rota, do veículo, dos parâmetros e do resultado,
 * para que o histórico continue fiel mesmo que as configurações mudem depois.
 */
@Serializable
data class TripQuote(
    val id: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val origin: PlaceRef,
    val destination: PlaceRef,
    val stops: List<PlaceRef> = emptyList(),
    val route: RouteSummary,
    val vehicle: VehicleSnapshot? = null,
    val driver: DriverProfile = DriverProfile(),
    val input: TripPricingInput,
    val settings: PricingSettings,
    val result: PricingResult,
    /** Preço combinado com o passageiro, quando diferente do sugerido. */
    val negotiatedPrice: Money? = null,
    val validUntilMillis: Long,
    val status: QuoteStatus = QuoteStatus.DRAFT,
    val passengerName: String = "",
    val notes: String = "",
    /** Orçamento criado a partir de dados de demonstração. */
    val isDemo: Boolean = false,
) {
    val suggestedPrice: Money get() = result.suggested
    val finalPrice: Money get() = negotiatedPrice ?: result.suggested
    val negotiation: Negotiation get() = Negotiation(result.suggested, finalPrice, result.sustainableMinimum)
    fun isExpired(nowMillis: Long): Boolean = nowMillis > validUntilMillis
}

/** Diferença entre o preço calculado e o negociado. */
data class Negotiation(val calculated: Money, val negotiated: Money, val sustainableMinimum: Money) {
    val difference: Money get() = negotiated - calculated
    val hasAdjustment: Boolean get() = !difference.isZero
    val isBelowSustainable: Boolean get() = negotiated < sustainableMinimum

    /** Variação percentual com uma casa decimal; nulo se o calculado for zero. */
    val differencePercent: Double?
        get() = if (calculated.isZero) {
            null
        } else {
            Math.round(difference.cents * PERCENT_SCALE / calculated.cents) / DECIMAL_SCALE
        }

    private companion object {
        const val PERCENT_SCALE = 1000.0
        const val DECIMAL_SCALE = 10.0
    }
}

@Serializable
enum class ThemePreference { SYSTEM, DARK, LIGHT }

@Serializable
data class AppPreferences(
    val theme: ThemePreference = ThemePreference.DARK,
    val selectedVehicleId: String? = null,
    val onboardingCompleted: Boolean = false,
)
