package com.routeprice.core.domain.pricing

import com.routeprice.core.domain.model.CommercialParams
import com.routeprice.core.domain.model.CostParams
import com.routeprice.core.domain.model.FuelType
import com.routeprice.core.domain.model.LineKind
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PricingModel
import com.routeprice.core.domain.model.PricingResult
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.PricingWarning
import com.routeprice.core.domain.model.RoundingRule
import com.routeprice.core.domain.model.TripPricingInput
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Todos os valores esperados foram calculados à mão e estão documentados em docs/MOTOR_DE_PRECOS.md.
 */
class PricingEngineTest {

    private val engine = PricingEngine()

    private val car = Vehicle(
        id = "v1",
        name = "Sedan",
        fuelType = FuelType.GASOLINE,
        consumptionKmPerUnit = BigDecimal("10"),
        maintenancePerKm = BigDecimal("0.15"),
        wearPerKm = BigDecimal("0.10"),
    )

    private val settings = PricingSettings(
        model = PricingModel.COMMERCIAL,
        commercial = CommercialParams(
            ratePerKm = BigDecimal("2.50"),
            ratePerMinute = BigDecimal("0.40"),
            stopFee = BigDecimal("5.00"),
            waitingRatePerMinute = BigDecimal("0.50"),
            freeWaitingMinutes = 5,
            minimumFare = BigDecimal("25.00"),
        ),
        cost = CostParams(
            energyPrices = mapOf(FuelType.GASOLINE to BigDecimal("6.00")),
            driverHourlyRate = BigDecimal("30.00"),
            profitMarginPercent = BigDecimal("20"),
        ),
        rounding = RoundingRule.UP_TO_1_REAL,
        reviewedByDriver = true,
    )

    // 12,5 km e 25 min
    private val simpleTrip = TripPricingInput(distanceMeters = 12_500, durationSeconds = 1_500)

    private fun PricingResult.line(kind: LineKind) = lines.firstOrNull { it.kind == kind }
    private fun money(value: String) = Money.reais(value)

    @Test
    fun `viagem simples - modelo comercial`() {
        val r = engine.calculate(simpleTrip, settings, car)

        assertEquals(money("31.25"), r.commercial.lines.first { it.kind == LineKind.DISTANCE }.amount)
        assertEquals(money("10.00"), r.commercial.lines.first { it.kind == LineKind.TIME }.amount)
        assertEquals(money("41.25"), r.commercial.total)
        // arredondado para cima até R$ 1
        assertEquals(money("0.75"), r.line(LineKind.ROUNDING)!!.amount)
        assertEquals(money("42.00"), r.suggested)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun `viagem simples - custos operacionais e minimo sustentavel`() {
        val r = engine.calculate(simpleTrip, settings, car)
        val op = r.operatingCost.lines.associateBy { it.kind }

        assertEquals(money("7.50"), op.getValue(LineKind.ENERGY).amount) // 1,25 L × 6,00
        assertEquals(money("1.88"), op.getValue(LineKind.MAINTENANCE).amount) // 1,875 → 1,88
        assertEquals(money("1.25"), op.getValue(LineKind.WEAR).amount)
        assertEquals(money("12.50"), op.getValue(LineKind.DRIVER_TIME).amount) // 25/60 h × 30
        assertEquals(money("23.13"), r.sustainableMinimum)
        // 23,13 / 0,8 = 28,9125 → 28,91 ; margem = 5,78
        assertEquals(money("5.78"), op.getValue(LineKind.PROFIT_MARGIN).amount)
        assertEquals(money("28.91"), r.operatingCost.total)
    }

    @Test
    fun `modelo de custos sem arredondamento`() {
        val r = engine.calculate(
            simpleTrip,
            settings.copy(model = PricingModel.OPERATING_COST, rounding = RoundingRule.NONE),
            car,
        )
        assertEquals(PricingModel.OPERATING_COST, r.selectedModel)
        assertEquals(money("28.91"), r.suggested)
        assertNull(r.line(LineKind.ROUNDING))
        assertNull(r.line(LineKind.SUSTAINABLE_COMPLEMENT))
    }

    @Test
    fun `multiplas paradas, espera, pedagio e despesas`() {
        val input = TripPricingInput(
            distanceMeters = 40_000,
            durationSeconds = 3_600,
            stopsCount = 3,
            waitingMinutes = 12,
            tolls = money("12.40"),
            tollsSource = ValueSource.EXTERNAL,
            extraExpenses = money("8.00"),
        )
        val r = engine.calculate(input, settings.copy(commercial = settings.commercial.copy(adjustmentPercent = BigDecimal("10"))), car)

        assertEquals(money("100.00"), r.line(LineKind.DISTANCE)!!.amount)
        assertEquals(money("24.00"), r.line(LineKind.TIME)!!.amount)
        assertEquals(money("15.00"), r.line(LineKind.STOPS)!!.amount)
        assertEquals(money("3.50"), r.line(LineKind.WAITING)!!.amount) // 12 − 5 grátis = 7 × 0,50
        assertEquals(money("14.25"), r.line(LineKind.COMMERCIAL_ADJUSTMENT)!!.amount) // 10% de 142,50
        assertEquals(money("12.40"), r.line(LineKind.TOLLS)!!.amount)
        assertEquals(ValueSource.EXTERNAL, r.line(LineKind.TOLLS)!!.source)
        assertEquals(money("8.00"), r.line(LineKind.EXTRA_EXPENSES)!!.amount)
        assertEquals(money("177.15"), r.commercial.total)
        assertEquals(money("178.00"), r.suggested)
    }

    @Test
    fun `pedagio entra uma unica vez e sem margem`() {
        val withToll = simpleTrip.copy(tolls = money("10.00"))
        val r = engine.calculate(withToll, settings.copy(model = PricingModel.OPERATING_COST, rounding = RoundingRule.NONE), car)
        val base = engine.calculate(simpleTrip, settings.copy(model = PricingModel.OPERATING_COST, rounding = RoundingRule.NONE), car)

        assertEquals(1, r.lines.count { it.kind == LineKind.TOLLS })
        assertEquals(base.suggested + money("10.00"), r.suggested)
        assertEquals(base.sustainableMinimum + money("10.00"), r.sustainableMinimum)
        assertEquals(base.operatingCost.lines.first { it.kind == LineKind.PROFIT_MARGIN }.amount,
            r.operatingCost.lines.first { it.kind == LineKind.PROFIT_MARGIN }.amount)
    }

    @Test
    fun `combustivel nao entra no modelo comercial`() {
        val r = engine.calculate(simpleTrip, settings, car)
        val kinds = r.commercial.lines.map { it.kind }
        assertFalse(LineKind.ENERGY in kinds)
        assertFalse(LineKind.MAINTENANCE in kinds)
        assertFalse(LineKind.WEAR in kinds)
    }

    @Test
    fun `preco comercial abaixo do sustentavel recebe complemento`() {
        val cheap = settings.copy(
            commercial = settings.commercial.copy(
                ratePerKm = BigDecimal("1.00"),
                ratePerMinute = BigDecimal("0.10"),
                minimumFare = BigDecimal("10.00"),
            ),
            rounding = RoundingRule.NONE,
        )
        val r = engine.calculate(TripPricingInput(distanceMeters = 5_000, durationSeconds = 1_200), cheap, car)

        // 5,00 + 2,00 = 7,00 → tarifa mínima 10,00 (complemento 3,00)
        assertEquals(money("3.00"), r.commercial.lines.first { it.kind == LineKind.MINIMUM_FARE_COMPLEMENT }.amount)
        assertEquals(money("10.00"), r.commercial.total)
        // custos: 3,00 + 0,75 + 0,50 + 10,00 = 14,25
        assertEquals(money("14.25"), r.sustainableMinimum)
        assertEquals(money("4.25"), r.line(LineKind.SUSTAINABLE_COMPLEMENT)!!.amount)
        assertEquals(money("14.25"), r.suggested)
        assertTrue(PricingWarning.COMMERCIAL_BELOW_SUSTAINABLE in r.warnings)
    }

    @Test
    fun `alteracao do preco do combustivel`() {
        val pricier = settings.copy(cost = settings.cost.copy(energyPrices = mapOf(FuelType.GASOLINE to BigDecimal("7.00"))))
        val r = engine.calculate(simpleTrip, pricier, car)
        assertEquals(money("8.75"), r.operatingCost.lines.first { it.kind == LineKind.ENERGY }.amount) // 1,25 × 7
        assertEquals(money("24.38"), r.sustainableMinimum)
    }

    @Test
    fun `alteracao do consumo`() {
        val thirsty = car.copy(consumptionKmPerUnit = BigDecimal("8"))
        val r = engine.calculate(simpleTrip, settings, thirsty)
        assertEquals(money("9.38"), r.operatingCost.lines.first { it.kind == LineKind.ENERGY }.amount) // 1,5625 × 6 = 9,375
        assertEquals(BigDecimal("1.56"), r.operatingCost.lines.first { it.kind == LineKind.ENERGY }.quantity)
    }

    @Test
    fun `alteracao da margem e limite de 90 por cento`() {
        val op = settings.copy(model = PricingModel.OPERATING_COST, rounding = RoundingRule.NONE)
        val zero = engine.calculate(simpleTrip, op.copy(cost = op.cost.copy(profitMarginPercent = BigDecimal.ZERO)), car)
        assertEquals(zero.sustainableMinimum, zero.suggested)

        val excessive = engine.calculate(simpleTrip, op.copy(cost = op.cost.copy(profitMarginPercent = BigDecimal("95"))), car)
        assertTrue(PricingWarning.MARGIN_CLAMPED in excessive.warnings)
        assertEquals(money("231.30"), excessive.suggested) // 23,13 / 0,10
    }

    @Test
    fun `taxa de espera respeita tolerancia gratuita`() {
        val withinFree = engine.calculate(simpleTrip.copy(waitingMinutes = 5), settings, car)
        assertNull(withinFree.commercial.lines.firstOrNull { it.kind == LineKind.WAITING })

        val r = engine.calculate(simpleTrip.copy(waitingMinutes = 20), settings, car)
        assertEquals(money("7.50"), r.commercial.lines.first { it.kind == LineKind.WAITING }.amount) // 15 × 0,50
        // espera também conta como tempo no modelo de custos: (25 + 20) / 60 × 30 = 22,50
        assertEquals(money("22.50"), r.operatingCost.lines.first { it.kind == LineKind.DRIVER_TIME }.amount)
    }

    @Test
    fun `deslocamento sem passageiro so entra nos custos`() {
        val r = engine.calculate(simpleTrip.copy(deadheadKm = BigDecimal("10"), deadheadMinutes = 15), settings, car)
        assertEquals(money("41.25"), r.commercial.total)
        val op = r.operatingCost.lines.associateBy { it.kind }
        assertEquals(money("13.50"), op.getValue(LineKind.ENERGY).amount) // 22,5 km / 10 × 6
        assertEquals(money("3.38"), op.getValue(LineKind.MAINTENANCE).amount) // 3,375
        assertEquals(money("2.25"), op.getValue(LineKind.WEAR).amount)
        assertEquals(money("20.00"), op.getValue(LineKind.DRIVER_TIME).amount) // 40 min
        assertEquals(money("39.13"), r.sustainableMinimum)
    }

    @Test
    fun `multiplicador do veiculo aplica nas tarifas comerciais`() {
        val suv = car.copy(rateMultiplier = BigDecimal("1.2"))
        val r = engine.calculate(simpleTrip, settings, suv)
        assertEquals(money("37.50"), r.commercial.lines.first { it.kind == LineKind.DISTANCE }.amount)
        assertEquals(money("12.00"), r.commercial.lines.first { it.kind == LineKind.TIME }.amount)
    }

    @Test
    fun `ajuste comercial negativo`() {
        val r = engine.calculate(
            simpleTrip,
            settings.copy(commercial = settings.commercial.copy(adjustmentPercent = BigDecimal("-10")), rounding = RoundingRule.NONE),
            car,
        )
        assertEquals(money("-4.13"), r.commercial.lines.first { it.kind == LineKind.COMMERCIAL_ADJUSTMENT }.amount)
        assertEquals(money("37.12"), r.suggested)
    }

    @Test
    fun `minutos fracionados`() {
        val r = engine.calculate(simpleTrip.copy(durationSeconds = 1_530), settings, car)
        assertEquals(money("10.20"), r.commercial.lines.first { it.kind == LineKind.TIME }.amount) // 25,5 × 0,40
    }

    @Test
    fun `veiculo eletrico usa preco por kWh`() {
        val ev = car.copy(fuelType = FuelType.ELECTRIC, consumptionKmPerUnit = BigDecimal("6"))
        val s = settings.copy(cost = settings.cost.copy(energyPrices = mapOf(FuelType.ELECTRIC to BigDecimal("0.90"))))
        val r = engine.calculate(simpleTrip, s, ev)
        assertEquals(money("1.88"), r.operatingCost.lines.first { it.kind == LineKind.ENERGY }.amount) // 2,0833 × 0,90
    }

    @Test
    fun `sem veiculo e sem preco de combustivel geram avisos`() {
        val noVehicle = engine.calculate(simpleTrip, settings, null)
        assertTrue(PricingWarning.NO_VEHICLE in noVehicle.warnings)
        assertEquals(money("12.50"), noVehicle.sustainableMinimum) // só o tempo

        val noPrice = engine.calculate(simpleTrip, settings.copy(cost = settings.cost.copy(energyPrices = emptyMap())), car)
        assertTrue(PricingWarning.MISSING_ENERGY_PRICE in noPrice.warnings)
        assertNull(noPrice.operatingCost.lines.firstOrNull { it.kind == LineKind.ENERGY })
    }

    @Test
    fun `parametros nao revisados e pedagio sem preco sao sinalizados`() {
        val r = engine.calculate(
            simpleTrip.copy(tollsPriceUnknown = true),
            settings.copy(reviewedByDriver = false),
            car,
        )
        assertTrue(PricingWarning.PARAMETERS_NOT_REVIEWED in r.warnings)
        assertTrue(PricingWarning.TOLLS_PRICE_UNKNOWN in r.warnings)
    }

    @Test
    fun `distancia zero aplica tarifa minima`() {
        val r = engine.calculate(TripPricingInput(0, 0), settings, car)
        assertEquals(money("25.00"), r.suggested)
        assertTrue(PricingWarning.ZERO_DISTANCE in r.warnings)
    }

    @Test
    fun `composicao sempre fecha com o total`() {
        val inputs = listOf(
            simpleTrip,
            TripPricingInput(33_333, 2_777, stopsCount = 2, waitingMinutes = 9, tolls = money("7.77")),
            TripPricingInput(1_234, 333, deadheadKm = BigDecimal("3.3")),
        )
        for (model in PricingModel.entries) {
            for (rule in RoundingRule.entries) {
                for (input in inputs) {
                    val r = engine.calculate(input, settings.copy(model = model, rounding = rule), car)
                    assertEquals(Money.sum(r.lines.map { it.amount }), r.suggested)
                    assertTrue(r.suggested >= r.sustainableMinimum)
                    assertEquals(0L, r.suggested.cents % rule.stepCents)
                }
            }
        }
    }

    @Test
    fun `arredondamento para cima`() {
        assertEquals(money("42.00"), PricingEngine.roundUp(money("41.01"), RoundingRule.UP_TO_1_REAL))
        assertEquals(money("41.00"), PricingEngine.roundUp(money("41.00"), RoundingRule.UP_TO_1_REAL))
        assertEquals(money("41.50"), PricingEngine.roundUp(money("41.01"), RoundingRule.UP_TO_50_CENTS))
        assertEquals(money("45.00"), PricingEngine.roundUp(money("41.01"), RoundingRule.UP_TO_5_REAIS))
        assertEquals(money("41.01"), PricingEngine.roundUp(money("41.01"), RoundingRule.NONE))
    }
}
