package com.routeprice.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Jornada completa sem chave de mapas (modo manual): nova viagem → endereços digitados →
 * distância e tempo → cálculo → tela do orçamento com preço sugerido.
 */
@RunWith(AndroidJUnit4::class)
class QuoteJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireManualMode() = assumeTrue("Teste do modo manual (sem MAPS_API_KEY)", BuildConfig.MAPS_API_KEY.isEmpty())


    /** Itens da LazyColumn fora da tela não existem na árvore: rola a lista até eles. */
    private fun reveal(tag: String) {
        rule.onNodeWithTag("tripPlanner").performScrollToNode(hasTestTag(tag))
    }

    private fun typeAddress(pointTag: String, text: String) {
        reveal(pointTag)
        rule.onNodeWithTag(pointTag).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("addressQuery")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("addressQuery").performTextInput(text)
        rule.onNodeWithText("Usar \"$text\"").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("addressQuery")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun criaOrcamentoNoModoManual() {
        rule.onNodeWithTag("newTrip").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("tripPlanner")).fetchSemanticsNodes().isNotEmpty() }
        typeAddress("pointOrigin", "Rua das Flores, 100")
        typeAddress("pointDestination", "Avenida Brasil, 2000")
        reveal("manualDistance")
        rule.onNodeWithTag("manualDistance").performTextInput("18,4")
        rule.onNodeWithTag("manualDuration").performTextInput("32")
        rule.onNodeWithTag("calculate").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("suggestedPrice")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Rua das Flores, 100").assertExists()
    }
}
