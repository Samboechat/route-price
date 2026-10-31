package com.routeprice.core.domain.model

import kotlinx.serialization.Serializable

/** De onde veio cada número do orçamento. Mostrado ao passageiro para separar fato de estimativa. */
@Serializable
enum class ValueSource {
    /** Confirmado por fonte externa (ex.: rota e pedágio do Google Routes). */
    EXTERNAL,

    /** Informado ou configurado pelo motorista. */
    DRIVER_INFORMED,

    /** Calculado pelo app a partir de outros dados. */
    ESTIMATED,
}

@Serializable
sealed interface TollInfo {
    /** A fonte da rota não informou pedágios. */
    @Serializable
    data object NotReported : TollInfo

    /** A rota passa por pedágio, mas a fonte não informou o valor. */
    @Serializable
    data object PriceUnknown : TollInfo

    /** Valor estimado pela fonte externa. */
    @Serializable
    data class Estimated(val amount: Money) : TollInfo
}

@Serializable
data class RouteLeg(val distanceMeters: Long, val durationSeconds: Long)

/** Uma alternativa de rota percorrida (nunca distância em linha reta). */
@Serializable
data class RouteOption(
    val id: String,
    val distanceMeters: Long,
    val durationSeconds: Long,
    val encodedPolyline: String?,
    val legs: List<RouteLeg> = emptyList(),
    val tolls: TollInfo = TollInfo.NotReported,
    val description: String = "",
    val isDefault: Boolean = false,
    val source: ValueSource = ValueSource.EXTERNAL,
)
