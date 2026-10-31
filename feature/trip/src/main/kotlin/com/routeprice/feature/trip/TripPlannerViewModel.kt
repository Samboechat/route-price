package com.routeprice.feature.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.routeprice.core.domain.model.AppResult
import com.routeprice.core.domain.model.DomainError
import com.routeprice.core.domain.model.GeoPoint
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PlaceSuggestion
import com.routeprice.core.domain.model.RouteOption
import com.routeprice.core.domain.model.RouteSummary
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.model.Vehicle
import com.routeprice.core.domain.repository.ExternalServicesStatus
import com.routeprice.core.domain.repository.LocationRepository
import com.routeprice.core.domain.repository.PlacesRepository
import com.routeprice.core.domain.repository.QuoteRepository
import com.routeprice.core.domain.repository.RouteRepository
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.usecase.BuildQuoteUseCase
import com.routeprice.core.domain.usecase.QuoteRequest
import com.routeprice.core.domain.util.BrFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

sealed interface PointTarget {
    data object Origin : PointTarget
    data object Destination : PointTarget
    data class Stop(val id: String) : PointTarget
}

data class StopItem(val id: String = UUID.randomUUID().toString(), val place: PlaceRef? = null)

sealed interface RouteState {
    data object Idle : RouteState
    data object Loading : RouteState
    data class Ready(val routes: List<RouteOption>, val selectedId: String) : RouteState {
        val selected: RouteOption get() = routes.first { it.id == selectedId }
    }
    data class Failed(val error: DomainError) : RouteState
}

data class SearchState(
    val target: PointTarget,
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val loading: Boolean = false,
    val error: DomainError? = null,
)

/** Campos de texto editáveis (mantidos como texto para não brigar com a digitação). */
data class TripExtras(
    val waitingMinutes: String = "",
    val tolls: String = "",
    val extraExpenses: String = "",
    val deadheadKm: String = "",
    val deadheadMinutes: String = "",
    val passengerName: String = "",
    val manualDistanceKm: String = "",
    val manualDurationMinutes: String = "",
)

data class TripPlannerUiState(
    val editingQuoteId: String? = null,
    val origin: PlaceRef? = null,
    val destination: PlaceRef? = null,
    val stops: List<StopItem> = emptyList(),
    val route: RouteState = RouteState.Idle,
    val manualRoute: Boolean = false,
    val extras: TripExtras = TripExtras(),
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicleId: String? = null,
    val mapsConfigured: Boolean = true,
    val online: Boolean = true,
    val search: SearchState? = null,
    val pickingOnMap: PointTarget? = null,
    val lastKnownLocation: GeoPoint? = null,
    val message: String? = null,
    val calculating: Boolean = false,
    val validationError: String? = null,
) {
    val routeSummary: RouteSummary?
        get() = if (manualRoute || !mapsConfigured) {
            val km = BrFormat.parseDecimal(extras.manualDistanceKm)
            val minutes = BrFormat.parseDecimal(extras.manualDurationMinutes)
            if (km == null || minutes == null || km <= BigDecimal.ZERO || minutes < BigDecimal.ZERO) {
                null
            } else {
                RouteSummary(
                    distanceMeters = km.movePointRight(3).toLong(),
                    durationSeconds = minutes.multiply(BigDecimal(60)).toLong(),
                    source = ValueSource.DRIVER_INFORMED,
                )
            }
        } else {
            (route as? RouteState.Ready)?.selected?.let {
                RouteSummary(it.distanceMeters, it.durationSeconds, it.encodedPolyline, it.source, it.tolls, it.description)
            }
        }

    val canCalculate: Boolean
        get() = origin != null && destination != null && stops.all { it.place != null } && routeSummary != null && !calculating
}

@HiltViewModel
class TripPlannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routes: RouteRepository,
    private val places: PlacesRepository,
    private val location: LocationRepository,
    private val quotes: QuoteRepository,
    private val vehicles: VehicleRepository,
    private val settings: SettingsRepository,
    private val buildQuote: BuildQuoteUseCase,
    private val services: ExternalServicesStatus,
) : ViewModel() {

    private val args = runCatching { savedStateHandle.toRoute<TripPlannerRoute>() }.getOrDefault(TripPlannerRoute())
    private val _state = MutableStateFlow(TripPlannerUiState(mapsConfigured = services.isMapsConfigured))
    val state: StateFlow<TripPlannerUiState> = _state.asStateFlow()

    private val navigation = Channel<String>(Channel.BUFFERED)
    /** Emite o id do orçamento calculado e salvo. */
    val quoteSaved = navigation.receiveAsFlow()

    private var sessionToken = newToken()
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var existing: TripQuote? = null

    init {
        viewModelScope.launch { services.isOnline.collect { online -> _state.update { it.copy(online = online) } } }
        viewModelScope.launch {
            vehicles.observeAll().collect { list ->
                val preferred = settings.appPreferences.first().selectedVehicleId
                _state.update { s ->
                    val keep = s.selectedVehicleId?.takeIf { id -> list.any { it.id == id } }
                    s.copy(vehicles = list, selectedVehicleId = keep ?: preferred?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id)
                }
            }
        }
        args.quoteId?.let { id -> viewModelScope.launch { loadExisting(id) } }
    }

    private suspend fun loadExisting(id: String) {
        val quote = quotes.get(id) ?: return
        existing = quote
        val manual = quote.route.source == ValueSource.DRIVER_INFORMED
        _state.update {
            it.copy(
                editingQuoteId = quote.id,
                origin = quote.origin,
                destination = quote.destination,
                stops = quote.stops.map { p -> StopItem(place = p) },
                manualRoute = manual,
                selectedVehicleId = quote.vehicle?.id ?: it.selectedVehicleId,
                extras = TripExtras(
                    waitingMinutes = quote.input.waitingMinutes.takeIf { m -> m > 0 }?.toString().orEmpty(),
                    tolls = if (quote.input.tollsSource == ValueSource.DRIVER_INFORMED && quote.input.tolls.isPositive) {
                        BrFormat.decimal(quote.input.tolls.toBigDecimal())
                    } else {
                        ""
                    },
                    extraExpenses = quote.input.extraExpenses.takeIf { m -> m.isPositive }?.let { m -> BrFormat.decimal(m.toBigDecimal()) }.orEmpty(),
                    deadheadKm = quote.input.deadheadKm.takeIf { k -> k > BigDecimal.ZERO }?.let { k -> BrFormat.decimal(k, 1) }.orEmpty(),
                    deadheadMinutes = quote.input.deadheadMinutes.takeIf { m -> m > 0 }?.toString().orEmpty(),
                    passengerName = quote.passengerName,
                    manualDistanceKm = if (manual) BrFormat.decimal(BigDecimal(quote.route.distanceMeters).movePointLeft(3), 1) else "",
                    manualDurationMinutes = if (manual) (quote.route.durationSeconds / SECONDS_PER_MINUTE).toString() else "",
                ),
            )
        }
        if (!manual) refreshRoute(immediate = true)
    }

    // ---------- Busca de endereços ----------

    fun openSearch(target: PointTarget) {
        sessionToken = newToken()
        _state.update { it.copy(search = SearchState(target), pickingOnMap = null) }
    }

    fun closeSearch() = _state.update { it.copy(search = null) }

    fun onQueryChange(query: String) {
        _state.update { it.copy(search = it.search?.copy(query = query, error = null)) }
        searchJob?.cancel()
        if (!_state.value.mapsConfigured) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(search = it.search?.copy(loading = true)) }
            val result = places.autocomplete(query, _state.value.lastKnownLocation, sessionToken)
            _state.update { s ->
                val search = s.search ?: return@update s
                when (result) {
                    is AppResult.Success -> s.copy(search = search.copy(suggestions = result.value, loading = false))
                    is AppResult.Failure -> s.copy(search = search.copy(loading = false, error = result.error))
                }
            }
        }
    }

    fun onSuggestionSelected(suggestion: PlaceSuggestion) {
        val target = _state.value.search?.target ?: return
        viewModelScope.launch {
            _state.update { it.copy(search = it.search?.copy(loading = true)) }
            when (val result = places.details(suggestion.placeId, sessionToken)) {
                is AppResult.Success -> {
                    setPoint(target, result.value.copy(label = suggestion.primaryText.ifBlank { result.value.label }))
                    _state.update { it.copy(search = null) }
                }
                is AppResult.Failure -> _state.update { it.copy(search = it.search?.copy(loading = false, error = result.error)) }
            }
            sessionToken = newToken()
        }
    }

    /** Usa o texto digitado como endereço sem geolocalização (modo manual). */
    fun useTypedText() {
        val search = _state.value.search ?: return
        val text = search.query.trim()
        if (text.isEmpty()) return
        setPoint(search.target, PlaceRef(label = text, address = text))
        _state.update { it.copy(search = null, manualRoute = it.manualRoute || it.mapsConfigured) }
    }

    fun useCurrentLocation() {
        val target = _state.value.search?.target ?: return
        viewModelScope.launch {
            _state.update { it.copy(search = it.search?.copy(loading = true, error = null)) }
            when (val loc = location.currentLocation()) {
                is AppResult.Success -> {
                    _state.update { it.copy(lastKnownLocation = loc.value) }
                    val place = places.reverseGeocode(loc.value)
                    val resolved = (place as? AppResult.Success)?.value ?: PlaceRef("Minha localização", location = loc.value)
                    setPoint(target, resolved.copy(label = "Minha localização · " + resolved.label))
                    _state.update { it.copy(search = null) }
                }
                is AppResult.Failure -> _state.update { it.copy(search = it.search?.copy(loading = false, error = loc.error)) }
            }
        }
    }

    fun onLocationPermissionDenied() {
        _state.update { it.copy(search = it.search?.copy(loading = false, error = DomainError.LocationPermissionDenied)) }
    }

    fun startPickOnMap() {
        val target = _state.value.search?.target ?: return
        _state.update { it.copy(search = null, pickingOnMap = target) }
    }

    fun cancelPickOnMap() = _state.update { it.copy(pickingOnMap = null) }

    fun onMapClick(point: GeoPoint) {
        val target = _state.value.pickingOnMap ?: return
        _state.update { it.copy(pickingOnMap = null) }
        viewModelScope.launch {
            val place = (places.reverseGeocode(point) as? AppResult.Success)?.value ?: PlaceRef("Ponto no mapa", location = point)
            setPoint(target, place)
        }
    }

    // ---------- Paradas ----------

    fun addStop() {
        val id = UUID.randomUUID().toString()
        _state.update { it.copy(stops = it.stops + StopItem(id)) }
        openSearch(PointTarget.Stop(id))
    }

    fun removeStop(id: String) {
        _state.update { it.copy(stops = it.stops.filterNot { s -> s.id == id }) }
        refreshRoute()
    }

    fun moveStop(id: String, delta: Int) {
        _state.update { s ->
            val index = s.stops.indexOfFirst { it.id == id }
            val newIndex = index + delta
            if (index < 0 || newIndex !in s.stops.indices) return@update s
            val list = s.stops.toMutableList()
            list.add(newIndex, list.removeAt(index))
            s.copy(stops = list)
        }
        refreshRoute()
    }

    fun swapOriginDestination() {
        _state.update { it.copy(origin = it.destination, destination = it.origin, stops = it.stops.reversed()) }
        refreshRoute()
    }

    fun clearPoint(target: PointTarget) {
        when (target) {
            PointTarget.Origin -> _state.update { it.copy(origin = null, route = RouteState.Idle) }
            PointTarget.Destination -> _state.update { it.copy(destination = null, route = RouteState.Idle) }
            is PointTarget.Stop -> removeStop(target.id)
        }
    }

    private fun setPoint(target: PointTarget, place: PlaceRef) {
        _state.update { s ->
            when (target) {
                PointTarget.Origin -> s.copy(origin = place)
                PointTarget.Destination -> s.copy(destination = place)
                is PointTarget.Stop -> s.copy(stops = s.stops.map { if (it.id == target.id) it.copy(place = place) else it })
            }
        }
        refreshRoute()
    }

    // ---------- Rota ----------

    fun selectRoute(id: String) = _state.update { s ->
        val r = s.route as? RouteState.Ready ?: return@update s
        s.copy(route = r.copy(selectedId = id))
    }

    fun setManualRoute(manual: Boolean) {
        _state.update { it.copy(manualRoute = manual) }
        if (!manual) refreshRoute(immediate = true)
    }

    fun retryRoute() = refreshRoute(immediate = true)

    private fun refreshRoute(immediate: Boolean = false) {
        routeJob?.cancel()
        val s = _state.value
        val origin = s.origin
        val destination = s.destination
        if (!s.mapsConfigured || s.manualRoute || origin == null || destination == null || s.stops.any { it.place == null }) {
            if (origin == null || destination == null) _state.update { it.copy(route = RouteState.Idle) }
            return
        }
        routeJob = viewModelScope.launch {
            if (!immediate) delay(ROUTE_DEBOUNCE_MS)
            _state.update { it.copy(route = RouteState.Loading) }
            val result = routes.computeRoutes(origin, destination, s.stops.mapNotNull { it.place })
            _state.update {
                it.copy(
                    route = when (result) {
                        is AppResult.Success -> RouteState.Ready(result.value, result.value.first().id)
                        is AppResult.Failure -> RouteState.Failed(result.error)
                    },
                )
            }
        }
    }

    // ---------- Detalhes ----------

    fun updateExtras(transform: (TripExtras) -> TripExtras) = _state.update { it.copy(extras = transform(it.extras), validationError = null) }

    fun selectVehicle(id: String?) {
        _state.update { it.copy(selectedVehicleId = id) }
        viewModelScope.launch { settings.updateAppPreferences { it.copy(selectedVehicleId = id) } }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---------- Cálculo ----------

    fun calculate() {
        val s = _state.value
        val origin = s.origin
        val destination = s.destination
        val route = s.routeSummary
        if (origin == null || destination == null || route == null || s.stops.any { it.place == null }) {
            _state.update { it.copy(validationError = "Defina embarque, destino, todas as paradas e a rota antes de calcular.") }
            return
        }
        val e = s.extras
        val tolls = e.tolls.takeIf { it.isNotBlank() }?.let(BrFormat::parseDecimal)
        val extra = e.extraExpenses.takeIf { it.isNotBlank() }?.let(BrFormat::parseDecimal)
        val deadheadKm = e.deadheadKm.takeIf { it.isNotBlank() }?.let(BrFormat::parseDecimal)
        val numbersInvalid = (e.tolls.isNotBlank() && tolls == null) || (e.extraExpenses.isNotBlank() && extra == null) ||
            (e.deadheadKm.isNotBlank() && deadheadKm == null) || listOfNotNull(tolls, extra, deadheadKm).any { it < BigDecimal.ZERO }
        if (numbersInvalid) {
            _state.update { it.copy(validationError = "Confira os valores digitados nos detalhes da viagem.") }
            return
        }
        _state.update { it.copy(calculating = true) }
        viewModelScope.launch {
            val vehicle = s.vehicles.firstOrNull { it.id == s.selectedVehicleId }
            val quote = buildQuote(
                QuoteRequest(
                    origin = origin,
                    destination = destination,
                    stops = s.stops.mapNotNull { it.place },
                    route = route,
                    vehicle = vehicle,
                    waitingMinutes = e.waitingMinutes.toIntOrNull() ?: 0,
                    extraExpenses = extra?.let(Money::of) ?: Money.ZERO,
                    manualTolls = tolls?.let(Money::of),
                    deadheadKm = deadheadKm ?: BigDecimal.ZERO,
                    deadheadMinutes = e.deadheadMinutes.toIntOrNull() ?: 0,
                    passengerName = e.passengerName,
                    notes = existing?.notes.orEmpty(),
                    negotiatedPrice = null,
                    status = existing?.status ?: com.routeprice.core.domain.model.QuoteStatus.DRAFT,
                    existing = existing,
                ),
                settings.pricingSettings.first(),
                settings.driverProfile.first(),
            )
            quotes.upsert(quote)
            existing = quote
            _state.update { it.copy(calculating = false, editingQuoteId = quote.id) }
            navigation.send(quote.id)
        }
    }

    private fun newToken() = UUID.randomUUID().toString()

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val ROUTE_DEBOUNCE_MS = 400L
        const val SECONDS_PER_MINUTE = 60
    }
}
