package com.routeprice.feature.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.routeprice.core.designsystem.QuoteCopy
import com.routeprice.core.designsystem.component.EmptyState
import com.routeprice.core.designsystem.component.LoadingState
import com.routeprice.core.designsystem.component.QuoteListItem
import com.routeprice.core.designsystem.component.RpTextField
import com.routeprice.core.designsystem.theme.Spacing
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.TripQuote
import kotlinx.serialization.Serializable

@Serializable
data object HistoryRoute

fun NavGraphBuilder.historyScreen(onBack: () -> Unit, onOpenQuote: (String) -> Unit, onNewTrip: () -> Unit) {
    composable<HistoryRoute> {
        val vm: HistoryViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm) {
            vm.events.collect { event ->
                when (event) {
                    is HistoryEvent.Open -> onOpenQuote(event.id)
                    is HistoryEvent.Deleted -> {
                        val result = snackbar.showSnackbar("Orçamento excluído", "Desfazer", duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) vm.restore(event.quote)
                    }
                }
            }
        }
        HistoryScreen(state, snackbar, onBack, onOpenQuote, onNewTrip, vm::onQueryChange, vm::onStatusFilter, vm::duplicate, vm::delete)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    state: HistoryUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNewTrip: () -> Unit,
    onQuery: (String) -> Unit,
    onStatus: (QuoteStatus?) -> Unit,
    onDuplicate: (TripQuote) -> Unit,
    onDelete: (TripQuote) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Histórico") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("history"),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                RpTextField(state.query, onQuery, "Buscar por endereço, passageiro ou nota", leadingIcon = Icons.Outlined.Search,
                    modifier = Modifier.testTag("historySearch"))
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = state.statusFilter == null, onClick = { onStatus(null) }, label = { Text("Todos") })
                    QuoteStatus.entries.forEach { s ->
                        FilterChip(selected = state.statusFilter == s, onClick = { onStatus(s) }, label = { Text(QuoteCopy.status(s)) })
                    }
                }
            }
            when {
                state.loading -> item { LoadingState("Carregando histórico") }
                state.quotes.isEmpty() && (state.query.isNotBlank() || state.statusFilter != null) -> item {
                    EmptyState(Icons.Outlined.SearchOff, "Nada encontrado", "Tente outro termo ou remova o filtro.")
                }
                state.quotes.isEmpty() -> item {
                    EmptyState(Icons.Outlined.ReceiptLong, "Histórico vazio", "Os orçamentos que você criar ficam salvos aqui, mesmo offline.",
                        actionLabel = "Nova viagem", onAction = onNewTrip)
                }
                else -> items(state.quotes, key = { it.id }) { quote ->
                    QuoteListItem(quote, onClick = { onOpen(quote.id) }) {
                        ItemMenu(onDuplicate = { onDuplicate(quote) }, onDelete = { onDelete(quote) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemMenu(onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "Ações do orçamento") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Duplicar") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { open = false; onDuplicate() })
        DropdownMenuItem(text = { Text("Excluir") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { open = false; onDelete() })
    }
}
