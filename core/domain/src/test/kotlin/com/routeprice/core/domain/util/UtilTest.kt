package com.routeprice.core.domain.util

import com.routeprice.core.domain.model.GeoPoint
import com.routeprice.core.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class UtilTest {

    @Test
    fun `decodifica polyline de referencia do Google`() {
        val points = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 1e-6)
        assertEquals(-120.2, points[0].longitude, 1e-6)
        assertEquals(40.7, points[1].latitude, 1e-6)
        assertEquals(-126.453, points[2].longitude, 1e-6)
    }

    @Test
    fun `polyline ida e volta`() {
        val pts = listOf(GeoPoint(-23.5015, -47.4526), GeoPoint(-23.5505, -46.6333), GeoPoint(-22.9068, -43.1729))
        val decoded = PolylineCodec.decode(PolylineCodec.encode(pts))
        pts.zip(decoded).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-5)
            assertEquals(a.longitude, b.longitude, 1e-5)
        }
    }

    @Test
    fun `formata dinheiro, distancia e duracao`() {
        assertEquals("R$ 1.234,56", BrFormat.money(Money(123_456)))
        assertEquals("R$ 0,05", BrFormat.money(Money(5)))
        assertEquals("−R$ 4,13", BrFormat.money(Money(-413)))
        assertEquals("850 m", BrFormat.distance(850))
        assertEquals("12,5 km", BrFormat.distance(12_500))
        assertEquals("432 km", BrFormat.distance(432_100))
        assertEquals("25 min", BrFormat.duration(1_500))
        assertEquals("1 h 05 min", BrFormat.duration(3_900))
        assertEquals("2 h", BrFormat.duration(7_200))
        assertEquals("1 min", BrFormat.duration(10))
        assertEquals("−9,5%", BrFormat.percent(-9.5))
    }

    @Test
    fun `interpreta numeros digitados`() {
        assertEquals(BigDecimal("1234.5"), BrFormat.parseDecimal("1.234,5"))
        assertEquals(BigDecimal("12.40"), BrFormat.parseDecimal("R$ 12,40"))
        assertEquals(BigDecimal("12.40"), BrFormat.parseDecimal("12.40"))
        assertEquals(BigDecimal("1234567"), BrFormat.parseDecimal("1.234.567"))
        assertNull(BrFormat.parseDecimal("abc"))
        assertNull(BrFormat.parseDecimal(""))
    }

    @Test
    fun `dinheiro arredonda meio para cima`() {
        assertEquals(Money(188), Money.of(BigDecimal("1.875")))
        assertEquals(Money(187), Money.of(BigDecimal("1.8749")))
        assertEquals(Money(-413), Money.of(BigDecimal("-4.125")))
    }
}
