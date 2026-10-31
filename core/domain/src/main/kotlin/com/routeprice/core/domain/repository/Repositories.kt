package com.routeprice.core.domain.repository

import com.routeprice.core.domain.model.AppPreferences
import com.routeprice.core.domain.model.AppResult
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.GeoPoint
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PlaceSuggestion
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.RouteOption
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {
    fun observeRecent(limit: Int): Flow<List<TripQuote>>
    fun search(query: String): Flow<List<TripQuote>>
    fun observe(id: String): Flow<TripQuote?>
    suspend fun get(id: String): TripQuote?
    suspend fun all(): List<TripQuote>
    suspend fun upsert(quote: TripQuote)
    suspend fun delete(id: String)
}

interface VehicleRepository {
    fun observeAll(): Flow<List<Vehicle>>
    suspend fun get(id: String): Vehicle?
    suspend fun all(): List<Vehicle>
    suspend fun upsert(vehicle: Vehicle)
    suspend fun delete(id: String)
}

interface SettingsRepository {
    val driverProfile: Flow<DriverProfile>
    val pricingSettings: Flow<PricingSettings>
    val appPreferences: Flow<AppPreferences>
    suspend fun updateDriverProfile(transform: (DriverProfile) -> DriverProfile)
    suspend fun updatePricingSettings(transform: (PricingSettings) -> PricingSettings)
    suspend fun updateAppPreferences(transform: (AppPreferences) -> AppPreferences)
}

/** Rotas reais percorridas, respeitando a ordem das paradas. */
interface RouteRepository {
    suspend fun computeRoutes(origin: PlaceRef, destination: PlaceRef, stops: List<PlaceRef>): AppResult<List<RouteOption>>
}

interface PlacesRepository {
    suspend fun autocomplete(query: String, near: GeoPoint?, sessionToken: String): AppResult<List<PlaceSuggestion>>
    suspend fun details(placeId: String, sessionToken: String): AppResult<PlaceRef>
    suspend fun reverseGeocode(point: GeoPoint): AppResult<PlaceRef>
}

interface LocationRepository {
    fun hasPermission(): Boolean
    suspend fun currentLocation(): AppResult<GeoPoint>
}

/** Estado das integrações externas, para a interface explicar o que falta. */
interface ExternalServicesStatus {
    val isMapsConfigured: Boolean
    val isOnline: Flow<Boolean>
}
