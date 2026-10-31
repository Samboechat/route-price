package com.routeprice.feature.quote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.routeprice.core.designsystem.theme.RoutePriceTheme
import com.routeprice.core.domain.model.DriverProfile
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.PricingSettings
import com.routeprice.core.domain.model.RouteSummary
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.pricing.PricingEngine
import com.routeprice.core.domain.usecase.BuildQuoteUseCase
import com.routeprice.core.domain.usecase.QuoteRequest
import com.routeprice.core.domain.usecase.SystemTimeProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassengerQuoteContentTest {
    @get:Rule val rule = createComposeRule()

    private val quote = BuildQuoteUseCase(PricingEngine(), SystemTimeProvider())(
        QuoteRequest(PlaceRef("Sorocaba"), PlaceRef("Campinas"), listOf(PlaceRef("Itu")),
            RouteSummary(90_000, 4_800, source = ValueSource.DRIVER_INFORMED), null, passengerName = "Maria"),
        PricingSettings(reviewedByDriver = true),
        DriverProfile("Samuel", "(15) 99999-0000"),
    )

    @Test
    fun mostraTotalTrajetoEExplicacao() {
        rule.setContent { RoutePriceTheme { PassengerQuoteContent(quote, System.currentTimeMillis()) } }
        rule.onNodeWithText("Para Maria").assertIsDisplayed()
        rule.onNodeWithText("Sorocaba").assertIsDisplayed()
        rule.onNodeWithTag("passengerQuote").performScrollToNode(hasText("Entenda o valor da sua viagem"))
        rule.onNodeWithText("Entenda o valor da sua viagem").assertIsDisplayed()
        rule.onNodeWithTag("passengerTotal").assertExists()
    }
}
