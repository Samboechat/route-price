package com.routeprice.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddRoad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.routeprice.core.designsystem.component.Banner
import com.routeprice.core.designsystem.component.BannerTone
import com.routeprice.core.designsystem.component.EmptyState
import com.routeprice.core.designsystem.component.LoadingState
import com.routeprice.core.designsystem.component.MetricTile
import com.routeprice.core.designsystem.component.QuoteListItem
import com.routeprice.core.designsystem.component.RpCard
import com.routeprice.core.designsystem.component.RpIconButton
import com.routeprice.core.designsystem.component.RpPrimaryButton
import com.routeprice.core.designsystem.component.RpTextButton
import com.routeprice.core.designsystem.component.SectionHeader
import com.routeprice.core.designsystem.component.Wordmark
import com.routeprice.core.designsystem.theme.Spacing
import com.routeprice.core.domain.util.BrFormat
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

data class HomeNavigation(
    val onNewTrip: () -> Unit,
    val onOpenQuote: (String) -> Unit,
    val onHistory: () -> Unit,
    val onSettings: () -> Unit,
    val onDriverProfile: () -> Unit,
    val onVehicles: () -> Unit,
    val onPricing: () -> Unit,
)

fun NavGraphBuilder.homeScreen(navigation: HomeNavigation) {
    composable<HomeRoute> {
        val viewModel: HomeViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) { viewModel.openQuote.collect(navigation.onOpenQuote) }
        HomeScreen(state, navigation, onCreateDemo = viewModel::createDemoQuote)
    }
}

@Composable
fun HomeScreen(state: HomeUiState, navigation: HomeNavigation, onCreateDemo: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("home"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(Modifier.weight(1f))
                RpIconButton(Icons.Outlined.History, "Histórico", navigation.onHistory)
                Spacer(Modifier.padding(horizontal = Spacing.xs))
                RpIconButton(Icons.Outlined.Settings, "Configurações", navigation.onSettings)
            }
        }
        item {
            Column(Modifier.padding(top = Spacing.lg)) {
                val name = state.driverFirstName.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
                Text(
                    "${state.greeting}$name",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Monte uma viagem e envie um orçamento profissional em poucos toques.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            RpCard(highlighted = true) {
                Text("Nova viagem", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Embarque, paradas e destino. O preço sai com a composição completa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                RpPrimaryButton("Nova viagem", navigation.onNewTrip, icon = Icons.Outlined.AddRoad, modifier = Modifier.testTag("newTrip"))
            }
        }
        item {
            AnimatedVisibility(!state.loading && !state.checklist.isComplete) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (state.checklist.needsPricingReview) {
                        Banner(
                            BannerTone.Warning,
                            "Os parâmetros de preço ainda são os exemplos iniciais. Ajuste às suas tarifas e custos.",
                            title = "Revise seus preços",
                            actionLabel = "Ajustar preços",
                            onAction = navigation.onPricing,
                        )
                    }
                    if (state.checklist.needsVehicle) {
                        Banner(
                            BannerTone.Info,
                            "Cadastre seu veículo para estimar combustível, manutenção e o preço mínimo sustentável.",
                            actionLabel = "Cadastrar veículo",
                            onAction = navigation.onVehicles,
                        )
                    }
                    if (state.checklist.needsProfile) {
                        Banner(
                            BannerTone.Info,
                            "Seu nome e telefone aparecem no orçamento enviado ao passageiro.",
                            actionLabel = "Completar perfil",
                            onAction = navigation.onDriverProfile,
                        )
                    }
                }
            }
        }
        state.summary?.let { summary ->
            item {
                SectionHeader("Resumo do mês")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricTile("Orçado", BrFormat.money(summary.quotedTotal), Modifier.weight(1f), caption = "${summary.quotesCount} orçamento(s)")
                    MetricTile("Aceito", BrFormat.money(summary.acceptedTotal), Modifier.weight(1f), caption = summary.acceptanceRatePercent?.let { "$it% de aceite" } ?: "—")
                }
            }
        }
        if (state.loading) {
            item { LoadingState("Carregando seus orçamentos") }
        } else if (state.recentQuotes.isEmpty() && state.recentTrips.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "Nenhum orçamento ainda",
                    message = "Seus orçamentos e viagens aparecem aqui. Quer ver como o passageiro recebe a proposta?",
                    actionLabel = "Ver orçamento de exemplo",
                    onAction = onCreateDemo,
                )
            }
        } else {
            if (state.recentTrips.isNotEmpty()) {
                item { SectionHeader("Últimas viagens") }
                items(state.recentTrips, key = { "trip-" + it.id }) { quote ->
                    QuoteListItem(quote, onClick = { navigation.onOpenQuote(quote.id) })
                }
            }
            if (state.recentQuotes.isNotEmpty()) {
                item { SectionHeader("Orçamentos recentes") }
                items(state.recentQuotes, key = { "quote-" + it.id }) { quote ->
                    QuoteListItem(quote, onClick = { navigation.onOpenQuote(quote.id) })
                }
            }
            item {
                RpTextButton("Ver histórico completo", navigation.onHistory, icon = Icons.AutoMirrored.Outlined.ArrowForward)
            }
        }
    }
}
