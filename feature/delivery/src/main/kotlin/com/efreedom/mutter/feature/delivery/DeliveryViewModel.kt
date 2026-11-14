package com.efreedom.mutter.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.LetterAccessKind
import com.efreedom.mutter.domain.model.LetterAccessState
import com.efreedom.mutter.domain.model.LetterKeepRequest
import com.efreedom.mutter.domain.model.LetterReader
import com.efreedom.mutter.domain.model.SendChoice
import com.efreedom.mutter.domain.usecase.DeliveryUseCase
import com.efreedom.mutter.domain.usecase.LetterAccessUseCase
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
    /** 간직 요청자(요청한 순). 요청 수가 0이거나(영구가 되면 0) 조회 실패면 비어 있다. */
    val keepRequests: List<LetterKeepRequest> = emptyList(),
    val isMakingPermanent: Boolean = false,
    val isDuplicating: Boolean = false,
    /** 편지권이 없어 구매 화면으로 보내야 한다(NO_TICKET). */
    val needTickets: Boolean = false,
    /** 방금 만든 사본 id — 화면이 그 편지의 발송 화면으로 이어 간다. */
    val duplicatedLetterId: String? = null,
    /** 보내기 전 선택에서 무료 기간을 골랐다. 이 화면에 머무는 동안만 기억한다(저장 안 함). */
    val choseFreeDays: Boolean = false,
) {
    /** 보내기 전 선택 카드. 보이는 동안 발급 폼과 "영구 편지로 만들기"를 숨긴다(카드가 대신한다). */
    val showsSendChoice: Boolean
        get() = SendChoice.shows(access, entitlements, hasLinks = links.isNotEmpty(), choseFreeDays = choseFreeDays)

    val canIssue: Boolean get() = if (usePassword) password.isNotEmpty() else true

    /** "영구 편지로 만들기" — 편지권 UI가 보이는 계정이고, 아직 영구가 아니며 되살릴 수 있는 편지. */
    val canMakePermanent: Boolean
        get() = entitlements?.showsTicketUi == true && access != null && !showsSendChoice &&
            access.kind != LetterAccessKind.PERMANENT && access.kind != LetterAccessKind.GONE

    /** 링크 발급 안내(받는 분이 처음 연 날부터 N일) — 기간 제한이 있는 편지에만. */
    val freeDaysNotice: Int?
        get() = entitlements?.freeDays?.takeIf { access != null && access.kind != LetterAccessKind.PERMANENT }
}

/** iOS DeliveryModelData 대응. 전달 링크 발급/관리 — 암호 기본 ON, 예약공개, revoke. */
@HiltViewModel
class DeliveryViewModel @Inject constructor(
    private val deliveryUseCase: DeliveryUseCase,
    private val letterAccessUseCase: LetterAccessUseCase,
    private val ticketUseCase: TicketUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryUiState())
    val state: StateFlow<DeliveryUiState> = _state.asStateFlow()

    private var letterId: String = ""

    /** 편지권 화면에 다녀오는 중 — 돌아오면([onResume]) 잔액을 다시 받는다. */
    private var awaitingTickets = false

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
                val link = deliveryUseCase.issue(
                    letterId = letterId,
                    password = if (st.usePassword) st.password else null,
                    revealAt = if (st.useReveal) st.revealAt else null,
                )
                _state.value = _state.value.copy(
                    lastIssuedLink = link,
                    password = "",
                )
                load()
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
        // 요청자 — 건수가 있을 때만 부른다(영구가 되면 건수가 0이 되어 사라진다).
        val keepRequests = if ((access?.keepRequestCount ?: 0) > 0) {
            try {
                letterAccessUseCase.keepRequests(letterId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
        } else {
            emptyList()
        }
        _state.value = _state.value.copy(
            access = access,
            entitlements = entitlements,
            readers = readers,
            keepRequests = keepRequests,
        )
    }

    /** 편지권 1장으로 영구화(make_letter_permanent). 편지권이 없으면 구매 화면으로. */
    fun makePermanent() {
        if (_state.value.isMakingPermanent) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isMakingPermanent = true, error = null)
            try {
                letterAccessUseCase.makePermanent(letterId)
                loadAccess()
            } catch (e: MutterError.NoTicket) {
                _state.value = _state.value.copy(needTickets = true)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isMakingPermanent = false)
            }
        }
    }

    fun onNeedTicketsHandled() {
        awaitingTickets = true
        _state.value = _state.value.copy(needTickets = false)
    }

    /** 보내기 전 선택 — 편지권이 없어 편지권 화면으로. */
    fun openTicketsForChoice() {
        _state.value = _state.value.copy(needTickets = true)
    }

    /** 보내기 전 선택 — 무료 기간. 발급 폼을 연다. */
    fun chooseFreeDays() {
        _state.value = _state.value.copy(choseFreeDays = true)
    }

    /** 화면 복귀 — 편지권 화면에서 돌아왔으면 잔액을 다시 받아 선택 카드의 보유 수를 갱신한다. */
    fun onResume() {
        if (!awaitingTickets) return
        awaitingTickets = false
        viewModelScope.launch {
            ticketUseCase.entitlementsOrNull()?.let { _state.value = _state.value.copy(entitlements = it) }
        }
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
