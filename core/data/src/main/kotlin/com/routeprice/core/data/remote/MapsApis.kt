package com.routeprice.core.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.security.MessageDigest

interface RoutesApi {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Body body: ComputeRoutesRequest,
        @Header("X-Goog-FieldMask") fieldMask: String = FIELD_MASK,
    ): ComputeRoutesResponse

    companion object {
        const val BASE_URL = "https://routes.googleapis.com/"
        const val FIELD_MASK = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline," +
            "routes.legs.distanceMeters,routes.legs.duration,routes.travelAdvisory.tollInfo," +
            "routes.description,routes.routeLabels"
    }
}

interface PlacesApi {
    @POST("v1/places:autocomplete")
    suspend fun autocomplete(@Body body: AutocompleteRequest): AutocompleteResponse

    @GET("v1/places/{placeId}")
    suspend fun details(
        @Path("placeId") placeId: String,
        @Query("sessionToken") sessionToken: String,
        @Query("languageCode") languageCode: String = "pt-BR",
        @Header("X-Goog-FieldMask") fieldMask: String = "id,formattedAddress,location,displayName",
    ): PlaceDetailsDto

    companion object {
        const val BASE_URL = "https://places.googleapis.com/"
    }
}

/** Fornece a chave da Google Maps Platform (vinda do BuildConfig do app; vazia se não configurada). */
fun interface MapsApiKeyProvider {
    fun apiKey(): String
}

/**
 * Envia a chave e os cabeçalhos de restrição por app Android (pacote + SHA-1 do certificado),
 * permitindo restringir a chave no Console do Google Cloud a este aplicativo assinado.
 */
class GoogleApiHeadersInterceptor(
    private val context: Context,
    private val keyProvider: MapsApiKeyProvider,
) : Interceptor {

    private val certSha1: String? by lazy { signingCertSha1(context) }

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("X-Goog-Api-Key", keyProvider.apiKey())
            .header("X-Android-Package", context.packageName)
        certSha1?.let { builder.header("X-Android-Cert", it) }
        return chain.proceed(builder.build())
    }

    companion object {
        @SuppressLint("PackageManagerGetSignatures")
        @Suppress("DEPRECATION")
        fun signingCertSha1(context: Context): String? = runCatching {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return null
            MessageDigest.getInstance("SHA-1").digest(cert).joinToString("") { "%02X".format(it) }
        }.getOrNull()
    }
}
