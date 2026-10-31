package com.routeprice.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routeprice.core.domain.model.CommercialParams
import com.routeprice.core.domain.model.CostParams
import com.routeprice.core.domain.model.FuelType
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PricingModel
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.RoundingRule
import com.routeprice.core.domain.model.TripPricingInput
import com.routeprice.core.domain.model.Vehicle
import com.routeprice.core.domain.pricing.PricingEngine
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.util.BrFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/** Formulário com campos em texto; convertido e validado ao salvar. */
data class PricingForm(
    val model: PricingModel = PricingModel.COMMERCIAL,
    val ratePerKm: String = "",
    val ratePerMinute: String = "",
    val stopFee: String = "",
    val waitingRate: String = "",
    val freeWaiting: String = "",
    val minimumFare: String = "",
    val adjustment: String = "",
    val energyPrices: Map<FuelType, String> = emptyMap(),
    val hourlyRate: String = "",
    val margin: String = "",
    val rounding: RoundingRule = RoundingRule.UP_TO_1_REAL,
    val validityHours: String = "24",
    val errors: Map<String, String> = emptyMap(),
    val reviewed: Boolean = false,
)

data class PricingPreview(val suggested: Money, val sustainable: Money, val commercial: Money, val operating: Money)

@HiltViewModel
class PricingSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val vehicles: VehicleRepository,
    private val engine: PricingEngine,
) : ViewModel() {

    private val _form = MutableStateFlow(PricingForm())
    val form: StateFlow<PricingForm> = _form.asStateFlow()
    private val _preview = MutableStateFlow<PricingPreview?>(null)
    val preview: StateFlow<PricingPreview?> = _preview.asStateFlow()
    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()
    private var previewVehicle: Vehicle? = null

    init {
        viewModelScope.launch {
            _form.value = toForm(settings.pricingSettings.first())
            val prefs = settings.appPreferences.first()
            previewVehicle = prefs.selectedVehicleId?.let { vehicles.get(it) } ?: vehicles.all().firstOrNull()
            refreshPreview()
        }
    }

    fun update(transform: (PricingForm) -> PricingForm) {
        _form.update { transform(it).copy(errors = emptyMap()) }
        refreshPreview()
    }

    fun save() {
        val (parsed, errors) = parse(_form.value)
        if (parsed == null) {
            _form.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            settings.updatePricingSettings { parsed.copy(reviewedByDriver = true) }
            _saved.send(Unit)
        }
    }

    private fun refreshPreview() {
        val (parsed, _) = parse(_form.value)
        _preview.value = parsed?.let {
            val r = engine.calculate(PREVIEW_TRIP, it.copy(reviewedByDriver = true), previewVehicle)
            PricingPreview(r.suggested, r.sustainableMinimum, r.commercial.total, r.operatingCost.total)
        }
    }

    companion object {
        /** Viagem de referência para a prévia: 10 km e 20 minutos. */
        val PREVIEW_TRIP = TripPricingInput(distanceMeters = 10_000, durationSeconds = 1_200)
        private const val MAX_VALIDITY = 720

        fun toForm(s: PricingSettings) = PricingForm(
            model = s.model,
            ratePerKm = s.commercial.ratePerKm.display(),
            ratePerMinute = s.commercial.ratePerMinute.display(),
            stopFee = s.commercial.stopFee.display(),
            waitingRate = s.commercial.waitingRatePerMinute.display(),
            freeWaiting = s.commercial.freeWaitingMinutes.toString(),
            minimumFare = s.commercial.minimumFare.display(),
            adjustment = s.commercial.adjustmentPercent.display(),
            energyPrices = s.cost.energyPrices.mapValues { it.value.display() },
            hourlyRate = s.cost.driverHourlyRate.display(),
            margin = s.cost.profitMarginPercent.display(),
            rounding = s.rounding,
            validityHours = s.quoteValidityHours.toString(),
            reviewed = s.reviewedByDriver,
        )

        /** Converte e valida; devolve (configurações, erros por campo). Campo vazio vale zero. */
        fun parse(f: PricingForm): Pair<PricingSettings?, Map<String, String>> {
            val errors = mutableMapOf<String, String>()
            fun num(key: String, text: String, allowNegative: Boolean = false, max: BigDecimal? = null): BigDecimal {
                if (text.isBlank()) return BigDecimal.ZERO
                val v = BrFormat.parseDecimal(text)
                when {
                    v == null -> errors[key] = "Número inválido"
                    !allowNegative && v < BigDecimal.ZERO -> errors[key] = "Não pode ser negativo"
                    max != null && v > max -> errors[key] = "Máximo ${BrFormat.decimal(max)}"
                    else -> return v
                }
                return BigDecimal.ZERO
            }
            val commercial = CommercialParams(
                ratePerKm = num("ratePerKm", f.ratePerKm),
                ratePerMinute = num("ratePerMinute", f.ratePerMinute),
                stopFee = num("stopFee", f.stopFee),
                waitingRatePerMinute = num("waitingRate", f.waitingRate),
                freeWaitingMinutes = f.freeWaiting.ifBlank { "0" }.toIntOrNull()?.coerceAtLeast(0) ?: 0.also { errors["freeWaiting"] = "Minutos inteiros" },
                minimumFare = num("minimumFare", f.minimumFare),
                adjustmentPercent = num("adjustment", f.adjustment, allowNegative = true, max = BigDecimal(300)),
            )
            val cost = CostParams(
                energyPrices = f.energyPrices.mapNotNull { (fuel, text) ->
                    val v = num("energy_${fuel.name}", text)
                    if (v > BigDecimal.ZERO) fuel to v else null
                }.toMap(),
                driverHourlyRate = num("hourlyRate", f.hourlyRate),
                profitMarginPercent = num("margin", f.margin, max = BigDecimal(90)),
            )
            val validity = f.validityHours.toIntOrNull()
            if (validity == null || validity !in 1..MAX_VALIDITY) errors["validity"] = "Entre 1 e $MAX_VALIDITY horas"
            if (errors.isNotEmpty()) return null to errors
            return PricingSettings(f.model, commercial, cost, f.rounding, validity!!, f.reviewed) to emptyMap()
        }
    }
}
