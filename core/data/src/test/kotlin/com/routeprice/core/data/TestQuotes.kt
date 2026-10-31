package com.routeprice.core.data

import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.RouteSummary
import com.routeprice.core.domain.model.TollInfo
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.pricing.PricingEngine
import com.routeprice.core.domain.usecase.BuildQuoteUseCase
import com.routeprice.core.domain.usecase.QuoteRequest
import com.routeprice.core.domain.usecase.TimeProvider

object TestQuotes {
    private val clock = object : TimeProvider {
        override fun nowMillis() = 1_700_000_000_000L
    }
    private val build = BuildQuoteUseCase(PricingEngine(), clock)

    fun quote(origin: String, destination: String, passenger: String = ""): TripQuote = build(
        QuoteRequest(
            origin = PlaceRef(origin),
            destination = PlaceRef(destination),
            stops = listOf(PlaceRef("Parada Única")),
            route = RouteSummary(50_000, 3_000, source = ValueSource.EXTERNAL, tolls = TollInfo.Estimated(Money.reais("9.90"))),
            vehicle = null,
            passengerName = passenger,
        ),
        PricingSettings(),
        DriverProfile(name = "Samuel"),
    )
}
