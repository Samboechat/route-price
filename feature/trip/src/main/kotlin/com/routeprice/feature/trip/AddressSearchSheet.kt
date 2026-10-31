package com.routeprice.feature.trip

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.routeprice.core.designsystem.QuoteCopy
import com.routeprice.core.designsystem.component.Banner
import com.routeprice.core.designsystem.component.BannerTone
import com.routeprice.core.designsystem.component.RpTextField
import com.routeprice.core.designsystem.theme.Spacing
import com.routeprice.core.domain.model.DomainError

fun PointTarget.title(): String = when (this) {
    PointTarget.Origin -> "Embarque"
    PointTarget.Destination -> "Destino"
    is PointTarget.Stop -> "Parada"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressSearchSheet(
    search: SearchState,
    mapsConfigured: Boolean,
    online: Boolean,
    onQueryChange: (String) -> Unit,
    onSuggestion: (com.routeprice.core.domain.model.PlaceSuggestion) -> Unit,
    onUseTyped: () -> Unit,
    onUseLocation: () -> Unit,
    onLocationDenied: () -> Unit,
    onPickOnMap: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) onUseLocation() else onLocationDenied()
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(search.target.title(), style = MaterialTheme.typography.titleLarge)
            RpTextField(
                value = search.query,
                onValueChange = onQueryChange,
                label = if (mapsConfigured) "Buscar endereço ou local" else "Endereço (texto livre)",
                leadingIcon = Icons.Outlined.Search,
                imeAction = ImeAction.Search,
                modifier = Modifier.focusRequester(focus).testTag("addressQuery"),
            )
            if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (mapsConfigured) {
                SheetAction(Icons.Outlined.MyLocation, "Usar minha localização", "Pedimos a localização só agora, para este campo.") {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        onUseLocation()
                    } else {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
                SheetAction(Icons.Outlined.EditLocationAlt, "Escolher no mapa", "Toque no ponto exato no mapa.", onPickOnMap)
            }
            if (search.query.isNotBlank()) {
                SheetAction(
                    Icons.Outlined.TextFields,
                    "Usar \"${search.query.trim()}\"",
                    "Sem localização confirmada: informe distância e tempo manualmente.",
                    onUseTyped,
                )
            }
            if (!online && mapsConfigured) {
                Banner(BannerTone.Warning, QuoteCopy.error(DomainError.Offline))
            }
            search.error?.let { Banner(BannerTone.Error, QuoteCopy.error(it)) }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp)) {
                items(search.suggestions, key = { it.placeId }) { s ->
                    SheetAction(Icons.Outlined.Place, s.primaryText, s.secondaryText.ifBlank { null }) { onSuggestion(s) }
                }
            }
            Spacer(Modifier.size(Spacing.lg))
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
