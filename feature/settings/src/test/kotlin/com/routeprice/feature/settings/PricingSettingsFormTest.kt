package com.routeprice.feature.settings

import com.routeprice.core.domain.model.FuelType
import com.routeprice.core.domain.model.PricingModel
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.RoundingRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PricingSettingsFormTest {

    @Test
    fun `formulario ida e volta preserva os parametros`() {
        val original = PricingSettings(
            model = PricingModel.OPERATING_COST,
            rounding = RoundingRule.UP_TO_5_REAIS,
            quoteValidityHours = 48,
            reviewedByDriver = true,
        ).let { it.copy(cost = it.cost.copy(energyPrices = mapOf(FuelType.ETHANOL to BigDecimal("4.39")))) }
        val (parsed, errors) = PricingSettingsViewModel.parse(PricingSettingsViewModel.toForm(original))
        assertTrue(errors.isEmpty())
        assertEquals(original.model, parsed!!.model)
        assertEquals(0, original.commercial.ratePerKm.compareTo(parsed.commercial.ratePerKm))
        assertEquals(0, BigDecimal("4.39").compareTo(parsed.cost.energyPrices.getValue(FuelType.ETHANOL)))
        assertEquals(48, parsed.quoteValidityHours)
        assertEquals(RoundingRule.UP_TO_5_REAIS, parsed.rounding)
    }

    @Test
    fun `aceita virgula e rejeita valores invalidos`() {
        val base = PricingSettingsViewModel.toForm(PricingSettings())
        val (ok, _) = PricingSettingsViewModel.parse(base.copy(ratePerKm = "3,15", adjustment = "-5"))
        assertEquals(0, BigDecimal("3.15").compareTo(ok!!.commercial.ratePerKm))
        assertEquals(0, BigDecimal("-5").compareTo(ok.commercial.adjustmentPercent))

        val (bad, errors) = PricingSettingsViewModel.parse(base.copy(ratePerKm = "-1", margin = "95", validityHours = "0"))
        assertNull(bad)
        assertTrue(errors.keys.containsAll(listOf("ratePerKm", "margin", "validity")))
    }

    @Test
    fun `combustivel vazio nao entra na tabela de precos`() {
        val form = PricingSettingsViewModel.toForm(PricingSettings()).copy(energyPrices = mapOf(FuelType.GASOLINE to "", FuelType.DIESEL to "6,09"))
        val (parsed, _) = PricingSettingsViewModel.parse(form)
        assertEquals(setOf(FuelType.DIESEL), parsed!!.cost.energyPrices.keys)
    }
}
