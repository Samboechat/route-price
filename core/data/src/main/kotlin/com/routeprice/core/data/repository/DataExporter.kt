package com.routeprice.core.data.repository

import com.routeprice.core.data.local.AppJson
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.Vehicle
import com.routeprice.core.domain.repository.QuoteRepository
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.usecase.QuoteCsvExporter
import com.routeprice.core.domain.usecase.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class BackupFile(
    val format: String = "route-price-backup",
    val version: Int = 1,
    val exportedAtMillis: Long,
    val driver: DriverProfile,
    val pricing: PricingSettings,
    val vehicles: List<Vehicle>,
    val quotes: List<TripQuote>,
)

/** Exportação completa dos dados locais (JSON) e do histórico (CSV). */
class DataExporter @Inject constructor(
    private val quotes: QuoteRepository,
    private val vehicles: VehicleRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) {
    suspend fun exportJson(): String = AppJson.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            exportedAtMillis = time.nowMillis(),
            driver = settings.driverProfile.first(),
            pricing = settings.pricingSettings.first(),
            vehicles = vehicles.all(),
            quotes = quotes.all(),
        ),
    )

    suspend fun exportCsv(): String = QuoteCsvExporter.export(quotes.all())
}
