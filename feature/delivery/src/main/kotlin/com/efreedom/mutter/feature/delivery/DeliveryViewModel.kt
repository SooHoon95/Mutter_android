package com.efreedom.mutter.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.core.navigation.TicketCheckoutRoute
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.LetterAccessKind
import com.efreedom.mutter.domain.model.LetterAccessState
import com.efreedom.mutter.domain.model.LetterReader
import com.efreedom.mutter.domain.model.SendChoice
import com.efreedom.mutter.domain.usecase.DeliveryUseCase
import com.efreedom.mutter.domain.usecase.LetterAccessUseCase
import com.efreedom.mutter.domain.usecase.PermanentSendUseCase
import com.efreedom.mutter.domain.usecase.TicketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DeliveryUiState(
    val links: List<DeliveryLink> = emptyList(),
    /** 암호 기본 ON (기본값이 프라이버시). */
    val usePassword: Boolean = true,
    val password: String = "",
    val useReveal: Boolean = false,
    val revealAt: Instant = Instant.now().plusSeconds(3600),
    val isLoading: Boolean = false,
    /** 목록 새로고침(당겨서 새로고침) 진행 표시 — 발급/취소 버튼의 isLoading과 분리. */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    /** 방금 발급한 링크. 암호·예약 여부가 공유 문구에 쓰인다. */
    val lastIssuedLink: DeliveryLink? = null,
    /**
     * 편지 접근 상태(영구 편지권). null이면 서버 반영 전이거나 조회 실패 — 열람자·사본·영구화 UI를 숨겨 기존 화면 그대로.
     */
    val access: LetterAccessState? = null,
    val entitlements: Entitlements? = null,
    /** 열람자(처음 연 순). 조회 실패면 비어 있다. */
    val readers: List<LetterReader> = emptyList(),
    val isMakingPermanent: Boolean = false,
    val isDuplicating: Boolean = false,
    /** 결제 안내 시트 — 영구 편지로 보내거나 만들려는데 편지권이 없을 때 띄운다(null이면 닫힘). */
    val checkoutRoute: TicketCheckoutRoute? = null,
    /** 방금 만든 사본 id — 화면이 그 편지의 발송 화면으로 이어 간다. */
    val duplicatedLetterId: String? = null,
    /** 보내기 전 선택에서 무료 기간을 골랐다. 이 화면에 머무는 동안만 기억한다(저장 안 함). */
    val choseFreeDays: Boolean = false,
    /** 보내기 전 선택에서 영구 편지를 골랐다 — **고른 것만 기억한다.** 편지권은 링크를 만들 때 서버가 쓴다. */
    val chosePermanent: Boolean = false,
) {
    /** 보내기 전 선택 카드. 보이는 동안 발급 폼과 "영구 편지로 만들기"를 숨긴다(카드가 대신한다). */
    val showsSendChoice: Boolean
        get() = SendChoice.shows(
            access,
            entitlements,
            hasLinks = links.isNotEmpty(),
            hasChosen = choseFreeDays || chosePermanent,
        )

    /** 영구 편지를 골랐고 아직 링크를 만들지 않았다 — 발급 폼 위에 "보낼 때 편지권 1장이 사용돼요"와 "다시 고르기"를 보인다. */
    val showsPermanentPending: Boolean
        get() = chosePermanent && access?.kind == LetterAccessKind.OPEN && links.isEmpty()

    val canIssue: Boolean get() = if (usePassword) password.isNotEmpty() else true

    /**
     * "영구 편지로 만들기" — 편지권 UI가 보이는 계정이고, 아직 영구가 아니며 되살릴 수 있는 편지 중 **이미 보낸(링크가 있는) 편지만.**
     * 아직 안 보낸 편지는 보내기 전 선택으로 고른다(누르는 즉시 편지권이 쓰이면 보내기 전에 차감된다).
     */
    val canMakePermanent: Boolean
        get() = entitlements?.showsTicketUi == true && access != null && links.isNotEmpty() &&
            access.kind != LetterAccessKind.PERMANENT && access.kind != LetterAccessKind.GONE

    /** 링크 발급 안내(받는 분이 처음 연 날부터 N일) — 기간 제한이 있는 편지에만. 영구 편지를 골랐으면 없다. */
    val freeDaysNotice: Int?
        get() = entitlements?.freeDays?.takeIf {
            access != null && access.kind != LetterAccessKind.PERMANENT && !chosePermanent
        }
}

/** iOS DeliveryModelData 대응. 전달 링크 발급/관리 — 암호 기본 ON, 예약공개, revoke. */
@HiltViewModel
class DeliveryViewModel @Inject constructor(
    private val deliveryUseCase: DeliveryUseCase,
    private val letterAccessUseCase: LetterAccessUseCase,
    private val ticketUseCase: TicketUseCase,
    private val permanentSendUseCase: PermanentSendUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryUiState())
    val state: StateFlow<DeliveryUiState> = _state.asStateFlow()

    private var letterId: String = ""

    /** Screen의 LaunchedEffect에서 한 번 호출. */
    fun start(letterId: String) {
        if (this.letterId == letterId) return
        this.letterId = letterId
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                val links = deliveryUseCase.links(letterId)
                _state.value = _state.value.copy(links = links)
                loadAccess()
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }

    fun issue() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val st = _state.value
                val password = if (st.usePassword) st.password else null
                val revealAt = if (st.useReveal) st.revealAt else null
                // 영구 편지를 골랐으면 서버가 링크 발급과 편지권 1장 사용을 한 트랜잭션으로 처리한다.
                val link = if (st.showsPermanentPending) {
                    permanentSendUseCase.issueLink(letterId, password, revealAt)
                } else {
                    deliveryUseCase.issue(letterId, password, revealAt)
                }
                _state.value = _state.value.copy(
                    lastIssuedLink = link,
                    password = "",
                )
                load()
            } catch (e: MutterError.NoTicket) {
                // 영구 편지로 링크를 만들려는데 편지권이 없어졌다(다른 기기에서 사용 등) — 살 수 있는 계정은 결제 안내 시트(구매)로.
                _state.value = if (_state.value.entitlements?.canPurchase == true) {
                    _state.value.copy(checkoutRoute = TicketCheckoutRoute.SendPermanent)
                } else {
                    _state.value.copy(error = e.userMessage)
                }
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "발급할 수 없었어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun revoke(token: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                deliveryUseCase.revoke(token)
                load()
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "취소할 수 없었어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 상태·잔액·열람자. 셋 다 서버 반영 전에는 실패한다 → 조용히 빈 값(오류 표시 없음).
     */
    private suspend fun loadAccess() {
        val (access, entitlements, readers) = coroutineScope {
            val accessDeferred = async { letterAccessUseCase.accessStates(listOf(letterId))[letterId] }
            val entitlementsDeferred = async { ticketUseCase.entitlementsOrNull() }
            val readersDeferred = async {
                try {
                    letterAccessUseCase.readers(letterId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    emptyList()
                }
            }
            Triple(accessDeferred.await(), entitlementsDeferred.await(), readersDeferred.await())
        }
        _state.value = _state.value.copy(access = access, entitlements = entitlements, readers = readers)
    }

    /**
     * 보내기 전 선택 — 영구 편지를 고르고 버튼을 눌렀다.
     * 편지권이 있으면(관리자 포함) 선택만 기억하고 발급 폼을 연다. 없으면 결제 안내 시트(구매)를 띄운다.
     * **여기서는 편지권을 쓰지 않는다** — 고르고 나서 링크를 만들지 않으면 편지권만 나가기 때문이다.
     */
    fun choosePermanent() {
        _state.value = if (_state.value.entitlements?.hasTicket == true) {
            _state.value.copy(chosePermanent = true)
        } else {
            _state.value.copy(checkoutRoute = TicketCheckoutRoute.SendPermanent)
        }
    }

    /** "다시 고르기" — 선택 카드로 돌아간다. */
    fun chooseAgain() {
        _state.value = _state.value.copy(chosePermanent = false, choseFreeDays = false)
    }

    /**
     * 이미 보낸 편지의 "영구 편지로 만들기 · 편지권 1장" — 문구가 편지권 1장을 말하므로 확인 없이,
     * 편지권이 있으면(관리자 포함) 바로 쓰고 없으면 결제 안내 시트(구매 후 사용)를 띄운다.
     */
    fun makeSentLetterPermanent() {
        if (_state.value.isMakingPermanent) return
        if (_state.value.entitlements?.hasTicket == true) {
            viewModelScope.launch { makePermanent() }
        } else {
            _state.value = _state.value.copy(checkoutRoute = TicketCheckoutRoute.MakePermanent(letterId))
        }
    }

    /** 편지권 1장으로 영구화(make_letter_permanent). */
    private suspend fun makePermanent() {
        _state.value = _state.value.copy(isMakingPermanent = true, error = null)
        try {
            letterAccessUseCase.makePermanent(letterId)
            loadAccess()
        } catch (e: MutterError.NoTicket) {
            // 화면의 잔액이 낡았다(다른 기기에서 사용 등) — 살 수 있는 계정이면 결제 안내 시트로.
            _state.value = if (_state.value.entitlements?.canPurchase == true) {
                _state.value.copy(checkoutRoute = TicketCheckoutRoute.MakePermanent(letterId))
            } else {
                _state.value.copy(error = e.userMessage)
            }
        } catch (e: Throwable) {
            val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
            _state.value = _state.value.copy(error = message)
        } finally {
            _state.value = _state.value.copy(isMakingPermanent = false)
        }
    }

    /**
     * 결제 안내 시트가 닫혔다. 하려던 일을 마쳤으면 다시 불러와 잔액·상태를 맞추고,
     * 보내기 전 구매였으면 영구 편지를 고른 것으로 넘어간다(발급 폼이 열린다).
     * 마치지 않고 닫았어도 잔액은 다시 받는다 — 결제 대기가 그 사이 끝났을 수 있다(카드의 보유 수).
     */
    fun checkoutFinished(completed: Boolean) {
        val route = _state.value.checkoutRoute
        _state.value = _state.value.copy(checkoutRoute = null)
        if (completed) {
            if (route == TicketCheckoutRoute.SendPermanent) _state.value = _state.value.copy(chosePermanent = true)
            load()
        } else {
            viewModelScope.launch {
                ticketUseCase.entitlementsOrNull()?.let { _state.value = _state.value.copy(entitlements = it) }
            }
        }
    }

    /** 보내기 전 선택 — 무료 기간. 발급 폼을 연다. */
    fun chooseFreeDays() {
        _state.value = _state.value.copy(choseFreeDays = true)
    }

    /** 같은 내용으로 새 편지를 만든다(duplicate-letter). 새 편지는 링크가 없어 발송 화면으로 이어 간다. */
    fun duplicate() {
        if (_state.value.isDuplicating) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isDuplicating = true, error = null)
            try {
                val newId = letterAccessUseCase.duplicate(letterId)
                _state.value = _state.value.copy(duplicatedLetterId = newId)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isDuplicating = false)
            }
        }
    }

    fun onDuplicatedHandled() {
        _state.value = _state.value.copy(duplicatedLetterId = null)
    }

    fun setUsePassword(value: Boolean) {
        _state.value = _state.value.copy(usePassword = value)
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(password = value)
    }

    fun setUseReveal(value: Boolean) {
        _state.value = _state.value.copy(useReveal = value)
    }

    fun setRevealAt(value: Instant) {
        _state.value = _state.value.copy(revealAt = value)
    }
}
