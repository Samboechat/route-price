package com.routeprice.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.routeprice.core.designsystem.Brand
import com.routeprice.core.designsystem.QuoteCopy
import com.routeprice.core.designsystem.component.Banner
import com.routeprice.core.designsystem.component.BannerTone
import com.routeprice.core.designsystem.component.EmptyState
import com.routeprice.core.designsystem.component.MetricTile
import com.routeprice.core.designsystem.component.RpCard
import com.routeprice.core.designsystem.component.RpNumberField
import com.routeprice.core.designsystem.component.RpPrimaryButton
import com.routeprice.core.designsystem.component.RpTextButton
import com.routeprice.core.designsystem.component.RpTextField
import com.routeprice.core.designsystem.component.SectionHeader
import com.routeprice.core.designsystem.theme.Spacing
import com.routeprice.core.domain.model.FuelType
import com.routeprice.core.domain.model.PricingModel
import com.routeprice.core.domain.model.RoundingRule
import com.routeprice.core.domain.model.ThemePreference
import com.routeprice.core.domain.model.VehicleType
import com.routeprice.core.domain.util.BrFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable data object SettingsRoute
@Serializable data object DriverProfileRoute
@Serializable data object VehiclesRoute
@Serializable data class VehicleEditRoute(val vehicleId: String? = null)
@Serializable data object PricingSettingsRoute

fun NavGraphBuilder.settingsScreens(
    onBack: () -> Unit,
    onOpen: (Any) -> Unit,
) {
    composable<SettingsRoute> { SettingsHomeScreen(onBack, onOpen) }
    composable<DriverProfileRoute> { DriverProfileScreen(onBack) }
    composable<VehiclesRoute> { VehiclesScreen(onBack, onEdit = { onOpen(VehicleEditRoute(it)) }) }
    composable<VehicleEditRoute> { VehicleEditScreen(onBack) }
    composable<PricingSettingsRoute> { PricingSettingsScreen(onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    bottomBar: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

@Composable
private fun SaveBar(label: String, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        RpPrimaryButton(label, onClick, Modifier.navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.md).testTag("save"))
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- Configurações ----------------

@Composable
private fun SettingsHomeScreen(onBack: () -> Unit, onOpen: (Any) -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<ExportKind?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val kind = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val content = vm.export(kind)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } ?: error("sem saída")
                }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Dados exportados" else "Não foi possível exportar")
        }
    }

    SettingsScaffold("Configurações", onBack, snackbar) {
        item {
            RpCard {
                NavRow(Icons.Outlined.Person, "Perfil do motorista", state.driver.name.ifBlank { "Nome e telefone no orçamento" }) { onOpen(DriverProfileRoute) }
                NavRow(
                    Icons.Outlined.DirectionsCar,
                    "Veículos",
                    if (state.vehicleCount == 0) "Nenhum cadastrado" else "${state.vehicleCount} cadastrado(s)",
                ) { onOpen(VehiclesRoute) }
                NavRow(
                    Icons.Outlined.Payments,
                    "Preços e custos",
                    QuoteCopy.modelLabel(state.pricing.model) + if (state.pricing.reviewedByDriver) "" else " · revise os exemplos",
                ) { onOpen(PricingSettingsRoute) }
            }
        }
        item {
            SectionHeader("Aparência")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(ThemePreference.DARK to "Escuro", ThemePreference.LIGHT to "Claro", ThemePreference.SYSTEM to "Sistema").forEach { (t, label) ->
                    FilterChip(selected = state.prefs.theme == t, onClick = { vm.setTheme(t) }, label = { Text(label) },
                        leadingIcon = if (t == ThemePreference.DARK) ({ Icon(Icons.Outlined.DarkMode, null) }) else null)
                }
            }
        }
        item {
            SectionHeader("Integrações")
            RpCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (state.mapsConfigured) Icons.Outlined.CheckCircle else Icons.Outlined.Map, null,
                        tint = if (state.mapsConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Spacing.md))
                    Column {
                        Text("Google Maps Platform", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (state.mapsConfigured) "Chave configurada: mapa, busca de endereços e rotas ativos." else "Sem chave: rotas e endereços em modo manual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(if (state.online) "Conectado à internet" else "Sem internet: histórico e configurações seguem disponíveis",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SectionHeader("Seus dados")
            RpCard {
                NavRow(Icons.Outlined.DataObject, "Exportar backup (JSON)", "Perfil, veículos, preços e todo o histórico") {
                    pending = ExportKind.JSON
                    launcher.launch(ExportKind.JSON.fileName)
                }
                NavRow(Icons.Outlined.TableChart, "Exportar histórico (CSV)", "Abre no Excel ou Planilhas") {
                    pending = ExportKind.CSV
                    launcher.launch(ExportKind.CSV.fileName)
                }
            }
            Text(
                "Os dados ficam só neste aparelho. Endereços são enviados ao Google apenas para buscar locais e calcular rotas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        item {
            Row(Modifier.padding(vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Spacing.sm))
                Text("${Brand.NAME} · ${Brand.TAGLINE}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------- Perfil ----------------

@Composable
private fun DriverProfileScreen(onBack: () -> Unit) {
    val vm: DriverProfileViewModel = hiltViewModel()
    val f by vm.form.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.saved.collect { onBack() } }
    SettingsScaffold("Perfil do motorista", onBack, bottomBar = { SaveBar("Salvar perfil", vm::save) }) {
        item {
            Text(
                "Estas informações aparecem no orçamento enviado ao passageiro.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { RpTextField(f.name, { v -> vm.update { it.copy(name = v) } }, "Nome") }
        item { RpTextField(f.phone, { v -> vm.update { it.copy(phone = v) } }, "Telefone / WhatsApp", keyboardType = KeyboardType.Phone) }
        item { RpTextField(f.city, { v -> vm.update { it.copy(city = v) } }, "Cidade de atuação") }
        item {
            RpTextField(f.vehicleDescriptionForPassenger, { v -> vm.update { it.copy(vehicleDescriptionForPassenger = v) } },
                "Observação para o passageiro", placeholder = "Ex.: ar-condicionado, água, cadeirinha sob pedido", singleLine = false)
        }
    }
}

// ---------------- Veículos ----------------

@Composable
private fun VehiclesScreen(onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val vm: VehiclesViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    SettingsScaffold("Veículos", onBack, bottomBar = { SaveBar("Adicionar veículo") { onEdit(null) } }) {
        if (!state.loading && state.vehicles.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.DirectionsCar, "Nenhum veículo", "Cadastre consumo e custos por km para calcular o preço mínimo sustentável.")
            }
        }
        items(state.vehicles, key = { it.id }) { v ->
            RpCard(onClick = { onEdit(v.id) }, highlighted = v.id == state.defaultId) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOf(
                                QuoteCopy.vehicleType(v.type),
                                QuoteCopy.fuel(v.fuelType),
                                "${BrFormat.decimal(v.consumptionKmPerUnit, 1)} ${QuoteCopy.consumptionUnit(v.fuelType)}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (v.id == state.defaultId) {
                        Text("Padrão", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        RpTextButton("Tornar padrão", { vm.setDefault(v.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleEditScreen(onBack: () -> Unit) {
    val vm: VehicleEditViewModel = hiltViewModel()
    val f by vm.form.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.done.collect { onBack() } }
    SettingsScaffold(if (vm.isEditing) "Editar veículo" else "Novo veículo", onBack, bottomBar = { SaveBar("Salvar veículo", vm::save) }) {
        item {
            RpTextField(f.name, { v -> vm.update { it.copy(name = v) } }, "Nome (ex.: Corolla prata)", errorText = f.errors["name"], modifier = Modifier.testTag("vehicleName"))
        }
        item {
            SectionHeader("Tipo")
            ChipRow(VehicleType.entries, f.type, QuoteCopy::vehicleType) { t -> vm.update { it.copy(type = t) } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RpTextField(f.plate, { v -> vm.update { it.copy(plate = v) } }, "Placa (opcional)", Modifier.weight(1f))
                RpNumberField(f.seats, { v -> vm.update { it.copy(seats = v) } }, "Lugares", Modifier.weight(1f), decimal = false, errorText = f.errors["seats"])
            }
        }
        item {
            SectionHeader("Energia")
            ChipRow(FuelType.entries, f.fuelType, QuoteCopy::fuel) { t -> vm.update { it.copy(fuelType = t) } }
        }
        item {
            RpNumberField(f.consumption, { v -> vm.update { it.copy(consumption = v) } }, "Consumo médio", suffix = QuoteCopy.consumptionUnit(f.fuelType),
                errorText = f.errors["consumption"], supportingText = "Use a média real do seu veículo na cidade/estrada.", modifier = Modifier.testTag("vehicleConsumption"))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RpNumberField(
                    f.maintenancePerKm,
                    { v -> vm.update { it.copy(maintenancePerKm = v) } },
                    "Manutenção por km",
                    Modifier.weight(1f),
                    prefix = "R$ ",
                    errorText = f.errors["maintenance"],
                )
                RpNumberField(f.wearPerKm, { v -> vm.update { it.copy(wearPerKm = v) } }, "Desgaste por km", Modifier.weight(1f), prefix = "R$ ", errorText = f.errors["wear"])
            }
            Text("Manutenção: revisões, óleo, peças. Desgaste: pneus e depreciação. Divida o gasto anual pelos km rodados no ano.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            RpNumberField(f.rateMultiplier, { v -> vm.update { it.copy(rateMultiplier = v) } }, "Multiplicador da tarifa comercial", suffix = "×",
                errorText = f.errors["multiplier"], supportingText = "1 = tarifa normal. Ex.: 1,2 cobra 20% a mais por km e minuto com este veículo.")
        }
        if (vm.isEditing) item { RpTextButton("Excluir veículo", { confirmDelete = true }) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir veículo?") },
            text = { Text("Orçamentos antigos continuam com os dados do veículo salvos.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

// ---------------- Preços ----------------

@Composable
private fun PricingSettingsScreen(onBack: () -> Unit) {
    val vm: PricingSettingsViewModel = hiltViewModel()
    val f by vm.form.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.saved.collect { onBack() } }
    SettingsScaffold("Preços e custos", onBack, bottomBar = { SaveBar("Salvar parâmetros", vm::save) }) {
        if (!f.reviewed) {
            item { Banner(BannerTone.Warning, "Os valores abaixo são exemplos iniciais, não tarifas oficiais. Ajuste à sua realidade e salve.") }
        }
        item {
            SectionHeader("Modelo usado no preço sugerido")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PricingModel.entries.forEach { m ->
                    val sel = f.model == m
                    Column(
                        Modifier.weight(1f)
                            .border(
                                BorderStroke(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                MaterialTheme.shapes.medium,
                            )
                            .clickable(role = Role.RadioButton) { vm.update { it.copy(model = m) } }
                            .padding(Spacing.md),
                    ) {
                        Text(QuoteCopy.modelLabel(m), style = MaterialTheme.typography.titleSmall)
                        Text(QuoteCopy.modelDescription(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("O preço sugerido nunca fica abaixo do mínimo sustentável (seus custos sem lucro).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
        }
        preview?.let { p ->
            item {
                SectionHeader("Prévia · viagem de 10 km e 20 min")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricTile("Sugerido", BrFormat.money(p.suggested), Modifier.weight(1f))
                    MetricTile("Mínimo", BrFormat.money(p.sustainable), Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricTile("Comercial", BrFormat.money(p.commercial), Modifier.weight(1f))
                    MetricTile("Custos + margem", BrFormat.money(p.operating), Modifier.weight(1f))
                }
            }
        }
        item { SectionHeader("Tarifa comercial (modelo A)") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RpNumberField(
                    f.ratePerKm,
                    { v -> vm.update { it.copy(ratePerKm = v) } },
                    "Por km",
                    Modifier.weight(1f).testTag("ratePerKm"),
                    prefix = "R$ ",
                    errorText = f.errors["ratePerKm"],
                )
                RpNumberField(
                    f.ratePerMinute,
                    { v -> vm.update { it.copy(ratePerMinute = v) } },
                    "Por minuto",
                    Modifier.weight(1f),
                    prefix = "R$ ",
                    errorText = f.errors["ratePerMinute"],
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RpNumberField(f.stopFee, { v -> vm.update { it.copy(stopFee = v) } }, "Por parada", Modifier.weight(1f), prefix = "R$ ", errorText = f.errors["stopFee"])
                RpNumberField(
                    f.minimumFare,
                    { v -> vm.update { it.copy(minimumFare = v) } },
                    "Tarifa mínima",
                    Modifier.weight(1f),
                    prefix = "R$ ",
                    errorText = f.errors["minimumFare"],
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RpNumberField(
                    f.waitingRate,
                    { v -> vm.update { it.copy(waitingRate = v) } },
                    "Espera por minuto",
                    Modifier.weight(1f),
                    prefix = "R$ ",
                    errorText = f.errors["waitingRate"],
                )
                RpNumberField(
                    f.freeWaiting,
                    { v -> vm.update { it.copy(freeWaiting = v) } },
                    "Espera grátis",
                    Modifier.weight(1f),
                    suffix = "min",
                    decimal = false,
                    errorText = f.errors["freeWaiting"],
                )
            }
        }
        item {
            RpTextField(f.adjustment, { v -> vm.update { it.copy(adjustment = v) } }, "Ajuste comercial", suffix = "%",
                keyboardType = KeyboardType.Text, errorText = f.errors["adjustment"], supportingText = "Positivo ou negativo, ex.: 10 ou -5")
        }
        item { SectionHeader("Custos operacionais (modelo B)") }
        item {
            Text("Preço da energia por tipo (informe o que você paga hoje):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FuelType.entries.chunked(2).forEach { pair ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    pair.forEach { fuel ->
                        RpNumberField(
                            f.energyPrices[fuel].orEmpty(),
                            { v -> vm.update { it.copy(energyPrices = it.energyPrices + (fuel to v)) } },
                            "${QuoteCopy.fuel(fuel)} (R$/${fuel.unit})",
                            Modifier.weight(1f),
                            prefix = "R$ ",
                            errorText = f.errors["energy_${fuel.name}"],
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RpNumberField(
                    f.hourlyRate,
                    { v -> vm.update { it.copy(hourlyRate = v) } },
                    "Valor da sua hora",
                    Modifier.weight(1f),
                    prefix = "R$ ",
                    errorText = f.errors["hourlyRate"],
                )
                RpNumberField(f.margin, { v -> vm.update { it.copy(margin = v) } }, "Margem de lucro", Modifier.weight(1f), suffix = "%", errorText = f.errors["margin"])
            }
        }
        item {
            SectionHeader("Arredondamento e validade")
            ChipRow(RoundingRule.entries, f.rounding, QuoteCopy::rounding) { r -> vm.update { it.copy(rounding = r) } }
        }
        item {
            RpNumberField(
                f.validityHours,
                { v -> vm.update { it.copy(validityHours = v) } },
                "Validade da proposta",
                suffix = "horas",
                decimal = false,
                errorText = f.errors["validity"],
            )
        }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}
