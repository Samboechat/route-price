package com.routeprice.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.captureToImage
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.services.storage.TestStorage
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Percorre as telas principais e salva capturas (PNG) como saída adicional do teste,
 * para revisão visual no artifact do GitHub Actions.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }


    /** Itens da LazyColumn fora da tela não existem na árvore: rola a lista até eles. */
    private fun reveal(tag: String) {
        rule.onNodeWithTag("tripPlanner").performScrollToNode(hasTestTag(tag))
    }

    private fun typeAddress(pointTag: String, text: String) {
        reveal(pointTag)
        rule.onNodeWithTag(pointTag).performClick()
        waitTag("addressQuery")
        rule.onNodeWithTag("addressQuery").performTextInput(text)
        rule.onNodeWithText("Usar \"$text\"").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("addressQuery")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun tour() {
        assumeTrue(BuildConfig.MAPS_API_KEY.isEmpty())
        waitTag("home")
        shot("01-inicio")

        rule.onNodeWithTag("newTrip").performClick()
        waitTag("tripPlanner")
        typeAddress("pointOrigin", "Rua das Flores, 100")
        reveal("addStop")
        rule.onNodeWithTag("addStop").performClick()
        waitTag("addressQuery")
        rule.onNodeWithTag("addressQuery").performTextInput("Shopping Central")
        rule.onNodeWithText("Usar \"Shopping Central\"").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("addressQuery")).fetchSemanticsNodes().isEmpty() }
        typeAddress("pointDestination", "Avenida Brasil, 2000")
        reveal("manualDistance")
        rule.onNodeWithTag("manualDistance").performTextInput("18,4")
        rule.onNodeWithTag("manualDuration").performTextInput("32")
        Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        shot("02-nova-viagem")

        rule.onNodeWithTag("calculate").performClick()
        try {
            rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("quote")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("quote").performScrollToNode(hasTestTag("suggestedPrice"))
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            // Diagnóstico: captura e árvore de semântica do estado em que a tela ficou.
            shot("erro-apos-calcular")
            TestStorage().openOutputFile("screenshots/erro-arvore.txt").use {
                it.write(rule.onRoot(useUnmergedTree = true).printToString(maxDepth = 60).toByteArray())
            }
            throw e
        }
        shot("03-orcamento")
        rule.onNodeWithTag("quote").performScrollToNode(hasText("Composição do preço"))
        shot("04-orcamento-composicao")

        rule.onNodeWithTag("quote").performScrollToNode(hasText("Ver como o passageiro"))
        rule.onNodeWithText("Ver como o passageiro").performClick()
        waitTag("passengerQuote")
        shot("05-passageiro")
        rule.onNodeWithTag("passengerQuote").performScrollToNode(hasText("Entenda o valor da sua viagem"))
        shot("06-passageiro-entenda")
    }
}
