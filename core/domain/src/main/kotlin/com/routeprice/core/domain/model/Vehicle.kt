@file:UseSerializers(BigDecimalSerializer::class)

package com.routeprice.core.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal

@Serializable
enum class VehicleType { HATCH, SEDAN, SUV, MINIVAN, VAN, PICKUP }

@Serializable
enum class FuelType(val unit: String) {
    GASOLINE("L"),
    ETHANOL("L"),
    DIESEL("L"),
    CNG("m³"),
    ELECTRIC("kWh"),
}

/**
 * Perfil de veículo com os custos que dependem da quilometragem.
 * [consumptionKmPerUnit] é km/L (ou km/m³, km/kWh conforme [fuelType]).
 * [rateMultiplier] multiplica as tarifas comerciais por km e por minuto (ex.: 1,2 para SUV).
 */
@Serializable
data class Vehicle(
    val id: String,
    val name: String,
    val type: VehicleType = VehicleType.SEDAN,
    val plate: String = "",
    val seats: Int = 4,
    val fuelType: FuelType = FuelType.GASOLINE,
    val consumptionKmPerUnit: BigDecimal = BigDecimal.ZERO,
    val maintenancePerKm: BigDecimal = BigDecimal.ZERO,
    val wearPerKm: BigDecimal = BigDecimal.ZERO,
    val rateMultiplier: BigDecimal = BigDecimal.ONE,
)

/** Cópia mínima do veículo guardada no orçamento, para o histórico não mudar se o perfil for editado. */
@Serializable
data class VehicleSnapshot(
    val id: String,
    val name: String,
    val type: VehicleType,
    val plate: String,
    val seats: Int,
)

fun Vehicle.snapshot() = VehicleSnapshot(id, name, type, plate, seats)
