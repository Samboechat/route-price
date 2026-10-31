package com.routeprice.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routeprice.core.domain.model.AppPreferences
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.FuelType
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.ThemePreference
import com.routeprice.core.domain.model.Vehicle
import com.routeprice.core.domain.model.VehicleType
import com.routeprice.core.domain.repository.DataExportService
import com.routeprice.core.domain.repository.ExternalServicesStatus
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.util.BrFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

private const val STOP_TIMEOUT = 5_000L

// ---------------- Tela principal de configurações ----------------

data class SettingsUiState(
    val driver: DriverProfile = DriverProfile(),
    val pricing: PricingSettings = PricingSettings(),
    val vehicleCount: Int = 0,
    val prefs: AppPreferences = AppPreferences(),
    val mapsConfigured: Boolean = false,
    val online: Boolean = true,
)

enum class ExportKind(val mime: String, val fileName: String) {
    JSON("application/json", "route-price-backup.json"),
    CSV("text/csv", "route-price-historico.csv"),
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    vehicles: VehicleRepository,
    services: ExternalServicesStatus,
    private val exporter: DataExportService,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.driverProfile,
        settings.pricingSettings,
        vehicles.observeAll(),
        settings.appPreferences,
        services.isOnline,
    ) { driver, pricing, list, prefs, online ->
        SettingsUiState(driver, pricing, list.size, prefs, services.isMapsConfigured, online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), SettingsUiState(mapsConfigured = services.isMapsConfigured))

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { settings.updateAppPreferences { it.copy(theme = theme) } }
    }

    suspend fun export(kind: ExportKind): String = when (kind) {
        ExportKind.JSON -> exporter.exportJson()
        ExportKind.CSV -> exporter.exportCsv()
    }
}

// ---------------- Perfil do motorista ----------------

@HiltViewModel
class DriverProfileViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    private val _form = MutableStateFlow(DriverProfile())
    val form: StateFlow<DriverProfile> = _form.asStateFlow()
    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    init {
        viewModelScope.launch { _form.value = settings.driverProfile.first() }
    }

    fun update(transform: (DriverProfile) -> DriverProfile) = _form.update(transform)

    fun save() {
        viewModelScope.launch {
            val f = _form.value
            settings.updateDriverProfile {
                f.copy(name = f.name.trim(), phone = f.phone.trim(), city = f.city.trim(), vehicleDescriptionForPassenger = f.vehicleDescriptionForPassenger.trim())
            }
            _saved.send(Unit)
        }
    }
}

// ---------------- Veículos ----------------

data class VehiclesUiState(val vehicles: List<Vehicle> = emptyList(), val defaultId: String? = null, val loading: Boolean = true)

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    repository: VehicleRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<VehiclesUiState> = combine(repository.observeAll(), settings.appPreferences) { list, prefs ->
        VehiclesUiState(list, prefs.selectedVehicleId, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), VehiclesUiState())

    fun setDefault(id: String) {
        viewModelScope.launch { settings.updateAppPreferences { it.copy(selectedVehicleId = id) } }
    }
}

data class VehicleForm(
    val id: String? = null,
    val name: String = "",
    val type: VehicleType = VehicleType.SEDAN,
    val plate: String = "",
    val seats: String = "4",
    val fuelType: FuelType = FuelType.GASOLINE,
    val consumption: String = "",
    val maintenancePerKm: String = "",
    val wearPerKm: String = "",
    val rateMultiplier: String = "1",
    val errors: Map<String, String> = emptyMap(),
)

@HiltViewModel
class VehicleEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VehicleRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val vehicleId: String? = savedStateHandle.get<String>("vehicleId")
    private val _form = MutableStateFlow(VehicleForm())
    val form: StateFlow<VehicleForm> = _form.asStateFlow()
    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        vehicleId?.let { id ->
            viewModelScope.launch {
                repository.get(id)?.let { v ->
                    _form.value = VehicleForm(
                        id = v.id,
                        name = v.name,
                        type = v.type,
                        plate = v.plate,
                        seats = v.seats.toString(),
                        fuelType = v.fuelType,
                        consumption = v.consumptionKmPerUnit.display(),
                        maintenancePerKm = v.maintenancePerKm.display(),
                        wearPerKm = v.wearPerKm.display(),
                        rateMultiplier = v.rateMultiplier.display(),
                    )
                }
            }
        }
    }

    val isEditing: Boolean get() = vehicleId != null

    fun update(transform: (VehicleForm) -> VehicleForm) = _form.update { transform(it).copy(errors = emptyMap()) }

    fun save() {
        val f = _form.value
        val errors = mutableMapOf<String, String>()
        if (f.name.isBlank()) errors["name"] = "Informe um nome"
        val consumption = BrFormat.parseDecimal(f.consumption)
        if (consumption == null || consumption <= BigDecimal.ZERO) errors["consumption"] = "Informe o consumo médio"
        val maintenance = parseOptional(f.maintenancePerKm, "maintenance", errors)
        val wear = parseOptional(f.wearPerKm, "wear", errors)
        val multiplier = BrFormat.parseDecimal(f.rateMultiplier)
        if (multiplier == null || multiplier <= BigDecimal.ZERO || multiplier > BigDecimal(MAX_MULTIPLIER)) {
            errors["multiplier"] = "Use um valor entre 0,1 e $MAX_MULTIPLIER"
        }
        val seats = f.seats.toIntOrNull()
        if (seats == null || seats !in 1..MAX_SEATS) errors["seats"] = "Lugares inválidos"
        if (errors.isNotEmpty()) {
            _form.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            val vehicle = Vehicle(
                id = f.id ?: UUID.randomUUID().toString(),
                name = f.name.trim(),
                type = f.type,
                plate = f.plate.trim().uppercase(),
                seats = seats!!,
                fuelType = f.fuelType,
                consumptionKmPerUnit = consumption!!,
                maintenancePerKm = maintenance,
                wearPerKm = wear,
                rateMultiplier = multiplier!!,
            )
            repository.upsert(vehicle)
            if (settings.appPreferences.first().selectedVehicleId == null) {
                settings.updateAppPreferences { it.copy(selectedVehicleId = vehicle.id) }
            }
            _done.send(Unit)
        }
    }

    fun delete() {
        val id = vehicleId ?: return
        viewModelScope.launch {
            repository.delete(id)
            settings.updateAppPreferences { if (it.selectedVehicleId == id) it.copy(selectedVehicleId = null) else it }
            _done.send(Unit)
        }
    }

    private fun parseOptional(text: String, key: String, errors: MutableMap<String, String>): BigDecimal {
        if (text.isBlank()) return BigDecimal.ZERO
        val v = BrFormat.parseDecimal(text)
        if (v == null || v < BigDecimal.ZERO) {
            errors[key] = "Valor inválido"
            return BigDecimal.ZERO
        }
        return v
    }

    private companion object {
        const val MAX_MULTIPLIER = 5
        const val MAX_SEATS = 20
    }
}

private const val FORM_DECIMALS = 4

internal fun BigDecimal.display(): String = if (signum() == 0) "" else BrFormat.decimal(this, FORM_DECIMALS)
