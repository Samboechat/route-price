package com.routeprice.feature.quote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.routeprice.core.designsystem.QuoteCopy
import com.routeprice.core.designsystem.component.Banner
import com.routeprice.core.designsystem.component.BannerTone
import com.routeprice.core.designsystem.component.EmptyState
import com.routeprice.core.designsystem.component.LoadingState
import com.routeprice.core.designsystem.component.MetricTile
import com.routeprice.core.designsystem.component.RouteSketch
import com.routeprice.core.designsystem.component.RouteTimeline
import com.routeprice.core.designsystem.component.RpCard
import com.routeprice.core.designsystem.component.RpPrimaryButton
import com.routeprice.core.designsystem.component.SectionHeader
import com.routeprice.core.designsystem.component.Wordmark
import com.routeprice.core.designsystem.component.sourceColor
import com.routeprice.core.designsystem.theme.Spacing
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.model.ValueSource
import com.routeprice.core.domain.util.BrFormat
import kotlinx.serialization.Serializable

@Serializable
data class PassengerQuoteRoute(val quoteId: String)

fun NavGraphBuilder.passengerQuoteScreen(onBack: () -> Unit) {
    composable<PassengerQuoteRoute> {
        val vm: QuoteViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        LaunchedEffect(vm) {
            vm.events.collect { if (it is QuoteEvent.Share) ShareLauncher.launch(context, it.payload) }
        }
        PassengerQuoteScreen(state, onBack, vm::share)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PassengerQuoteScreen(state: QuoteUiState, onBack: () -> Unit, onShare: (ShareFormat) -> Unit) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Visão do passageiro") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (state.quote != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    RpPrimaryButton(
                        "Enviar ao passageiro",
                        { sheet = true },
                        Modifier.navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.md),
                        icon = Icons.Outlined.Share,
                        loading = state.sharing,
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val quote = state.quote
        when {
            state.loading -> LoadingState("Carregando", Modifier.padding(padding).padding(Spacing.screen))
            quote == null -> EmptyState(Icons.AutoMirrored.Outlined.ShortText, "Orçamento não encontrado", "Ele pode ter sido excluído.", Modifier.padding(padding))
            else -> PassengerQuoteContent(quote, state.nowMillis, Modifier.padding(padding))
        }
    }
    if (sheet) ShareOptionsSheet(onDismiss = { sheet = false }, onSelect = { sheet = false; onShare(it) })
}

/** Apresentação da proposta para o passageiro: mesmos dados do PDF e da imagem. */
@Composable
fun PassengerQuoteContent(quote: TripQuote, nowMillis: Long, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize().testTag("passengerQuote"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            RpCard {
                Wordmark()
                Spacer(Modifier.height(Spacing.md))
                Text("Orçamento de viagem", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                if (quote.passengerName.isNotBlank()) Text("Para ${quote.passengerName}", style = MaterialTheme.typography.bodyLarge)
                val d = quote.driver
                if (d.name.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text("Motorista: ${d.name}" + (if (d.phone.isNotBlank()) " · ${d.phone}" else ""), style = MaterialTheme.typography.bodyMedium)
                    quote.vehicle?.let { v ->
                        Text("Veículo: ${v.name}" + (if (v.plate.isNotBlank()) " · ${v.plate}" else ""), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (d.vehicleDescriptionForPassenger.isNotBlank()) {
                        Text(d.vehicleDescriptionForPassenger, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (quote.isDemo) {
                    Spacer(Modifier.height(Spacing.sm))
                    Banner(BannerTone.Warning, "Exemplo com dados fictícios de demonstração.")
                }
            }
        }
        item {
            RpCard {
                RouteSketch(quote.route.encodedPolyline, Modifier.fillMaxWidth().height(160.dp), quote.stops.size)
                Spacer(Modifier.height(Spacing.md))
                RouteTimeline(quote.origin, quote.stops, quote.destination)
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricTile("Distância", BrFormat.distance(quote.route.distanceMeters), Modifier.weight(1f), Icons.Outlined.Straighten)
                    MetricTile("Tempo", BrFormat.duration(quote.route.durationSeconds), Modifier.weight(1f), Icons.Outlined.Schedule)
                }
                Spacer(Modifier.height(Spacing.sm))
                MetricTile("Pedágios", PassengerView.tollsText(quote), Modifier.fillMaxWidth(), Icons.Outlined.Toll)
            }
        }
        item { BreakdownCard(quote, "Composição do valor") }
        item {
            RpCard(highlighted = true) {
                Text("Valor total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(BrFormat.money(PassengerView.total(quote)), style = MaterialTheme.typography.displayMedium, modifier = Modifier.testTag("passengerTotal"))
                val expired = quote.isExpired(nowMillis)
                Text(
                    (if (expired) "Proposta expirada em " else "Proposta válida até ") + BrFormat.dateTime(quote.validUntilMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { UnderstandSection(quote) }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

@Composable
private fun UnderstandSection(quote: TripQuote) {
    RpCard {
        Text("Entenda o valor da sua viagem", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        PassengerView.kindsPresent(quote).forEach { kind ->
            Column(Modifier.padding(vertical = Spacing.xs)) {
                Text(QuoteCopy.lineLabel(kind), style = MaterialTheme.typography.titleSmall)
                Text(QuoteCopy.lineExplanation(kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        SectionHeader("De onde vêm os números")
        listOf(ValueSource.EXTERNAL, ValueSource.DRIVER_INFORMED, ValueSource.ESTIMATED).forEach { s ->
            Row(Modifier.padding(vertical = Spacing.xs), verticalAlignment = Alignment.Top) {
                Spacer(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(sourceColor(s)))
                Spacer(Modifier.width(Spacing.sm))
                Text("${QuoteCopy.sourceLabel(s)}: ${QuoteCopy.sourceExplanation(s)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            QuoteCopy.routeSourceLabel(quote.route.source) + ". Tempo e distância podem variar com o trânsito.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareOptionsSheet(onDismiss: () -> Unit, onSelect: (ShareFormat) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl)) {
            Text("Enviar orçamento", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.sm))
            ShareOption(Icons.AutoMirrored.Outlined.Chat, "WhatsApp", "Imagem do orçamento com o resumo em texto") { onSelect(ShareFormat.WHATSAPP) }
            ShareOption(Icons.Outlined.PictureAsPdf, "PDF", "Documento completo para enviar ou imprimir") { onSelect(ShareFormat.PDF) }
            ShareOption(Icons.Outlined.Image, "Imagem", "PNG em alta resolução") { onSelect(ShareFormat.IMAGE) }
            ShareOption(Icons.AutoMirrored.Outlined.ShortText, "Texto", "Resumo simples para qualquer app") { onSelect(ShareFormat.TEXT) }
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.lg))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
