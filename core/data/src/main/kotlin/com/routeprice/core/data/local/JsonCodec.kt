package com.routeprice.core.data.local

import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.Vehicle
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

/** JSON tolerante a campos novos/antigos, para o histórico sobreviver a atualizações do app. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

object EntityMapper {
    fun toEntity(q: TripQuote) = QuoteEntity(
        id = q.id,
        createdAt = q.createdAtMillis,
        updatedAt = q.updatedAtMillis,
        status = q.status.name,
        searchText = normalize(
            listOf(q.origin.displayText, q.destination.displayText, q.passengerName, q.notes) +
                q.stops.map { it.displayText },
        ),
        finalPriceCents = q.finalPrice.cents,
        payload = AppJson.encodeToString(TripQuote.serializer(), q),
    )

    fun fromEntity(e: QuoteEntity): TripQuote? =
        runCatching { AppJson.decodeFromString(TripQuote.serializer(), e.payload) }.getOrNull()

    fun toEntity(v: Vehicle, now: Long) =
        VehicleEntity(id = v.id, name = v.name, payload = AppJson.encodeToString(Vehicle.serializer(), v), updatedAt = now)

    fun fromEntity(e: VehicleEntity): Vehicle? =
        runCatching { AppJson.decodeFromString(Vehicle.serializer(), e.payload) }.getOrNull()

    /** Minúsculas e sem acentos, para "sao paulo" encontrar "São Paulo". */
    fun normalize(parts: List<String>): String = normalize(parts.joinToString(" "))

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .trim()
}
