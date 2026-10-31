package com.routeprice.core.domain.util

import com.routeprice.core.domain.model.GeoPoint

/** Decodificador do formato "Encoded Polyline" usado pelo Google Routes. */
object PolylineCodec {

    private const val CHUNK_MASK = 0x1f
    private const val CONTINUATION = 0x20
    private const val OFFSET = 63
    private const val SHIFT = 5
    private const val PRECISION = 1e5

    fun decode(encoded: String): List<GeoPoint> {
        val points = ArrayList<GeoPoint>(encoded.length / 2)
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            val (dLat, afterLat) = readValue(encoded, index)
            val (dLng, afterLng) = readValue(encoded, afterLat)
            index = afterLng
            lat += dLat
            lng += dLng
            points += GeoPoint(lat / PRECISION, lng / PRECISION)
        }
        return points
    }

    fun encode(points: List<GeoPoint>): String = buildString {
        var prevLat = 0
        var prevLng = 0
        for (p in points) {
            val lat = Math.round(p.latitude * PRECISION).toInt()
            val lng = Math.round(p.longitude * PRECISION).toInt()
            writeValue(lat - prevLat, this)
            writeValue(lng - prevLng, this)
            prevLat = lat
            prevLng = lng
        }
    }

    private fun readValue(encoded: String, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var index = start
        var b: Int
        do {
            require(index < encoded.length) { "polyline truncada" }
            b = encoded[index++].code - OFFSET
            result = result or ((b and CHUNK_MASK) shl shift)
            shift += SHIFT
        } while (b >= CONTINUATION)
        val value = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        return value to index
    }

    private fun writeValue(value: Int, out: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= CONTINUATION) {
            out.append(((CONTINUATION or (v and CHUNK_MASK)) + OFFSET).toChar())
            v = v shr SHIFT
        }
        out.append((v + OFFSET).toChar())
    }
}
