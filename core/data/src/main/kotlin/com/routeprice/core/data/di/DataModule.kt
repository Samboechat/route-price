package com.routeprice.core.data.di

import android.content.Context
import androidx.room.Room
import com.routeprice.core.data.local.AppDatabase
import com.routeprice.core.data.local.AppJson
import com.routeprice.core.data.local.QuoteDao
import com.routeprice.core.data.local.VehicleDao
import com.routeprice.core.data.remote.GoogleApiHeadersInterceptor
import com.routeprice.core.data.remote.MapsApiKeyProvider
import com.routeprice.core.data.remote.PlacesApi
import com.routeprice.core.data.remote.RoutesApi
import com.routeprice.core.data.repository.DataStoreSettingsRepository
import com.routeprice.core.data.repository.GooglePlacesRepository
import com.routeprice.core.data.repository.GoogleRouteRepository
import com.routeprice.core.data.repository.RoomQuoteRepository
import com.routeprice.core.data.repository.RoomVehicleRepository
import com.routeprice.core.data.system.AndroidExternalServicesStatus
import com.routeprice.core.data.system.FusedLocationRepository
import com.routeprice.core.domain.repository.ExternalServicesStatus
import com.routeprice.core.domain.repository.LocationRepository
import com.routeprice.core.domain.repository.PlacesRepository
import com.routeprice.core.domain.repository.QuoteRepository
import com.routeprice.core.domain.repository.RouteRepository
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.usecase.SystemTimeProvider
import com.routeprice.core.domain.usecase.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds abstract fun quotes(impl: RoomQuoteRepository): QuoteRepository
    @Binds abstract fun vehicles(impl: RoomVehicleRepository): VehicleRepository
    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun routes(impl: GoogleRouteRepository): RouteRepository
    @Binds abstract fun places(impl: GooglePlacesRepository): PlacesRepository
    @Binds abstract fun location(impl: FusedLocationRepository): LocationRepository
    @Binds abstract fun status(impl: AndroidExternalServicesStatus): ExternalServicesStatus
    @Binds abstract fun time(impl: SystemTimeProvider): TimeProvider
}

@Module
@InstallIn(SingletonComponent::class)
object DataProviders {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun quoteDao(db: AppDatabase): QuoteDao = db.quoteDao()

    @Provides fun vehicleDao(db: AppDatabase): VehicleDao = db.vehicleDao()

    @Provides
    @Singleton
    fun okHttp(@ApplicationContext context: Context, keyProvider: MapsApiKeyProvider): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(GoogleApiHeadersInterceptor(context, keyProvider))
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun routesApi(client: OkHttpClient): RoutesApi = retrofit(client, RoutesApi.BASE_URL).create(RoutesApi::class.java)

    @Provides
    @Singleton
    fun placesApi(client: OkHttpClient): PlacesApi = retrofit(client, PlacesApi.BASE_URL).create(PlacesApi::class.java)

    private fun retrofit(client: OkHttpClient, baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private const val TIMEOUT_SECONDS = 15L
    private const val CALL_TIMEOUT_SECONDS = 25L
}
