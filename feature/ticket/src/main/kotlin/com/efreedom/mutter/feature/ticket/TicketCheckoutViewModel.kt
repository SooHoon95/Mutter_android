package com.efreedom.mutter.feature.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.core.navigation.TicketCheckoutRoute
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.TicketProduct
import com.efreedom.mutter.domain.usecase.LetterAccessUseCase
import com.efreedom.mutter.domain.usecase.TicketPurchaseOutcome
import com.efreedom.mutter.domain.usecase.TicketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 시트가 보일 안내·오류. 문구는 화면이 리소스로 붙인다(서버·스토어 오류만 문자열을 그대로 싣는다). */
sealed interface TicketCheckoutMessage {
    /** 스토어 상품을 받지 못했다 — 살 수 없다. */
    data object PurchaseUnavailable : TicketCheckoutMessage
    /** 결제 대기(현금 결제 등) — 끝나면 편지권이 들어온다. */
    data object Pending : TicketCheckoutMessage
    data class Error(val text: String) : TicketCheckoutMessage
}

data class TicketCheckoutUiState(
    val entitlements: Entitlements? = null,
    /** 스토어에서 찾은 상품(id → 가격). 비어 있으면(로드 전·실패) 살 수 없다. */
    val products: Map<String, TicketProduct> = emptyMap(),
    val selectedProductId: String = TicketProduct.ONE,
    val isLoading: Boolean = true,
    /** 구매하거나 편지권을 쓰는 중 — 버튼 로딩, 시트 닫기 막기. */
    val isWorking: Boolean = false,
    /** 하려던 일이 끝났다(편지권 확보 또는 사용) → 시트가 닫히며 호출한 화면에 알린다. */
    val isDone: Boolean = false,
    val message: TicketCheckoutMessage? = null,
) {
    val tickets: Int get() = entitlements?.tickets ?: 0

    /** 지금 가진 편지권으로 바로 할 수 있는지(관리자는 차감 없음). 구매 직후나, 시트가 뜨는 사이 잔액이 생긴 경우. */
    val canUseNow: Boolean get() = entitlements?.hasTicket == true

    val canConfirm: Boolean
        get() = !isLoading && !isWorking && (canUseNow || products.containsKey(selectedProductId))
}

/**
 * 결제 안내 시트(iOS TicketCheckoutModelData 대응) — 편지권이 없는 계정이 영구 편지로 보내거나 받은 편지를 간직하려 할 때.
 * 상품을 골라 사고, 산 편지권으로 하려던 일(영구 전환·간직)까지 이어서 마친다.
 * 단, 아직 안 보낸 편지([TicketCheckoutRoute.SendPermanent])는 구매까지만 한다 — 편지권은 실제로 보낼 때 쓰인다.
 *
 * 시트는 호출한 화면의 ViewModelStore에 붙으므로 같은 인스턴스가 다음 시트에 다시 쓰인다.
 * 뜰 때 [start], 닫힐 때 [close]로 한 번의 시트 수명을 구분한다.
 */
@HiltViewModel
class TicketCheckoutViewModel @Inject constructor(
    private val ticketUseCase: TicketUseCase,
    private val letterAccessUseCase: LetterAccessUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TicketCheckoutUiState())
    val state: StateFlow<TicketCheckoutUiState> = _state.asStateFlow()

    private var route: TicketCheckoutRoute? = null
    private var loadJob: Job? = null

    /**
     * 시트가 뜰 때 — 끝내지 못한 거래를 먼저 서버에 보내(잔액 반영) 뒤 잔액과 상품을 받는다.
     * 같은 시트가 다시 그려진 것(화면 회전 등)이면 진행 중인 상태를 그대로 둔다.
     */
    fun start(route: TicketCheckoutRoute) {
        if (this.route == route) return
        this.route = route
        _state.value = TicketCheckoutUiState()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            ticketUseCase.resendUnconsumed()
            val (entitlements, products) = coroutineScope {
                val productsDeferred = async {
                    try {
                        ticketUseCase.products()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
                ticketUseCase.entitlementsOrNull() to productsDeferred.await()
            }
            val loaded = _state.value.copy(
                entitlements = entitlements,
                products = products.associateBy { it.id },
                isLoading = false,
            )
            _state.value = if (!loaded.canUseNow && loaded.products.isEmpty()) {
                loaded.copy(message = TicketCheckoutMessage.PurchaseUnavailable)
            } else {
                loaded
            }
        }
    }

    /** 시트가 닫혔다 — 다음 시트가 지난 결과(완료 표시 등)를 물려받지 않게 비운다. */
    fun close() {
        loadJob?.cancel()
        route = null
        _state.value = TicketCheckoutUiState()
    }

    fun selectProduct(productId: String) {
        if (_state.value.isWorking) return
        _state.value = _state.value.copy(selectedProductId = productId)
    }

    /** 맨 아래 버튼 — 편지권이 없으면 고른 상품을 사고, 이어서 하려던 일(영구 전환·간직, 보내기 전이면 없음)을 한다. */
    fun confirm() {
        val route = route ?: return
        if (!_state.value.canConfirm) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true, message = null)
            try {
                if (!_state.value.canUseNow) {
                    when (val outcome = ticketUseCase.purchase(_state.value.selectedProductId)) {
                        is TicketPurchaseOutcome.Granted -> {
                            val entitlements = ticketUseCase.entitlementsOrNull()
                                ?: _state.value.entitlements?.copy(tickets = outcome.tickets)
                            _state.value = _state.value.copy(entitlements = entitlements)
                        }
                        TicketPurchaseOutcome.Pending -> {
                            _state.value = _state.value.copy(message = TicketCheckoutMessage.Pending)
                            return@launch
                        }
                        // 구매를 취소하면 시트는 그대로 둔다.
                        TicketPurchaseOutcome.Cancelled -> return@launch
                    }
                }
                useTicket(route)
                _state.value = _state.value.copy(isDone = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 구매는 됐는데 사용이 실패했으면 편지권은 남아 있다 — 잔액을 다시 받아 버튼을 "편지권 1장으로 …"로 바꾼다.
                val entitlements = ticketUseCase.entitlementsOrNull() ?: _state.value.entitlements
                val text = (e as? MutterError)?.userMessage ?: MutterError.Unknown.userMessage
                _state.value = _state.value.copy(
                    entitlements = entitlements,
                    message = TicketCheckoutMessage.Error(text),
                )
            } finally {
                _state.value = _state.value.copy(isWorking = false)
            }
        }
    }

    private suspend fun useTicket(route: TicketCheckoutRoute) {
        when (route) {
            // 아직 안 보낸 편지 — 여기서 쓰지 않는다. 링크 발급·발송 때 서버가 쓴다.
            TicketCheckoutRoute.SendPermanent -> Unit
            is TicketCheckoutRoute.MakePermanent -> letterAccessUseCase.makePermanent(route.letterId)
            is TicketCheckoutRoute.KeepReceived -> letterAccessUseCase.keepReceived(route.token, route.password)
        }
    }
}
