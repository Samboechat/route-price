package com.routeprice.feature.quote

import androidx.lifecycle.SavedStateHandle
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.LineKind
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PricingModel
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.RouteSummary
import com.routeprice.core.domain.model.TollInfo
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.pricing.PricingEngine
import com.routeprice.core.domain.repository.QuoteRepository
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.usecase.BuildQuoteUseCase
import com.routeprice.core.domain.usecase.DuplicateQuoteUseCase
import com.routeprice.core.domain.usecase.QuoteRequest
import com.routeprice.core.domain.usecase.TimeProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Repositório em memória para testar o fluxo do orçamento sem banco. */
private class InMemoryQuotes : QuoteRepository {
    val data = MutableStateFlow<Map<String, TripQuote>>(emptyMap())
    override fun observeRecent(limit: Int): Flow<List<TripQuote>> = data.map { it.values.toList() }
    override fun search(query: String): Flow<List<TripQuote>> = data.map { it.values.toList() }
    override fun observe(id: String): Flow<TripQuote?> = data.map { it[id] }
    override suspend fun get(id: String) = data.value[id]
    override suspend fun all() = data.value.values.toList()
    override suspend fun upsert(quote: TripQuote) { data.value = data.value + (quote.id to quote) }
    override suspend fun delete(id: String) { data.value = data.value - id }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuoteFeatureTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = object : TimeProvider {
        override fun nowMillis() = 1_700_000_000_000L
    }
    private val build = BuildQuoteUseCase(PricingEngine(), clock)

    private fun quote(tolls: TollInfo = TollInfo.Estimated(Money.reais("12.30"))) = build(
        QuoteRequest(
            origin = PlaceRef("Sorocaba"),
            destination = PlaceRef("São Paulo"),
            stops = listOf(PlaceRef("Alphaville")),
            route = RouteSummary(98_000, 5_400, "_p~iF~ps|U", ValueSource.EXTERNAL, tolls),
            vehicle = null,
            passengerName = "Maria",
        ),
        PricingSettings(reviewedByDriver = true),
        DriverProfile("Samuel", "(15) 99999-0000"),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `visao do passageiro fecha com o total, inclusive com desconto`() {
        val q = quote()
        assertEquals(q.finalPrice, Money.sum(PassengerView.lines(q).map { it.amount }))
        val negotiated = q.copy(negotiatedPrice = q.suggestedPrice - Money.reais("10.00"))
        val lines = PassengerView.lines(negotiated)
        assertEquals("Desconto combinado", lines.last().label)
        assertEquals(negotiated.finalPrice, Money.sum(lines.map { it.amount }))
    }

    @Test
    fun `texto de compartilhamento traz trajeto, pedagio, total e validade`() {
        val text = PassengerView.text(quote())
        assertTrue(text.contains("Embarque: Sorocaba"))
        assertTrue(text.contains("Parada 1: Alphaville"))
        assertTrue(text.contains("Destino: São Paulo"))
        assertTrue(text.contains("R$ 12,30"))
        assertTrue(text.contains("Valor total"))
        assertTrue(text.contains("válida até"))
        assertFalse(text.contains("EXEMPLO"))
        assertTrue(PassengerView.text(quote().copy(isDemo = true)).contains("EXEMPLO"))
    }

    @Test
    fun `pedagio sem preco nunca vira valor inventado`() {
        val q = quote(TollInfo.PriceUnknown)
        assertEquals("Há pedágio; valor não informado", PassengerView.tollsText(q))
        assertNull(q.result.lines.firstOrNull { it.kind == LineKind.TOLLS })
    }

    private fun viewModel(repo: InMemoryQuotes, id: String): QuoteViewModel {
        val settings = mockk<SettingsRepository>()
        every { settings.pricingSettings } returns flowOf(PricingSettings(reviewedByDriver = true))
        every { settings.driverProfile } returns flowOf(DriverProfile("Samuel"))
        val vehicles = mockk<VehicleRepository>()
        coEvery { vehicles.get(any()) } returns null
        val sharer = object : QuoteSharer {
            override suspend fun prepare(quote: TripQuote, format: ShareFormat) = SharePayload(format, PassengerView.text(quote))
        }
        return QuoteViewModel(SavedStateHandle(mapOf("quoteId" to id)), repo, vehicles, settings, build, DuplicateQuoteUseCase(clock), sharer, clock)
    }

    @Test
    fun `preco negociado registra diferenca e pode voltar ao sugerido`() = runTest(dispatcher) {
        val repo = InMemoryQuotes()
        val q = quote()
        repo.upsert(q)
        val vm = viewModel(repo, q.id)
        advanceUntilIdle()

        vm.onNegotiatedChange("150,00")
        vm.applyNegotiated()
        advanceUntilIdle()
        val saved = repo.get(q.id)!!
        assertEquals(Money.reais("150.00"), saved.finalPrice)
        assertEquals(Money.reais("150.00") - q.suggestedPrice, saved.negotiation.difference)

        vm.onNegotiatedChange("")
        vm.applyNegotiated()
        advanceUntilIdle()
        assertNull(repo.get(q.id)!!.negotiatedPrice)
    }

    @Test
    fun `valor negociado invalido mostra erro`() = runTest(dispatcher) {
        val repo = InMemoryQuotes()
        val q = quote()
        repo.upsert(q)
        val vm = viewModel(repo, q.id)
        advanceUntilIdle()
        vm.onNegotiatedChange("abc")
        vm.applyNegotiated()
        assertNotNull(vm.state.value.negotiatedError)
    }

    @Test
    fun `compartilhar marca rascunho como enviado`() = runTest(dispatcher) {
        val repo = InMemoryQuotes()
        val q = quote()
        repo.upsert(q)
        val vm = viewModel(repo, q.id)
        advanceUntilIdle()
        vm.share(ShareFormat.TEXT)
        advanceUntilIdle()
        val event = vm.events.first()
        assertTrue(event is QuoteEvent.Share)
        assertEquals(QuoteStatus.SENT, repo.get(q.id)!!.status)
    }

    @Test
    fun `trocar de modelo recalcula o mesmo orcamento`() = runTest(dispatcher) {
        val repo = InMemoryQuotes()
        val q = quote()
        repo.upsert(q)
        val vm = viewModel(repo, q.id)
        advanceUntilIdle()
        vm.recalculate(PricingModel.OPERATING_COST)
        advanceUntilIdle()
        val updated = repo.get(q.id)!!
        assertEquals(PricingModel.OPERATING_COST, updated.result.selectedModel)
        assertEquals(q.createdAtMillis, updated.createdAtMillis)
        assertEquals(1, repo.all().size)
    }

    @Test
    fun `duplicar e excluir`() = runTest(dispatcher) {
        val repo = InMemoryQuotes()
        val q = quote()
        repo.upsert(q)
        val vm = viewModel(repo, q.id)
        advanceUntilIdle()
        vm.duplicate()
        advanceUntilIdle()
        assertEquals(2, repo.all().size)
        vm.delete()
        advanceUntilIdle()
        assertNull(repo.get(q.id))
    }
}
