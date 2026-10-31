package com.routeprice.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.repository.QuoteRepository
import com.routeprice.core.domain.usecase.DuplicateQuoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val statusFilter: QuoteStatus? = null,
    val quotes: List<TripQuote> = emptyList(),
)

sealed interface HistoryEvent {
    data class Open(val id: String) : HistoryEvent
    data class Deleted(val quote: TripQuote) : HistoryEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: QuoteRepository,
    private val duplicate: DuplicateQuoteUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val status = MutableStateFlow<QuoteStatus?>(null)
    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val results = query.debounce(SEARCH_DEBOUNCE_MS).flatMapLatest { q ->
        if (q.isBlank()) repository.observeRecent(MAX_ITEMS) else repository.search(q.trim())
    }

    val state: StateFlow<HistoryUiState> = combine(query, status, results) { q, s, list ->
        HistoryUiState(
            loading = false,
            query = q,
            statusFilter = s,
            quotes = if (s == null) list else list.filter { it.status == s },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), HistoryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onStatusFilter(value: QuoteStatus?) {
        status.value = value
    }

    fun duplicate(quote: TripQuote) {
        viewModelScope.launch {
            val copy = duplicate.invoke(quote)
            repository.upsert(copy)
            _events.send(HistoryEvent.Open(copy.id))
        }
    }

    fun delete(quote: TripQuote) {
        viewModelScope.launch {
            repository.delete(quote.id)
            _events.send(HistoryEvent.Deleted(quote))
        }
    }

    fun restore(quote: TripQuote) {
        viewModelScope.launch { repository.upsert(quote) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val MAX_ITEMS = 500
        const val STOP_TIMEOUT = 5_000L
    }
}
