package com.routeprice.core.domain.usecase

import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.TripQuote
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Resumo financeiro do mês exibido na tela inicial. */
data class MonthlySummary(
    val month: YearMonth,
    val quotesCount: Int,
    val acceptedCount: Int,
    val quotedTotal: Money,
    val acceptedTotal: Money,
    val totalDistanceMeters: Long,
) {
    /** Percentual de orçamentos aceitos no mês; nulo sem orçamentos. */
    val acceptanceRatePercent: Int?
        get() = if (quotesCount == 0) null else acceptedCount * PERCENT / quotesCount

    private companion object {
        const val PERCENT = 100
    }
}

object SummaryCalculator {
    fun monthly(quotes: List<TripQuote>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): MonthlySummary {
        val inMonth = quotes.filter {
            !it.isDemo && YearMonth.from(Instant.ofEpochMilli(it.createdAtMillis).atZone(zone)) == month
        }
        val accepted = inMonth.filter { it.status == QuoteStatus.ACCEPTED }
        return MonthlySummary(
            month = month,
            quotesCount = inMonth.size,
            acceptedCount = accepted.size,
            quotedTotal = Money.sum(inMonth.map { it.finalPrice }),
            acceptedTotal = Money.sum(accepted.map { it.finalPrice }),
            totalDistanceMeters = accepted.sumOf { it.route.distanceMeters },
        )
    }
}
