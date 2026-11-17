package com.efreedom.mutter.feature.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.analytics.Analytics
import com.efreedom.mutter.domain.analytics.AnalyticsEvent
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.TicketHistoryItem
import com.efreedom.mutter.domain.model.TicketProduct
import com.efreedom.mutter.domain.usecase.TicketPurchaseOutcome
import com.efreedom.mutter.domain.usecase.TicketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 화면이 띄울 안내(구매 결과). 문구는 화면이 리소스로 붙인다. */
enum class TicketNotice { GRANTED, PENDING }

data class TicketsUiState(
    val entitlements: Entitlements? = null,
    val history: List<TicketHistoryItem> = emptyList(),
    /** 스토어에서 찾은 상품. 없는 상품의 버튼은 끈다(Play Console 미등록·Play 없는 기기). */
    val products: Map<String, TicketProduct> = emptyMap(),
    /** 구매 UI는 보이는데 스토어 상품을 하나도 못 받았거나 결제 테스트 대상이 아닌 계정(403 not_tester)이다 — 살 수 없는 이유를 화면이 알린다. */
    val purchaseUnavailable: Boolean = false,
    /** 다른 계정으로 산 구매(403 account_mismatch) — 구매한 계정으로 로그인하라고 화면이 알린다. */
    val purchaseAccountMismatch: Boolean = false,
    val isLoading: Boolean = false,
    /** 당겨서 새로고침 중 — 진입 로드는 표시하지 않는다. */
    val isRefreshing: Boolean = false,
    /** 구매 진행 중인 상품 id. */
    val purchasingId: String? = null,
    val notice: TicketNotice? = null,
    val error: String? = null,
)

/** 편지권 화면 — 잔액·내역·구매(1장/5장). 진입 때 남은 거래를 먼저 다시 보낸다. */
@HiltViewModel
class TicketsViewModel @Inject constructor(
    private val ticketUseCase: TicketUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(TicketsUiState())
    val state: StateFlow<TicketsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // 검증·소비 못 하고 남은 거래(앱 종료·네트워크 실패)부터 정리해 잔액에 반영한다.
            ticketUseCase.resendUnconsumed()
            load()
        }
    }

    fun load() {
        viewModelScope.launch { fetch() }
    }

    /** 당겨서 새로고침(iOS `.refreshable { load() }`) — 진입 로드와 같은 일을 표시와 함께 한다. */
    fun refresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            try {
                fetch()
            } finally {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }

    private suspend fun fetch() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val entitlements = ticketUseCase.entitlements()
            val history = runCatching { ticketUseCase.history() }.getOrDefault(_state.value.history)
            _state.value = _state.value.copy(entitlements = entitlements, history = history)
            if (entitlements.canPurchase && _state.value.products.isEmpty()) {
                val products = runCatching { ticketUseCase.products() }.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    products = products.associateBy { it.id },
                    purchaseUnavailable = products.isEmpty(),
                )
            }
        } catch (e: Throwable) {
            val message = (e as? MutterError)?.userMessage ?: MutterError.Unknown.userMessage
            _state.value = _state.value.copy(error = message)
        } finally {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun purchase(productId: String) {
        if (_state.value.purchasingId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                purchasingId = productId,
                error = null,
                notice = null,
                purchaseAccountMismatch = false,
                purchaseUnavailable = _state.value.products.isEmpty(),
            )
            try {
                when (ticketUseCase.purchase(productId)) {
                    is TicketPurchaseOutcome.Granted -> {
                        // Granted = 서버 검증·지급까지 끝났다.
                        analytics.log(AnalyticsEvent.TicketPurchase(productId))
                        _state.value = _state.value.copy(notice = TicketNotice.GRANTED)
                        load()
                    }
                    TicketPurchaseOutcome.Pending -> _state.value = _state.value.copy(notice = TicketNotice.PENDING)
                    TicketPurchaseOutcome.Cancelled -> Unit
                }
            } catch (_: MutterError.PurchaseUnavailable) {
                // 결제 테스트 대상이 아닌 계정(403 not_tester) — 상품을 못 받았을 때와 같은 현지화 문구로 보인다. 거래는 남아 있다.
                _state.value = _state.value.copy(purchaseUnavailable = true, error = null)
            } catch (_: MutterError.PurchaseAccountMismatch) {
                // 다른 계정으로 산 구매 — 거래는 남아 있어 구매한 계정으로 로그인하면 들어온다.
                _state.value = _state.value.copy(purchaseAccountMismatch = true, error = null)
            } catch (e: Throwable) {
                // 검증 실패면 거래는 남아 있다 — 다음 진입 때 다시 보낸다.
                val message = (e as? MutterError)?.userMessage ?: MutterError.Unknown.userMessage
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(purchasingId = null)
            }
        }
    }

    fun onNoticeShown() {
        _state.value = _state.value.copy(notice = null)
    }
}
