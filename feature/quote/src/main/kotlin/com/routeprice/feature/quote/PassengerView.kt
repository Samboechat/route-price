package com.routeprice.feature.quote

import com.routeprice.core.designsystem.Brand
import com.routeprice.core.designsystem.QuoteCopy
import com.routeprice.core.domain.model.LineKind
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.TollInfo
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.util.BrFormat

/** Uma linha do orçamento como o passageiro vê. */
data class PassengerLine(
    val label: String,
    val amount: Money,
    val source: ValueSource,
    val detail: String?,
    val explanation: String,
)

/**
 * Monta a visão do passageiro a partir do orçamento. Usada na tela, no PDF, na imagem e no texto,
 * garantindo que todos mostrem os mesmos números e que a soma das linhas feche com o total.
 */
object PassengerView {

    fun lines(quote: TripQuote): List<PassengerLine> {
        val base = quote.result.lines.map { line ->
            PassengerLine(
                label = QuoteCopy.lineLabel(line.kind),
                amount = line.amount,
                source = line.source,
                detail = QuoteCopy.lineDetail(line),
                explanation = QuoteCopy.lineExplanation(line.kind),
            )
        }
        val negotiation = quote.negotiation
        if (!negotiation.hasAdjustment) return base
        return base + PassengerLine(
            label = if (negotiation.difference.cents < 0) "Desconto combinado" else "Ajuste combinado",
            amount = negotiation.difference,
            source = ValueSource.DRIVER_INFORMED,
            detail = null,
            explanation = "Diferença acertada entre motorista e passageiro sobre o valor calculado.",
        )
    }

    fun total(quote: TripQuote): Money = quote.finalPrice

    fun tollsText(quote: TripQuote): String = when {
        quote.input.tolls.isPositive -> BrFormat.money(quote.input.tolls) + " · " + QuoteCopy.sourceLabel(quote.input.tollsSource).lowercase()
        quote.route.tolls is TollInfo.PriceUnknown -> "Há pedágio; valor não informado"
        else -> "Sem pedágio informado"
    }

    fun kindsPresent(quote: TripQuote): List<LineKind> = quote.result.lines.map { it.kind }.distinct()

    /** Mensagem de texto para WhatsApp/SMS. */
    fun text(quote: TripQuote): String = buildString {
        if (quote.isDemo) appendLine("⚠️ EXEMPLO — dados fictícios de demonstração").appendLine()
        appendLine("*Orçamento de viagem*")
        if (quote.passengerName.isNotBlank()) appendLine("Para: ${quote.passengerName}")
        appendLine()
        appendLine("🟢 Embarque: ${quote.origin.displayText}")
        quote.stops.forEachIndexed { i, s -> appendLine("• Parada ${i + 1}: ${s.displayText}") }
        appendLine("🏁 Destino: ${quote.destination.displayText}")
        appendLine()
        appendLine("Distância: ${BrFormat.distance(quote.route.distanceMeters)} · Tempo estimado: ${BrFormat.duration(quote.route.durationSeconds)}")
        appendLine("Pedágios: ${tollsText(quote)}")
        appendLine()
        appendLine("*Composição do valor*")
        lines(quote).forEach { appendLine("${it.label}: ${BrFormat.money(it.amount)}") }
        appendLine()
        appendLine("*Valor total: ${BrFormat.money(total(quote))}*")
        appendLine("Proposta válida até ${BrFormat.dateTime(quote.validUntilMillis)}")
        val d = quote.driver
        if (d.name.isNotBlank()) {
            appendLine()
            append("Motorista: ${d.name}")
            if (d.phone.isNotBlank()) append(" · ${d.phone}")
            appendLine()
            quote.vehicle?.let { v -> appendLine("Veículo: ${v.name}${if (v.plate.isNotBlank()) " · ${v.plate}" else ""}") }
        }
        appendLine()
        append("_${Brand.SHARE_SIGNATURE}_")
    }
}
