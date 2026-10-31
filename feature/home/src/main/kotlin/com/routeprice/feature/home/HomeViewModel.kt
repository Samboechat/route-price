package com.routeprice.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.RouteSummary
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.repository.QuoteRepository
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.usecase.BuildQuoteUseCase
import com.routeprice.core.domain.usecase.MonthlySummary
import com.routeprice.core.domain.usecase.QuoteRequest
import com.routeprice.core.domain.usecase.SummaryCalculator
import com.routeprice.core.domain.usecase.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class SetupChecklist(val needsProfile: Boolean, val needsVehicle: Boolean, val needsPricingReview: Boolean) {
    val isComplete: Boolean get() = !needsProfile && !needsVehicle && !needsPricingReview
}

data class HomeUiState(
    val loading: Boolean = true,
    val greeting: String = "",
    val driverFirstName: String = "",
    val recentQuotes: List<TripQuote> = emptyList(),
    val recentTrips: List<TripQuote> = emptyList(),
    val summary: MonthlySummary? = null,
    val checklist: SetupChecklist = SetupChecklist(false, false, false),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val quotes: QuoteRepository,
    private val settings: SettingsRepository,
    vehicles: VehicleRepository,
    private val buildQuote: BuildQuoteUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    private val events = Channel<String>(Channel.BUFFERED)

    /** Id do orçamento a abrir (ex.: exemplo recém-criado). */
    val openQuote = events.receiveAsFlow()

    val state: StateFlow<HomeUiState> = combine(
        quotes.observeRecent(RECENT_LIMIT),
        settings.driverProfile,
        settings.pricingSettings,
        vehicles.observeAll(),
    ) { recent, driver, pricing, vehicleList ->
        val now = time.nowMillis()
        val zone = ZoneId.systemDefault()
        HomeUiState(
            loading = false,
            greeting = greeting(Instant.ofEpochMilli(now).atZone(zone).hour),
            driverFirstName = driver.name.trim().substringBefore(' '),
            recentQuotes = recent.filter { it.status != QuoteStatus.ACCEPTED }.take(SECTION_SIZE),
            recentTrips = recent.filter { it.status == QuoteStatus.ACCEPTED }.take(SECTION_SIZE),
            summary = SummaryCalculator.monthly(recent, YearMonth.from(Instant.ofEpochMilli(now).atZone(zone)), zone),
            checklist = SetupChecklist(
                needsProfile = !driver.isComplete,
                needsVehicle = vehicleList.isEmpty(),
                needsPricingReview = !pricing.reviewedByDriver,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), HomeUiState())

    /** Cria um orçamento de demonstração, identificado como exemplo em todas as telas e documentos. */
    fun createDemoQuote() {
        viewModelScope.launch {
            val pricing: PricingSettings = settings.pricingSettings.first()
            val driver: DriverProfile = settings.driverProfile.first()
            val quote = buildQuote(
                QuoteRequest(
                    origin = PlaceRef("Embarque de exemplo", "Endereço fictício"),
                    destination = PlaceRef("Destino de exemplo", "Endereço fictício"),
                    stops = listOf(PlaceRef("Parada de exemplo", "Endereço fictício")),
                    route = RouteSummary(
                        distanceMeters = DEMO_DISTANCE_METERS,
                        durationSeconds = DEMO_DURATION_SECONDS,
                        source = ValueSource.DRIVER_INFORMED,
                        description = "Dados de demonstração",
                    ),
                    vehicle = null,
                    waitingMinutes = DEMO_WAITING_MINUTES,
                    passengerName = "Passageiro de exemplo",
                ),
                pricing,
                driver,
            ).copy(isDemo = true)
            quotes.upsert(quote)
            events.send(quote.id)
        }
    }

    companion object {
        private const val RECENT_LIMIT = 200
        private const val SECTION_SIZE = 5
        private const val STOP_TIMEOUT = 5_000L
        private const val DEMO_DISTANCE_METERS = 18_400L
        private const val DEMO_DURATION_SECONDS = 1_920L
        private const val DEMO_WAITING_MINUTES = 10
        private const val MORNING = 5
        private const val AFTERNOON = 12
        private const val EVENING = 18

        fun greeting(hour: Int): String = when (hour) {
            in MORNING until AFTERNOON -> "Bom dia"
            in AFTERNOON until EVENING -> "Boa tarde"
            else -> "Boa noite"
        }
    }
}
