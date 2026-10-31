package com.routeprice.feature.trip

import androidx.lifecycle.SavedStateHandle
import com.routeprice.core.domain.model.AppPreferences
import com.routeprice.core.domain.model.AppResult
import com.routeprice.core.domain.model.DomainError
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.GeoPoint
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PlaceSuggestion
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.RouteOption
import com.routeprice.core.domain.model.TollInfo
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.model.Vehicle
import com.routeprice.core.domain.pricing.PricingEngine
import com.routeprice.core.domain.repository.ExternalServicesStatus
import com.routeprice.core.domain.repository.LocationRepository
import com.routeprice.core.domain.repository.PlacesRepository
import com.routeprice.core.domain.repository.QuoteRepository
import com.routeprice.core.domain.repository.RouteRepository
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.usecase.BuildQuoteUseCase
import com.routeprice.core.domain.usecase.TimeProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
class TripPlannerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val routes = mockk<RouteRepository>()
    private val places = mockk<PlacesRepository>()
    private val location = mockk<LocationRepository>()
    private val quotes = mockk<QuoteRepository>(relaxed = true)
    private val vehicles = mockk<VehicleRepository>()
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val services = mockk<ExternalServicesStatus>()
    private val clock = object : TimeProvider {
        override fun nowMillis() = 1_000L
    }

    private val sorocaba = PlaceRef("Sorocaba", location = GeoPoint(-23.50, -47.45), placeId = "p1")
    private val alphaville = PlaceRef("Alphaville", location = GeoPoint(-23.50, -46.85), placeId = "p2")
    private val saoPaulo = PlaceRef("São Paulo", location = GeoPoint(-23.55, -46.63), placeId = "p3")
    private val car = Vehicle("v1", "Sedan")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { services.isMapsConfigured } returns true
        every { services.isOnline } returns flowOf(true)
        every { vehicles.observeAll() } returns flowOf(listOf(car))
        every { settings.appPreferences } returns flowOf(AppPreferences())
        every { settings.pricingSettings } returns flowOf(PricingSettings())
        every { settings.driverProfile } returns flowOf(DriverProfile(name = "Samuel"))
        coEvery { places.details("p1", any()) } returns AppResult.Success(sorocaba)
        coEvery { places.details("p2", any()) } returns AppResult.Success(alphaville)
        coEvery { places.details("p3", any()) } returns AppResult.Success(saoPaulo)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(mapsConfigured: Boolean = true): TripPlannerViewModel {
        every { services.isMapsConfigured } returns mapsConfigured
        return TripPlannerViewModel(
            SavedStateHandle(), routes, places, location, quotes, vehicles, settings,
            BuildQuoteUseCase(PricingEngine(), clock), services,
        )
    }

    private fun route(id: String, meters: Long, tolls: TollInfo = TollInfo.NotReported) =
        RouteOption(id, meters, meters / 10, "_p~iF~ps|U", tolls = tolls)

    private fun TestScope.select(vm: TripPlannerViewModel, target: PointTarget, placeId: String) {
        vm.openSearch(target)
        vm.onSuggestionSelected(PlaceSuggestion(placeId, placeId, ""))
        advanceUntilIdle()
    }

    @Test
    fun `seleciona veiculo padrao e monta rota respeitando a ordem das paradas`() = runTest(dispatcher) {
        val stops = slot<List<PlaceRef>>()
        coEvery { routes.computeRoutes(any(), any(), capture(stops)) } returns AppResult.Success(listOf(route("r0", 98_000)))
        val vm = vm()
        advanceUntilIdle()
        assertEquals("v1", vm.state.value.selectedVehicleId)

        select(vm, PointTarget.Origin, "p1")
        select(vm, PointTarget.Destination, "p3")
        vm.addStop()
        val stopId = vm.state.value.stops.single().id
        vm.onSuggestionSelected(PlaceSuggestion("p2", "Alphaville", ""))
        advanceUntilIdle()

        val ready = vm.state.value.route as RouteState.Ready
        assertEquals(98_000L, ready.selected.distanceMeters)
        assertEquals(listOf(alphaville), stops.captured)
        assertTrue(vm.state.value.canCalculate)
        assertEquals(stopId, vm.state.value.stops.single().id)
    }

    @Test
    fun `reordenar paradas recalcula a rota na nova ordem`() = runTest(dispatcher) {
        val calls = mutableListOf<List<PlaceRef>>()
        coEvery { routes.computeRoutes(any(), any(), capture(calls)) } returns AppResult.Success(listOf(route("r0", 50_000)))
        val vm = vm()
        select(vm, PointTarget.Origin, "p1")
        select(vm, PointTarget.Destination, "p3")
        vm.addStop(); vm.onSuggestionSelected(PlaceSuggestion("p2", "Alphaville", "")); advanceUntilIdle()
        vm.addStop(); vm.onSuggestionSelected(PlaceSuggestion("p1", "Sorocaba", "")); advanceUntilIdle()
        val second = vm.state.value.stops[1].id

        vm.moveStop(second, -1)
        advanceUntilIdle()

        assertEquals(listOf("Sorocaba", "Alphaville"), calls.last().map { it.label })
    }

    @Test
    fun `falha de rota fica visivel e permite modo manual`() = runTest(dispatcher) {
        coEvery { routes.computeRoutes(any(), any(), any()) } returns AppResult.Failure(DomainError.Offline)
        val vm = vm()
        select(vm, PointTarget.Origin, "p1")
        select(vm, PointTarget.Destination, "p3")
        assertEquals(RouteState.Failed(DomainError.Offline), vm.state.value.route)
        assertFalse(vm.state.value.canCalculate)

        vm.setManualRoute(true)
        vm.updateExtras { it.copy(manualDistanceKm = "12,5", manualDurationMinutes = "25") }
        val summary = vm.state.value.routeSummary!!
        assertEquals(12_500L, summary.distanceMeters)
        assertEquals(1_500L, summary.durationSeconds)
        assertEquals(ValueSource.DRIVER_INFORMED, summary.source)
        assertTrue(vm.state.value.canCalculate)
    }

    @Test
    fun `sem chave de mapas usa texto digitado e distancia manual`() = runTest(dispatcher) {
        val vm = vm(mapsConfigured = false)
        vm.openSearch(PointTarget.Origin); vm.onQueryChange("Rua A, 100"); vm.useTypedText()
        vm.openSearch(PointTarget.Destination); vm.onQueryChange("Rua B, 200"); vm.useTypedText()
        advanceUntilIdle()
        assertEquals("Rua A, 100", vm.state.value.origin!!.label)
        assertNull(vm.state.value.origin!!.location)
        assertFalse(vm.state.value.canCalculate)
        vm.updateExtras { it.copy(manualDistanceKm = "8", manualDurationMinutes = "15") }
        assertTrue(vm.state.value.canCalculate)
        coVerify(exactly = 0) { routes.computeRoutes(any(), any(), any()) }
    }

    @Test
    fun `calcular salva o orcamento com pedagio da rota e despesas`() = runTest(dispatcher) {
        coEvery { routes.computeRoutes(any(), any(), any()) } returns AppResult.Success(
            listOf(route("r0", 98_000, TollInfo.Estimated(Money.reais("12.30"))), route("r1", 101_000)),
        )
        val saved = slot<TripQuote>()
        coEvery { quotes.upsert(capture(saved)) } returns Unit
        val vm = vm()
        select(vm, PointTarget.Origin, "p1")
        select(vm, PointTarget.Destination, "p3")
        vm.updateExtras { it.copy(extraExpenses = "8,00", waitingMinutes = "10", passengerName = "Maria") }

        vm.calculate()
        advanceUntilIdle()

        val q = saved.captured
        assertEquals(98_000L, q.route.distanceMeters)
        assertEquals(Money.reais("12.30"), q.input.tolls)
        assertEquals(ValueSource.EXTERNAL, q.input.tollsSource)
        assertEquals(Money.reais("8.00"), q.input.extraExpenses)
        assertEquals("Maria", q.passengerName)
        assertEquals("v1", q.vehicle?.id)
        assertEquals(q.id, vm.quoteSaved.first())
    }

    @Test
    fun `alternativa de rota escolhida e usada no calculo`() = runTest(dispatcher) {
        coEvery { routes.computeRoutes(any(), any(), any()) } returns AppResult.Success(listOf(route("r0", 98_000), route("r1", 101_000)))
        val saved = slot<TripQuote>()
        coEvery { quotes.upsert(capture(saved)) } returns Unit
        val vm = vm()
        select(vm, PointTarget.Origin, "p1")
        select(vm, PointTarget.Destination, "p3")
        vm.selectRoute("r1")
        vm.calculate()
        advanceUntilIdle()
        assertEquals(101_000L, saved.captured.route.distanceMeters)
    }

    @Test
    fun `valores invalidos bloqueiam o calculo com mensagem`() = runTest(dispatcher) {
        val vm = vm(mapsConfigured = false)
        vm.openSearch(PointTarget.Origin); vm.onQueryChange("A"); vm.useTypedText()
        vm.openSearch(PointTarget.Destination); vm.onQueryChange("B"); vm.useTypedText()
        vm.updateExtras { it.copy(manualDistanceKm = "10", manualDurationMinutes = "20", tolls = "1,2,3,x") }
        vm.calculate()
        advanceUntilIdle()
        assertNotNull(vm.state.value.validationError)
        coVerify(exactly = 0) { quotes.upsert(any()) }
    }

    @Test
    fun `localizacao negada mostra erro de permissao`() = runTest(dispatcher) {
        coEvery { location.currentLocation() } returns AppResult.Failure(DomainError.LocationPermissionDenied)
        val vm = vm()
        vm.openSearch(PointTarget.Origin)
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertEquals(DomainError.LocationPermissionDenied, vm.state.value.search?.error)
    }

    @Test
    fun `erro de geocodificacao na busca e exibido`() = runTest(dispatcher) {
        coEvery { places.autocomplete(any(), any(), any()) } returns AppResult.Failure(DomainError.QuotaOrKeyRejected)
        val vm = vm()
        vm.openSearch(PointTarget.Destination)
        vm.onQueryChange("Avenida Paulista")
        advanceUntilIdle()
        assertEquals(DomainError.QuotaOrKeyRejected, vm.state.value.search?.error)
    }

    @Test
    fun `escolher no mapa usa geocodificacao reversa`() = runTest(dispatcher) {
        val point = GeoPoint(-23.0, -47.0)
        coEvery { places.reverseGeocode(point) } returns AppResult.Success(PlaceRef("Rua X", location = point))
        coEvery { routes.computeRoutes(any(), any(), any()) } returns AppResult.Success(listOf(route("r0", 1_000)))
        val vm = vm()
        vm.openSearch(PointTarget.Origin)
        vm.startPickOnMap()
        assertEquals(PointTarget.Origin, vm.state.value.pickingOnMap)
        vm.onMapClick(point)
        advanceUntilIdle()
        assertEquals("Rua X", vm.state.value.origin!!.label)
        assertNull(vm.state.value.pickingOnMap)
    }
}

