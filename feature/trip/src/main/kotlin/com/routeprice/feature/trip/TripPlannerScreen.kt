package com.routeprice.feature.trip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.routeprice.core.designsystem.QuoteCopy
import com.routeprice.core.designsystem.component.Banner
import com.routeprice.core.designsystem.component.BannerTone
import com.routeprice.core.designsystem.component.MetricTile
import com.routeprice.core.designsystem.component.RouteSketch
import com.routeprice.core.designsystem.component.RpCard
import com.routeprice.core.designsystem.component.RpNumberField
import com.routeprice.core.designsystem.component.RpPrimaryButton
import com.routeprice.core.designsystem.component.RpTextButton
import com.routeprice.core.designsystem.component.RpTextField
import com.routeprice.core.designsystem.component.SectionHeader
import com.routeprice.core.designsystem.component.SkeletonBlock
import com.routeprice.core.designsystem.component.StopMarker
import com.routeprice.core.designsystem.theme.Spacing
import com.routeprice.core.domain.model.PlaceRef
import com.routeprice.core.domain.model.TollInfo
import com.routeprice.core.domain.util.BrFormat
import kotlinx.serialization.Serializable

/** Nova viagem ([quoteId] nulo) ou edição de um orçamento existente. */
@Serializable
data class TripPlannerRoute(val quoteId: String? = null)

fun NavGraphBuilder.tripPlannerScreen(onBack: () -> Unit, onQuoteReady: (String) -> Unit, onManageVehicles: () -> Unit) {
    composable<TripPlannerRoute> {
        val viewModel: TripPlannerViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) { viewModel.quoteSaved.collect(onQuoteReady) }
        TripPlannerScreen(state, viewModel, onBack, onManageVehicles)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripPlannerScreen(
    state: TripPlannerUiState,
    vm: TripPlannerViewModel,
    onBack: () -> Unit,
    onManageVehicles: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (state.editingQuoteId == null) "Nova viagem" else "Editar viagem") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { CalculateBar(state, vm::calculate) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("tripPlanner"),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { MapSection(state, vm) }
            item { PointsCard(state, vm) }
            item { RouteSection(state, vm) }
            item { VehicleSection(state, vm::selectVehicle, onManageVehicles) }
            item { DetailsSection(state, vm::updateExtras) }
            state.validationError?.let { item { Banner(BannerTone.Error, it) } }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }

    state.search?.let { search ->
        AddressSearchSheet(
            search = search,
            mapsConfigured = state.mapsConfigured,
            online = state.online,
            onQueryChange = vm::onQueryChange,
            onSuggestion = vm::onSuggestionSelected,
            onUseTyped = vm::useTypedText,
            onUseLocation = vm::useCurrentLocation,
            onLocationDenied = vm::onLocationPermissionDenied,
            onPickOnMap = vm::startPickOnMap,
            onDismiss = vm::closeSearch,
        )
    }
}

@Composable
private fun MapSection(state: TripPlannerUiState, vm: TripPlannerViewModel) {
    val ready = state.route as? RouteState.Ready
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (state.mapsConfigured) {
            TripMap(
                origin = state.origin,
                stops = state.stops.map { it.place },
                destination = state.destination,
                routes = ready?.routes.orEmpty(),
                selectedRouteId = ready?.selectedId,
                darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                onMapClick = vm::onMapClick,
                onRouteClick = vm::selectRoute,
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
            state.pickingOnMap?.let { target ->
                Banner(
                    BannerTone.Info,
                    "Toque no mapa para definir: ${target.title()}.",
                    actionLabel = "Cancelar",
                    onAction = vm::cancelPickOnMap,
                )
            }
        } else {
            Box {
                RouteSketch(null, Modifier.fillMaxWidth().height(140.dp), state.stops.size)
            }
            Banner(
                BannerTone.Info,
                "Mapa e busca de endereços desativados: configure a MAPS_API_KEY (veja docs/CONFIGURACAO.md). " +
                    "Enquanto isso, digite os endereços e informe distância e tempo.",
            )
        }
        if (!state.online && state.mapsConfigured) Banner(BannerTone.Warning, QuoteCopy.error(com.routeprice.core.domain.model.DomainError.Offline))
    }
}

@Composable
private fun PointsCard(state: TripPlannerUiState, vm: TripPlannerViewModel) {
    // Sem mapa (ou em modo manual) endereço sem coordenada é o esperado: não alarmar.
    val warn = state.mapsConfigured && !state.manualRoute
    RpCard(Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trajeto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::swapOriginDestination) {
                Icon(Icons.Outlined.SwapVert, contentDescription = "Inverter embarque e destino")
            }
        }
        PointRow("A", "Embarque", state.origin, MaterialTheme.colorScheme.onSurface, "pointOrigin", { vm.openSearch(PointTarget.Origin) }, warn)
        state.stops.forEachIndexed { index, stop ->
            PointRow(
                marker = (index + 1).toString(),
                kind = "Parada ${index + 1}",
                place = stop.place,
                color = MaterialTheme.colorScheme.outline,
                tag = "pointStop$index",
                onClick = { vm.openSearch(PointTarget.Stop(stop.id)) },
                warnUnlocated = warn,
            ) {
                IconButton(onClick = { vm.moveStop(stop.id, -1) }, enabled = index > 0) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Mover parada ${index + 1} para cima")
                }
                IconButton(onClick = { vm.moveStop(stop.id, 1) }, enabled = index < state.stops.lastIndex) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Mover parada ${index + 1} para baixo")
                }
                IconButton(onClick = { vm.removeStop(stop.id) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remover parada ${index + 1}")
                }
            }
        }
        PointRow("B", "Destino", state.destination, MaterialTheme.colorScheme.primary, "pointDestination", { vm.openSearch(PointTarget.Destination) }, warn)
        RpTextButton("Adicionar parada", vm::addStop, icon = Icons.Outlined.Add, modifier = Modifier.testTag("addStop"))
    }
}

@Composable
private fun PointRow(
    marker: String,
    kind: String,
    place: PlaceRef?,
    color: androidx.compose.ui.graphics.Color,
    tag: String,
    onClick: () -> Unit,
    warnUnlocated: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$kind: ${place?.displayText ?: "não definido"}" }
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StopMarker(marker, color)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    place?.displayText ?: "Toque para definir",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (place == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (warnUnlocated && place != null && !place.isGeolocated) {
                    Text("Sem localização no mapa: informe distância e tempo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (actions != null) {
            Row(Modifier.fillMaxWidth().padding(start = 36.dp), horizontalArrangement = Arrangement.End) { actions() }
        }
    }
}

@Composable
private fun RouteSection(state: TripPlannerUiState, vm: TripPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Rota") {
            if (state.mapsConfigured) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Informar manualmente", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(Spacing.sm))
                    Switch(checked = state.manualRoute, onCheckedChange = vm::setManualRoute, modifier = Modifier.testTag("manualRouteSwitch"))
                }
            }
        }
        if (state.manualRoute || !state.mapsConfigured) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RpNumberField(
                    state.extras.manualDistanceKm,
                    { v -> vm.updateExtras { it.copy(manualDistanceKm = v) } },
                    "Distância",
                    Modifier.weight(1f).testTag("manualDistance"),
                    suffix = "km",
                )
                RpNumberField(
                    state.extras.manualDurationMinutes,
                    { v -> vm.updateExtras { it.copy(manualDurationMinutes = v) } },
                    "Tempo",
                    Modifier.weight(1f).testTag("manualDuration"),
                    suffix = "min",
                    decimal = false,
                )
            }
            Text(
                "Use a distância percorrida pela estrada (odômetro ou app de navegação), não a distância em linha reta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        when (val route = state.route) {
            RouteState.Idle -> Text(
                "Defina embarque e destino para traçar a rota.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RouteState.Loading -> SkeletonBlock(height = 84.dp)
            is RouteState.Failed -> Banner(
                BannerTone.Error,
                QuoteCopy.error(route.error),
                actionLabel = "Tentar de novo",
                onAction = vm::retryRoute,
            )
            is RouteState.Ready -> {
                if (route.routes.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        route.routes.forEachIndexed { i, r ->
                            FilterChip(
                                selected = r.id == route.selectedId,
                                onClick = { vm.selectRoute(r.id) },
                                label = {
                                    Text(
                                        (if (i == 0) "Recomendada" else r.description.ifBlank { "Alternativa $i" }) +
                                            " · " + BrFormat.distance(r.distanceMeters),
                                    )
                                },
                            )
                        }
                    }
                }
                val selected = route.selected
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricTile("Distância", BrFormat.distance(selected.distanceMeters), Modifier.weight(1f), Icons.Outlined.Straighten)
                    MetricTile("Tempo", BrFormat.duration(selected.durationSeconds), Modifier.weight(1f), Icons.Outlined.Schedule)
                }
                MetricTile("Pedágios", tollText(selected.tolls), Modifier.fillMaxWidth(), Icons.Outlined.Toll)
                Text(
                    QuoteCopy.routeSourceLabel(selected.source) + ", na ordem das paradas escolhidas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun tollText(t: TollInfo): String = when (t) {
    TollInfo.NotReported -> "—"
    TollInfo.PriceUnknown -> "Há pedágio, valor não informado"
    is TollInfo.Estimated -> BrFormat.money(t.amount)
}

@Composable
private fun VehicleSection(state: TripPlannerUiState, onSelect: (String?) -> Unit, onManage: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Veículo") { RpTextButton("Gerenciar", onManage) }
        if (state.vehicles.isEmpty()) {
            Banner(
                BannerTone.Info,
                "Sem veículo cadastrado: o custo de combustível e manutenção não entra no preço mínimo.",
                actionLabel = "Cadastrar veículo",
                onAction = onManage,
            )
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.vehicles.forEach { v ->
                    FilterChip(
                        selected = v.id == state.selectedVehicleId,
                        onClick = { onSelect(v.id) },
                        label = { Text("${v.name} · ${QuoteCopy.vehicleType(v.type)}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsSection(state: TripPlannerUiState, update: ((TripExtras) -> TripExtras) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val e = state.extras
    val routeTolls = (state.route as? RouteState.Ready)?.selected?.tolls
    RpCard(Modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget).clickable(role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Detalhes da viagem", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Espera, pedágios, despesas, deslocamento e passageiro",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                RpTextField(e.passengerName, { v -> update { it.copy(passengerName = v) } }, "Nome do passageiro (opcional)")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    RpNumberField(e.waitingMinutes, { v -> update { it.copy(waitingMinutes = v) } }, "Espera prevista", Modifier.weight(1f), suffix = "min", decimal = false)
                    RpNumberField(e.extraExpenses, { v -> update { it.copy(extraExpenses = v) } }, "Outras despesas", Modifier.weight(1f), prefix = "R$ ")
                }
                RpNumberField(
                    e.tolls,
                    { v -> update { it.copy(tolls = v) } },
                    "Pedágios (valor informado por você)",
                    prefix = "R$ ",
                    supportingText = when (routeTolls) {
                        is TollInfo.Estimated -> "A rota estima ${BrFormat.money(routeTolls.amount)}. Deixe vazio para usar esse valor."
                        TollInfo.PriceUnknown -> "A rota tem pedágio, mas a fonte não informou o valor. Digite o total."
                        else -> "Deixe vazio se não houver pedágio."
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    RpNumberField(e.deadheadKm, { v -> update { it.copy(deadheadKm = v) } }, "Deslocamento vazio", Modifier.weight(1f), suffix = "km")
                    RpNumberField(e.deadheadMinutes, { v -> update { it.copy(deadheadMinutes = v) } }, "Tempo vazio", Modifier.weight(1f), suffix = "min", decimal = false)
                }
                Text(
                    "Deslocamento vazio é a ida até o embarque e/ou a volta sem passageiro. Entra só nos custos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CalculateBar(state: TripPlannerUiState, onCalculate: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
            state.routeSummary?.let {
                Text(
                    "${BrFormat.distance(it.distanceMeters)} · ${BrFormat.duration(it.durationSeconds)} · ${state.stops.size} parada(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            RpPrimaryButton(
                "Calcular preço",
                onCalculate,
                icon = Icons.Outlined.Calculate,
                enabled = state.canCalculate,
                loading = state.calculating,
                modifier = Modifier.testTag("calculate"),
            )
        }
    }
}
