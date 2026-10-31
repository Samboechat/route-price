package com.routeprice.app

import android.app.Application
import com.routeprice.core.data.remote.MapsApiKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class RoutePriceApplication : Application()

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    /** A chave vem do BuildConfig (secrets.properties ou variável de ambiente); vazia = modo manual. */
    @Provides
    fun mapsApiKey(): MapsApiKeyProvider = MapsApiKeyProvider { BuildConfig.MAPS_API_KEY }
}
