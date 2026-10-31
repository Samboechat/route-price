package com.routeprice.core.data

import com.routeprice.core.data.local.AppJson
import com.routeprice.core.data.remote.MoneyDto
import com.routeprice.core.data.remote.RouteMapper
import com.routeprice.core.data.remote.RoutesApi
import com.routeprice.core.data.remote.TollInfoDto
import com.routeprice.core.domain.model.DomainError
import com.routeprice.core.domain.model.GeoPoint
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.TollInfo
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException

class RouteMapperTest {

    @Test
    fun `duracao no formato da API`() {
        assertEquals(1234L, RouteMapper.parseDuration("1234s"))
        assertEquals(13L, RouteMapper.parseDuration("12.5s"))
        assertEquals(0L, RouteMapper.parseDuration(null))
        assertEquals(0L, RouteMapper.parseDuration("abc"))
    }

    @Test
    fun `pedagio nunca e inventado`() {
        assertEquals(TollInfo.NotReported, RouteMapper.toll(null))
        assertEquals(TollInfo.PriceUnknown, RouteMapper.toll(TollInfoDto(estimatedPrice = null)))
        assertEquals(TollInfo.PriceUnknown, RouteMapper.toll(TollInfoDto(listOf(MoneyDto("USD", "3")))))
        assertEquals(
            TollInfo.Estimated(Money.reais("18.40")),
            RouteMapper.toll(TollInfoDto(listOf(MoneyDto("BRL", "18", 400_000_000)))),
        )
    }

    @Test
    fun `paradas desativam rotas alternativas e respeitam a ordem`() {
        val a = RouteMapper.waypoint(PlaceRef("A", location = GeoPoint(-23.5, -47.4)))!!
        val b = RouteMapper.waypoint(PlaceRef("B", placeId = "pB"))!!
        val c = RouteMapper.waypoint(PlaceRef("C", placeId = "pC"))!!
        val withStops = RouteMapper.request(a, c, listOf(b, a), null)
        assertFalse(withStops.computeAlternativeRoutes)
        assertEquals(listOf("pB", null), withStops.intermediates.map { it.placeId })
        assertTrue(RouteMapper.request(a, c, emptyList(), null).computeAlternativeRoutes)
        assertNull(RouteMapper.waypoint(PlaceRef("só texto")))
    }

    @Test
    fun `erros de rede viram erros de dominio`() {
        assertEquals(DomainError.Timeout, RouteMapper.error(SocketTimeoutException()))
        assertEquals(DomainError.Offline, RouteMapper.error(IOException()))
    }

    @Test
    fun `le resposta real da Routes API`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {"routes":[
                  {"distanceMeters":98765,"duration":"5400s","polyline":{"encodedPolyline":"_p~iF~ps|U"},
                   "legs":[{"distanceMeters":40000,"duration":"2000s"},{"distanceMeters":58765,"duration":"3400s"}],
                   "travelAdvisory":{"tollInfo":{"estimatedPrice":[{"currencyCode":"BRL","units":"12","nanos":300000000}]}},
                   "description":"SP-280","routeLabels":["DEFAULT_ROUTE"]},
                  {"distanceMeters":101000,"duration":"5900s","travelAdvisory":{}}
                ]}
                """.trimIndent(),
            ),
        )
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RoutesApi::class.java)

        val origin = RouteMapper.waypoint(PlaceRef("A", placeId = "a"))!!
        val dest = RouteMapper.waypoint(PlaceRef("B", placeId = "b"))!!
        val routes = RouteMapper.toDomain(api.computeRoutes(RouteMapper.request(origin, dest, emptyList(), null)))

        val recorded = server.takeRequest()
        assertEquals("/directions/v2:computeRoutes", recorded.path)
        assertTrue(recorded.getHeader("X-Goog-FieldMask")!!.contains("routes.travelAdvisory.tollInfo"))
        assertTrue(recorded.body.readUtf8().contains("\"extraComputations\":[\"TOLLS\"]"))

        assertEquals(2, routes.size)
        assertEquals(98_765L, routes[0].distanceMeters)
        assertEquals(5_400L, routes[0].durationSeconds)
        assertEquals(2, routes[0].legs.size)
        assertEquals(TollInfo.Estimated(Money.reais("12.30")), routes[0].tolls)
        assertTrue(routes[0].isDefault)
        assertEquals(TollInfo.NotReported, routes[1].tolls)
        server.shutdown()
    }
}
