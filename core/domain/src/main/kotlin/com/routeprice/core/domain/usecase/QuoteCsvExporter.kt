package com.routeprice.core.domain.usecase

import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.util.BrFormat
import java.time.ZoneId

/** Exporta o histórico em CSV (separador ";" para abrir direto no Excel em português). */
object QuoteCsvExporter {
    private val header = listOf(
        "id", "criado_em", "status", "passageiro", "embarque", "paradas", "destino", "distancia_km",
        "duracao_min", "pedagios", "modelo", "minimo_sustentavel", "preco_sugerido", "preco_final", "diferenca",
    )

    fun export(quotes: List<TripQuote>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine(header.joinToString(";"))
        quotes.forEach { q ->
            val row = listOf(
                q.id,
                BrFormat.dateTime(q.createdAtMillis, zone),
                q.status.name,
                q.passengerName,
                q.origin.displayText,
                q.stops.joinToString(" | ") { it.displayText },
                q.destination.displayText,
                BrFormat.decimal(q.route.distanceMeters.toBigDecimal().movePointLeft(3), 2),
                (q.route.durationSeconds / SECONDS_PER_MINUTE).toString(),
                BrFormat.decimal(q.input.tolls.toBigDecimal()),
                q.result.selectedModel.name,
                BrFormat.decimal(q.result.sustainableMinimum.toBigDecimal()),
                BrFormat.decimal(q.suggestedPrice.toBigDecimal()),
                BrFormat.decimal(q.finalPrice.toBigDecimal()),
                BrFormat.decimal(q.negotiation.difference.toBigDecimal()),
            )
            appendLine(row.joinToString(";") { escape(it) })
        }
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private const val SECONDS_PER_MINUTE = 60
}
