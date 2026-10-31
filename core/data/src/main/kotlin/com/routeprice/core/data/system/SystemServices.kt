package com.routeprice.core.data.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.routeprice.core.data.remote.MapsApiKeyProvider
import com.routeprice.core.domain.model.AppResult
import com.routeprice.core.domain.model.DomainError
import com.routeprice.core.domain.model.GeoPoint
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.repository.ExternalServicesStatus
import com.routeprice.core.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AndroidExternalServicesStatus @Inject constructor(
    @ApplicationContext context: Context,
    keyProvider: MapsApiKeyProvider,
) : ExternalServicesStatus {

    override val isMapsConfigured: Boolean = keyProvider.apiKey().isNotBlank()

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override val isOnline: Flow<Boolean> = callbackFlow {
        fun current(): Boolean = connectivity?.activeNetwork?.let { network ->
            connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } == true

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(current())
            }
        }
        trySend(current())
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        connectivity?.registerNetworkCallback(request, callback)
        awaitClose { connectivity?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

class FusedLocationRepository @Inject constructor(@ApplicationContext private val context: Context) : LocationRepository {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): AppResult<GeoPoint> {
        if (!hasPermission()) return AppResult.Failure(DomainError.LocationPermissionDenied)
        return try {
            val token = CancellationTokenSource()
            val location = LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                .await()
            location?.let { AppResult.Success(GeoPoint(it.latitude, it.longitude)) }
                ?: AppResult.Failure(DomainError.LocationUnavailable)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppResult.Failure(DomainError.LocationUnavailable)
        }
    }
}

/** Geocodificação reversa pelo serviço do próprio Android. */
class AndroidGeocoder @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun reverse(point: GeoPoint): AppResult<PlaceRef> {
        if (!Geocoder.isPresent()) return AppResult.Success(fallback(point))
        val geocoder = Geocoder(context, Locale.forLanguageTag("pt-BR"))
        val address = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(point.latitude, point.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()
                }
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return AppResult.Success(address?.toPlace(point) ?: fallback(point))
    }

    private fun Address.toPlace(point: GeoPoint): PlaceRef {
        val line = getAddressLine(0).orEmpty()
        val short = listOfNotNull(thoroughfare, subThoroughfare).joinToString(", ").ifBlank { line }
        return PlaceRef(label = short.ifBlank { fallback(point).label }, address = line, location = point)
    }

    private fun fallback(point: GeoPoint) = PlaceRef(
        label = "Ponto no mapa",
        address = "%.5f, %.5f".format(Locale.ROOT, point.latitude, point.longitude),
        location = point,
    )
}
