package com.routeprice.feature.quote

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routeprice.core.domain.model.Money
import com.routeprice.core.domain.model.PricingModel
import com.routeprice.core.domain.model.QuoteStatus
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.repository.QuoteRepository
import com.routeprice.core.domain.repository.SettingsRepository
import com.routeprice.core.domain.repository.VehicleRepository
import com.routeprice.core.domain.usecase.BuildQuoteUseCase
import com.routeprice.core.domain.usecase.DuplicateQuoteUseCase
import com.routeprice.core.domain.usecase.QuoteRequest
import com.routeprice.core.domain.usecase.TimeProvider
import com.routeprice.core.domain.util.BrFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuoteUiState(
    val loading: Boolean = true,
    val quote: TripQuote? = null,
    val notFound: Boolean = false,
    val negotiatedText: String = "",
    val negotiatedError: String? = null,
    val sharing: Boolean = false,
    val nowMillis: Long = 0,
)

sealed interface QuoteEvent {
    data class Share(val payload: SharePayload) : QuoteEvent
    data class Open(val quoteId: String) : QuoteEvent
    data object Deleted : QuoteEvent
    data class Message(val text: String) : QuoteEvent
}

@HiltViewModel
class QuoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val quotes: QuoteRepository,
    private val vehicles: VehicleRepository,
    private val settings: SettingsRepository,
    private val buildQuote: BuildQuoteUseCase,
    private val duplicate: DuplicateQuoteUseCase,
    private val sharer: QuoteSharer,
    private val time: TimeProvider,
) : ViewModel() {

    private val quoteId: String = checkNotNull(savedStateHandle.get<String>("quoteId")) { "quoteId ausente" }

    private val _state = MutableStateFlow(QuoteUiState(nowMillis = time.nowMillis()))
    val state: StateFlow<QuoteUiState> = _state.asStateFlow()

    private val _events = Channel<QuoteEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            quotes.observe(quoteId).collect { q ->
                _state.update { s ->
                    s.copy(
                        loading = false,
                        quote = q,
                        notFound = q == null,
                        negotiatedText = if (s.quote == null) q?.negotiatedPrice?.let { BrFormat.decimal(it.toBigDecimal()) }.orEmpty() else s.negotiatedText,
                        nowMillis = time.nowMillis(),
                    )
                }
            }
        }
    }

    fun onNegotiatedChange(text: String) = _state.update { it.copy(negotiatedText = text, negotiatedError = null) }

    /** Registra o preço combinado; vazio volta ao sugerido. A diferença fica registrada no orçamento. */
    fun applyNegotiated() {
        val q = _state.value.quote ?: return
        val text = _state.value.negotiatedText.trim()
        val value = if (text.isEmpty()) null else BrFormat.parseDecimal(text)
        if (text.isNotEmpty() && (value == null || value.signum() <= 0)) {
            _state.update { it.copy(negotiatedError = "Digite um valor válido") }
            return
        }
        val money = value?.let(Money::of)?.takeIf { it != q.suggestedPrice }
        save(q.copy(negotiatedPrice = money, updatedAtMillis = time.nowMillis()))
        if (money == null) _state.update { it.copy(negotiatedText = "") }
    }

    fun setStatus(status: QuoteStatus) {
        val q = _state.value.quote ?: return
        save(q.copy(status = status, updatedAtMillis = time.nowMillis()))
    }

    /** Recalcula com o modelo escolhido e os parâmetros atuais, mantendo o mesmo orçamento. */
    fun recalculate(model: PricingModel? = null) {
        val q = _state.value.quote ?: return
        viewModelScope.launch {
            val vehicle = q.vehicle?.id?.let { vehicles.get(it) }
            val current = settings.pricingSettings.first()
            val updated = buildQuote(
                QuoteRequest.from(q, vehicle).copy(negotiatedPrice = q.negotiatedPrice),
                if (model != null) current.copy(model = model) else current,
                settings.driverProfile.first(),
            ).copy(isDemo = q.isDemo)
            quotes.upsert(updated)
            _events.send(QuoteEvent.Message("Orçamento recalculado com seus parâmetros atuais."))
        }
    }

    fun duplicate() {
        val q = _state.value.quote ?: return
        viewModelScope.launch {
            val copy = duplicate(q)
            quotes.upsert(copy)
            _events.send(QuoteEvent.Open(copy.id))
        }
    }

    fun delete() {
        viewModelScope.launch {
            quotes.delete(quoteId)
            _events.send(QuoteEvent.Deleted)
        }
    }

    fun share(format: ShareFormat) {
        val q = _state.value.quote ?: return
        _state.update { it.copy(sharing = true) }
        viewModelScope.launch {
            val result = runCatching { sharer.prepare(q, format) }
            _state.update { it.copy(sharing = false) }
            result.onSuccess { payload ->
                if (q.status == QuoteStatus.DRAFT) save(q.copy(status = QuoteStatus.SENT, updatedAtMillis = time.nowMillis()))
                _events.send(QuoteEvent.Share(payload))
            }.onFailure {
                _events.send(QuoteEvent.Message("Não foi possível gerar o arquivo para compartilhar."))
            }
        }
    }

    private fun save(q: TripQuote) {
        viewModelScope.launch { quotes.upsert(q) }
    }
}
