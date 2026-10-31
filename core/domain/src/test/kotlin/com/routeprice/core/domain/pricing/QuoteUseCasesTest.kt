package com.routeprice.core.domain.pricing

import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.RouteSummary
import com.routeprice.core.domain.model.TollInfo
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.usecase.BuildQuoteUseCase
import com.routeprice.core.domain.usecase.DuplicateQuoteUseCase
import com.routeprice.core.domain.usecase.QuoteCsvExporter
import com.routeprice.core.domain.usecase.QuoteRequest
import com.routeprice.core.domain.usecase.SummaryCalculator
import com.routeprice.core.domain.usecase.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

class QuoteUseCasesTest {

    private var now = 1_761_900_000_000L // 31/10/2025
    private val clock = object : TimeProvider {
        override fun nowMillis() = now
    }
    private val build = BuildQuoteUseCase(PricingEngine(), clock)

    private fun request(tolls: TollInfo = TollInfo.NotReported, manual: Money? = null) = QuoteRequest(
        origin = PlaceRef("Sorocaba"),
        destination = PlaceRef("São Paulo"),
        stops = listOf(PlaceRef("Alphaville")),
        route = RouteSummary(100_000, 5_400, source = ValueSource.EXTERNAL, tolls = tolls),
        vehicle = null,
        manualTolls = manual,
    )

    @Test
    fun `pedagio estimado pela rota vem como fonte externa`() {
        val q = build(request(TollInfo.Estimated(Money.reais("18.20"))), PricingSettings(), DriverProfile())
        assertEquals(Money.reais("18.20"), q.input.tolls)
        assertEquals(ValueSource.EXTERNAL, q.input.tollsSource)
        assertEquals(1, q.input.stopsCount)
    }

    @Test
    fun `pedagio digitado substitui o da rota`() {
        val q = build(request(TollInfo.Estimated(Money.reais("18.20")), Money.reais("20.00")), PricingSettings(), DriverProfile())
        assertEquals(Money.reais("20.00"), q.input.tolls)
        assertEquals(ValueSource.DRIVER_INFORMED, q.input.tollsSource)
    }

    @Test
    fun `pedagio com preco desconhecido e sinalizado`() {
        val q = build(request(TollInfo.PriceUnknown), PricingSettings(), DriverProfile())
        assertTrue(q.input.tollsPriceUnknown)
        assertEquals(Money.ZERO, q.input.tolls)
    }

    @Test
    fun `validade e preco negociado`() {
        val q = build(request().copy(negotiatedPrice = Money.reais("300.00")), PricingSettings(quoteValidityHours = 48), DriverProfile())
        assertEquals(now + 48 * 3_600_000L, q.validUntilMillis)
        assertEquals(Money.reais("300.00"), q.finalPrice)
        assertEquals(Money.reais("300.00") - q.suggestedPrice, q.negotiation.difference)
        assertFalse(q.isExpired(now))
        assertTrue(q.isExpired(now + 49 * 3_600_000L))
    }

    @Test
    fun `preco negociado igual ao sugerido nao e registrado como ajuste`() {
        val first = build(request(), PricingSettings(), DriverProfile())
        val q = build(request().copy(negotiatedPrice = first.suggestedPrice), PricingSettings(), DriverProfile())
        assertNull(q.negotiatedPrice)
        assertFalse(q.negotiation.hasAdjustment)
    }

    @Test
    fun `negociacao calcula diferenca percentual`() {
        val n = com.routeprice.core.domain.model.Negotiation(Money.reais("42.00"), Money.reais("38.00"), Money.reais("23.13"))
        assertEquals(Money.reais("-4.00"), n.difference)
        assertEquals(-9.5, n.differencePercent!!, 0.0)
        assertFalse(n.isBelowSustainable)
        assertTrue(n.copy(negotiated = Money.reais("20.00")).isBelowSustainable)
    }

    @Test
    fun `recalculo preserva id e data de criacao`() {
        val first = build(request(), PricingSettings(), DriverProfile())
        now += 60_000
        val again = build(request().copy(existing = first), PricingSettings(), DriverProfile())
        assertEquals(first.id, again.id)
        assertEquals(first.createdAtMillis, again.createdAtMillis)
        assertEquals(now, again.updatedAtMillis)
    }

    @Test
    fun `duplicar cria novo rascunho`() {
        val first = build(request(), PricingSettings(), DriverProfile()).copy(status = QuoteStatus.ACCEPTED)
        now += 86_400_000
        val copy = DuplicateQuoteUseCase(clock)(first)
        assertNotEquals(first.id, copy.id)
        assertEquals(QuoteStatus.DRAFT, copy.status)
        assertEquals(now, copy.createdAtMillis)
        assertEquals(first.suggestedPrice, copy.suggestedPrice)
    }

    @Test
    fun `resumo mensal ignora demonstracoes e outros meses`() {
        val base = build(request(), PricingSettings(), DriverProfile())
        val quotes = listOf(
            base.copy(id = "1", status = QuoteStatus.ACCEPTED),
            base.copy(id = "2", status = QuoteStatus.SENT),
            base.copy(id = "3", isDemo = true, status = QuoteStatus.ACCEPTED),
            base.copy(id = "4", createdAtMillis = now - 40L * 86_400_000, status = QuoteStatus.ACCEPTED),
        )
        val s = SummaryCalculator.monthly(quotes, YearMonth.of(2025, 10), ZoneOffset.UTC)
        assertEquals(2, s.quotesCount)
        assertEquals(1, s.acceptedCount)
        assertEquals(base.finalPrice, s.acceptedTotal)
        assertEquals(50, s.acceptanceRatePercent)
    }

    @Test
    fun `csv escapa separadores`() {
        val q = build(request(), PricingSettings(), DriverProfile()).copy(passengerName = "Ana; \"Bia\"")
        val csv = QuoteCsvExporter.export(listOf(q), ZoneOffset.UTC)
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"Ana; \"\"Bia\"\"\""))
        assertTrue(lines[1].contains("Sorocaba"))
    }
}
