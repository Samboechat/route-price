package com.routeprice.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.routeprice.core.data.local.AppJson
import com.routeprice.core.data.local.EntityMapper
import com.routeprice.core.data.local.QuoteDao
import com.routeprice.core.data.local.VehicleDao
import com.routeprice.core.domain.model.AppPreferences
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.Vehicle
import com.routeprice.core.domain.repository.QuoteRepository
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.usecase.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import javax.inject.Inject
import javax.inject.Singleton

class RoomQuoteRepository @Inject constructor(private val dao: QuoteDao) : QuoteRepository {
    override fun observeRecent(limit: Int): Flow<List<TripQuote>> =
        dao.observeRecent(limit).map { list -> list.mapNotNull(EntityMapper::fromEntity) }

    override fun search(query: String): Flow<List<TripQuote>> =
        dao.search(EntityMapper.normalize(query)).map { list -> list.mapNotNull(EntityMapper::fromEntity) }

    override fun observe(id: String): Flow<TripQuote?> = dao.observe(id).map { it?.let(EntityMapper::fromEntity) }

    override suspend fun get(id: String): TripQuote? = dao.get(id)?.let(EntityMapper::fromEntity)

    override suspend fun all(): List<TripQuote> = dao.all().mapNotNull(EntityMapper::fromEntity)

    override suspend fun upsert(quote: TripQuote) = dao.upsert(EntityMapper.toEntity(quote))

    override suspend fun delete(id: String) = dao.delete(id)
}

class RoomVehicleRepository @Inject constructor(
    private val dao: VehicleDao,
    private val time: TimeProvider,
) : VehicleRepository {
    override fun observeAll(): Flow<List<Vehicle>> = dao.observeAll().map { it.mapNotNull(EntityMapper::fromEntity) }

    override suspend fun get(id: String): Vehicle? = dao.get(id)?.let(EntityMapper::fromEntity)

    override suspend fun all(): List<Vehicle> = dao.all().mapNotNull(EntityMapper::fromEntity)

    override suspend fun upsert(vehicle: Vehicle) = dao.upsert(EntityMapper.toEntity(vehicle, time.nowMillis()))

    override suspend fun delete(id: String) = dao.delete(id)
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Configurações em DataStore, cada grupo serializado como JSON. Valores inválidos voltam ao padrão. */
@Singleton
class DataStoreSettingsRepository internal constructor(private val store: DataStore<Preferences>) : SettingsRepository {

    @Inject constructor(@ApplicationContext context: Context) : this(context.settingsStore)

    override val driverProfile: Flow<DriverProfile> = read(DRIVER, DriverProfile.serializer(), DriverProfile())
    override val pricingSettings: Flow<PricingSettings> = read(PRICING, PricingSettings.serializer(), PricingSettings())
    override val appPreferences: Flow<AppPreferences> = read(PREFS, AppPreferences.serializer(), AppPreferences())

    override suspend fun updateDriverProfile(transform: (DriverProfile) -> DriverProfile) =
        update(DRIVER, DriverProfile.serializer(), DriverProfile(), transform)

    override suspend fun updatePricingSettings(transform: (PricingSettings) -> PricingSettings) =
        update(PRICING, PricingSettings.serializer(), PricingSettings(), transform)

    override suspend fun updateAppPreferences(transform: (AppPreferences) -> AppPreferences) =
        update(PREFS, AppPreferences.serializer(), AppPreferences(), transform)

    private fun <T> read(key: Preferences.Key<String>, serializer: KSerializer<T>, default: T): Flow<T> =
        store.data.map { prefs -> decode(prefs[key], serializer, default) }.distinctUntilChanged()

    private suspend fun <T> update(
        key: Preferences.Key<String>,
        serializer: KSerializer<T>,
        default: T,
        transform: (T) -> T,
    ) {
        store.edit { prefs ->
            val current = decode(prefs[key], serializer, default)
            prefs[key] = AppJson.encodeToString(serializer, transform(current))
        }
    }

    private fun <T> decode(raw: String?, serializer: KSerializer<T>, default: T): T =
        raw?.let { runCatching { AppJson.decodeFromString(serializer, it) }.getOrNull() } ?: default

    private companion object {
        val DRIVER = stringPreferencesKey("driver_profile")
        val PRICING = stringPreferencesKey("pricing_settings")
        val PREFS = stringPreferencesKey("app_preferences")
    }
}
