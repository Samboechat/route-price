package com.routeprice.feature.home

import com.routeprice.core.domain.model.AppPreferences
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.pricing.PricingEngine
import com.routeprice.core.domain.repository.QuoteRepository
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.usecase.BuildQuoteUseCase
import com.routeprice.core.domain.usecase.TimeProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = object : TimeProvider {
        override fun nowMillis() = 1_700_000_000_000L
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun saudacoes() {
        assertEquals("Bom dia", HomeViewModel.greeting(8))
        assertEquals("Boa tarde", HomeViewModel.greeting(13))
        assertEquals("Boa noite", HomeViewModel.greeting(22))
        assertEquals("Boa noite", HomeViewModel.greeting(2))
    }

    @Test
    fun `checklist e exemplo identificado`() = runTest(dispatcher) {
        val quotes = mockk<QuoteRepository>()
        val saved = slot<TripQuote>()
        every { quotes.observeRecent(any()) } returns flowOf(emptyList())
        coEvery { quotes.upsert(capture(saved)) } returns Unit
        val settings = mockk<SettingsRepository>()
        every { settings.driverProfile } returns flowOf(DriverProfile())
        every { settings.pricingSettings } returns flowOf(PricingSettings())
        every { settings.appPreferences } returns flowOf(AppPreferences())
        val vehicles = mockk<VehicleRepository>()
        every { vehicles.observeAll() } returns flowOf(emptyList())

        val vm = HomeViewModel(quotes, settings, vehicles, BuildQuoteUseCase(PricingEngine(), clock), clock)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state.checklist.needsProfile && state.checklist.needsVehicle && state.checklist.needsPricingReview)

        vm.createDemoQuote()
        advanceUntilIdle()
        assertTrue(saved.captured.isDemo)
        assertEquals(saved.captured.id, vm.openQuote.first())
    }
}
