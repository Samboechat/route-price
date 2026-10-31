package com.routeprice.feature.quote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.routeprice.core.designsystem.QuoteCopy
import com.routeprice.core.designsystem.component.Banner
import com.routeprice.core.designsystem.component.BannerTone
import com.routeprice.core.designsystem.component.DemoBadge
import com.routeprice.core.designsystem.component.EmptyState
import com.routeprice.core.designsystem.component.LoadingState
import com.routeprice.core.designsystem.component.MetricTile
import com.routeprice.core.designsystem.component.RouteSketch
import com.routeprice.core.designsystem.component.RouteTimeline
import com.routeprice.core.designsystem.component.RpCard
import com.routeprice.core.designsystem.component.RpNumberField
import com.routeprice.core.designsystem.component.RpPrimaryButton
import com.routeprice.core.designsystem.component.RpSecondaryButton
import com.routeprice.core.designsystem.component.SectionHeader
import com.routeprice.core.designsystem.component.SourceBadge
import com.routeprice.core.designsystem.component.StatusChip
import com.routeprice.core.designsystem.theme.NumericStyle
import com.routeprice.core.designsystem.theme.RpTheme
import com.routeprice.core.designsystem.theme.Spacing
import com.routeprice.core.domain.model.ModelBreakdown
import com.routeprice.core.domain.model.PricingModel
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.util.BrFormat
import kotlinx.serialization.Serializable

@Serializable
data class QuoteRoute(val quoteId: String)

data class QuoteNavigation(
    val onBack: () -> Unit,
    val onEditTrip: (String) -> Unit,
    val onPassengerView: (String) -> Unit,
    val onOpenQuote: (String) -> Unit,
)

fun NavGraphBuilder.quoteScreen(navigation: QuoteNavigation) {
    composable<QuoteRoute> {
        val vm: QuoteViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm) {
            vm.events.collect { event ->
                when (event) {
                    is QuoteEvent.Share -> ShareLauncher.launch(context, event.payload)
                    is QuoteEvent.Open -> navigation.onOpenQuote(event.quoteId)
                    QuoteEvent.Deleted -> navigation.onBack()
                    is QuoteEvent.Message -> snackbar.showSnackbar(event.text)
                }
            }
        }
        QuoteScreen(state, vm, navigation, snackbar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuoteScreen(state: QuoteUiState, vm: QuoteViewModel, navigation: QuoteNavigation, snackbar: SnackbarHostState) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var shareSheet by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Orçamento") },
                navigationIcon = { IconButton(onClick = navigation.onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Mais opções") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Editar viagem") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; state.quote?.let { navigation.onEditTrip(it.id) } })
                        DropdownMenuItem(text = { Text("Duplicar") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { menu = false; vm.duplicate() })
                        DropdownMenuItem(text = { Text("Recalcular com parâmetros atuais") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                            onClick = { menu = false; vm.recalculate() })
                        DropdownMenuItem(text = { Text("Excluir") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; confirmDelete = true })
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val quote = state.quote
        when {
            state.loading -> LoadingState("Carregando orçamento", Modifier.padding(padding).padding(Spacing.screen))
            quote == null -> EmptyState(Icons.Outlined.Delete, "Orçamento não encontrado", "Ele pode ter sido excluído.", Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).testTag("quote"),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { HeaderCard(quote, state.nowMillis) }
                item { PricesCard(quote, state, vm) }
                items(quote.result.warnings.toList()) { w -> Banner(BannerTone.Warning, QuoteCopy.warning(w)) }
                item { ModelComparison(quote, onSelect = { vm.recalculate(it) }) }
                item { BreakdownCard(quote) }
                item { StatusCard(quote.status, vm::setStatus) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        RpPrimaryButton("Compartilhar orçamento", { shareSheet = true }, icon = Icons.Outlined.Share, loading = state.sharing,
                            modifier = Modifier.testTag("share"))
                        RpSecondaryButton("Ver como o passageiro", { navigation.onPassengerView(quote.id) }, Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.Visibility)
                    }
                }
                item { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }

    if (shareSheet) {
        ShareOptionsSheet(onDismiss = { shareSheet = false }, onSelect = { shareSheet = false; vm.share(it) })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir orçamento?") },
            text = { Text("Esta ação não pode ser desfeita.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun HeaderCard(quote: TripQuote, now: Long) {
    RpCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StatusChip(quote.status)
            if (quote.isDemo) DemoBadge()
            Spacer(Modifier.weight(1f))
            val expired = quote.isExpired(now)
            Text(
                (if (expired) "Expirou em " else "Válido até ") + BrFormat.dateTime(quote.validUntilMillis),
                style = MaterialTheme.typography.bodySmall,
                color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        RouteSketch(quote.route.encodedPolyline, Modifier.fillMaxWidth().height(150.dp), quote.stops.size)
        Spacer(Modifier.height(Spacing.md))
        RouteTimeline(quote.origin, quote.stops, quote.destination)
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MetricTile("Distância", BrFormat.distance(quote.route.distanceMeters), Modifier.weight(1f), Icons.Outlined.Straighten)
            MetricTile("Tempo", BrFormat.duration(quote.route.durationSeconds), Modifier.weight(1f), Icons.Outlined.Schedule)
            MetricTile("Pedágios", if (quote.input.tolls.isPositive) BrFormat.money(quote.input.tolls) else "—", Modifier.weight(1f), Icons.Outlined.Toll)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(QuoteCopy.routeSourceLabel(quote.route.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        quote.vehicle?.let { Text("Veículo: ${it.name} · ${QuoteCopy.vehicleType(it.type)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PricesCard(quote: TripQuote, state: QuoteUiState, vm: QuoteViewModel) {
    val negotiation = quote.negotiation
    RpCard(highlighted = true) {
        Text("Preço sugerido", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(BrFormat.money(quote.suggestedPrice), style = MaterialTheme.typography.displayMedium, modifier = Modifier.testTag("suggestedPrice"))
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MetricTile("Mínimo sustentável", BrFormat.money(quote.result.sustainableMinimum), Modifier.weight(1f),
                caption = "Seus custos, sem lucro")
            MetricTile("Preço final", BrFormat.money(quote.finalPrice), Modifier.weight(1f),
                caption = if (negotiation.hasAdjustment) "Negociado" else "Igual ao sugerido")
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RpNumberField(
                state.negotiatedText,
                vm::onNegotiatedChange,
                "Preço combinado (opcional)",
                Modifier.weight(1f).testTag("negotiatedField"),
                prefix = "R$ ",
                errorText = state.negotiatedError,
            )
            RpSecondaryButton("Aplicar", vm::applyNegotiated, Modifier.padding(top = 6.dp))
        }
        if (negotiation.hasAdjustment) {
            val color = if (negotiation.difference.cents < 0) RpTheme.extended.negative else RpTheme.extended.positive
            val pct = negotiation.differencePercent?.let { " (${BrFormat.percent(it)})" }.orEmpty()
            Text(
                "Diferença registrada: ${BrFormat.money(negotiation.difference)}$pct em relação ao calculado.",
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
        if (negotiation.isBelowSustainable) {
            Spacer(Modifier.height(Spacing.sm))
            Banner(BannerTone.Error, "O preço combinado está abaixo do seu mínimo sustentável: esta viagem não cobre seus custos.")
        }
    }
}

@Composable
private fun ModelComparison(quote: TripQuote, onSelect: (PricingModel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Modelos de cálculo")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ModelTile(quote.result.commercial, quote.result.selectedModel == PricingModel.COMMERCIAL, Modifier.weight(1f)) { onSelect(PricingModel.COMMERCIAL) }
            ModelTile(quote.result.operatingCost, quote.result.selectedModel == PricingModel.OPERATING_COST, Modifier.weight(1f)) { onSelect(PricingModel.OPERATING_COST) }
        }
    }
}

@Composable
private fun ModelTile(breakdown: ModelBreakdown, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier
            .border(BorderStroke(if (selected) 2.dp else 1.dp, border), MaterialTheme.shapes.medium)
            .clickable(role = Role.RadioButton, enabled = !selected, onClick = onClick)
            .padding(Spacing.md),
    ) {
        Text(QuoteCopy.modelLabel(breakdown.model), style = MaterialTheme.typography.titleSmall)
        Text(BrFormat.money(breakdown.total), style = NumericStyle.merge(MaterialTheme.typography.titleLarge))
        Text(
            if (selected) "Em uso" else "Toque para usar",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun BreakdownCard(quote: TripQuote, title: String = "Composição do preço") {
    RpCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        PassengerView.lines(quote).forEachIndexed { i, line ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line.label, style = MaterialTheme.typography.bodyLarge)
                    line.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    SourceBadge(line.source)
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(BrFormat.money(line.amount), style = NumericStyle.merge(MaterialTheme.typography.titleMedium))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(BrFormat.money(PassengerView.total(quote)), style = NumericStyle.merge(MaterialTheme.typography.headlineSmall), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatusCard(status: QuoteStatus, onStatus: (QuoteStatus) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Situação")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            QuoteStatus.entries.forEach { s ->
                FilterChip(selected = s == status, onClick = { onStatus(s) }, label = { Text(QuoteCopy.status(s)) })
            }
        }
    }
}
