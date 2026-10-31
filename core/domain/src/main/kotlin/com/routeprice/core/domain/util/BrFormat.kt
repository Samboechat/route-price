package com.routeprice.core.domain.util

import com.routeprice.core.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Formatação no padrão brasileiro, sem depender do locale do aparelho. */
object BrFormat {
    private val locale: Locale = Locale.forLanguageTag("pt-BR")
    private val symbols = DecimalFormatSymbols(locale).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private fun pattern(p: String) = DecimalFormat(p, symbols).apply { roundingMode = RoundingMode.HALF_UP }

    fun money(value: Money): String {
        val sign = if (value.cents < 0) "−" else ""
        return "${sign}R$ " + pattern("#,##0.00").format(abs(value.cents) / 100.0)
    }

    /** "R$ 1.234,56" sem o símbolo: útil para campos de edição. */
    fun decimal(value: BigDecimal, maxDecimals: Int = 2): String {
        val p = if (maxDecimals == 0) "#,##0" else "#,##0." + "#".repeat(maxDecimals)
        return pattern(p).format(value)
    }

    fun rate(value: BigDecimal): String = "R$ " + pattern("#,##0.00##").format(value)

    fun distance(meters: Long): String = when {
        meters < 1000 -> "$meters m"
        meters < 100_000 -> pattern("#,##0.0").format(meters / 1000.0) + " km"
        else -> pattern("#,##0").format(meters / 1000.0) + " km"
    }

    fun duration(seconds: Long): String {
        val totalMinutes = ((seconds + 30) / 60).coerceAtLeast(if (seconds > 0) 1 else 0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "$minutes min"
            minutes == 0L -> "$hours h"
            else -> "$hours h ${minutes.toString().padStart(2, '0')} min"
        }
    }

    fun percent(value: Double): String {
        val sign = if (value > 0) "+" else if (value < 0) "−" else ""
        return sign + pattern("#,##0.#").format(abs(value)) + "%"
    }

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale).format(Instant.ofEpochMilli(millis).atZone(zone))

    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", locale).format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Interpreta texto digitado ("1.234,5", "12,40", "12.40") como número. Nulo se inválido. */
    fun parseDecimal(text: String): BigDecimal? {
        val cleaned = text.trim().replace("R$", "").replace(" ", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val normalized = when {
            cleaned.contains(',') -> cleaned.replace(".", "").replace(',', '.')
            cleaned.count { it == '.' } > 1 -> cleaned.replace(".", "")
            else -> cleaned
        }
        return normalized.toBigDecimalOrNull()
    }
}
