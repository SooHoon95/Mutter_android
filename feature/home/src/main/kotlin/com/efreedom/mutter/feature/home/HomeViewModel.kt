package com.efreedom.mutter.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Letter
import com.efreedom.mutter.domain.model.LetterAccessLabel
import com.efreedom.mutter.domain.model.LetterOpenSummary
import com.efreedom.mutter.domain.usecase.LetterAccessUseCase
import com.efreedom.mutter.domain.usecase.LetterUseCase
import com.efreedom.mutter.domain.usecase.ReceiptUseCase
import com.efreedom.mutter.domain.usecase.TicketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class LetterRow(
    val letter: Letter,
    val openSummary: LetterOpenSummary?,
    val isSent: Boolean,
) {
    val id: String get() = letter.id
    val isOpened: Boolean get() = (openSummary?.openCount ?: 0) > 0
}

data class HomeUiState(
    val rows: List<LetterRow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** 방금 삭제함으로 옮긴 편지 id — 화면이 "되돌리기" 스낵바를 띄우고 [HomeViewModel.undoDismissed]로 비운다. */
    val undoLetterId: String? = null,
    /** 보낸 편지 상태 표시(영구·n일 남음·닫힘). 서버 반영 전·조회 실패면 비어 있다 — 기존 목록 그대로. */
    val accessLabels: Map<String, LetterAccessLabel> = emptyMap(),
) {
    val sentRows: List<LetterRow> get() = rows.filter { it.isSent }
    val draftRows: List<LetterRow> get() = rows.filter { !it.isSent }
    val sentCount: Int get() = sentRows.size
    val openedCount: Int get() = sentRows.count { it.isOpened }
}

/** iOS HomeModelData(LetterUseCase + ReceiptUseCase) 대응. 보낸/임시저장 편지 목록 + 읽음 통계. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val letterUseCase: LetterUseCase,
    private val receiptUseCase: ReceiptUseCase,
    private val letterAccessUseCase: LetterAccessUseCase,
    private val ticketUseCase: TicketUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // coroutineScope로 감싸야 async 자식 실패가 부모(viewModelScope)로 전파돼 앱이
                // 죽지 않고, 이 try/catch에서 잡힌다(구조적 동시성 — async-in-launch 함정 회피).
                // 읽음 통계(opens)는 부가 정보 — 실패해도 편지 목록은 보여준다(iOS는 둘 다 try?).
                // 목록(letters)이 주 데이터이므로 실패 시엔 아래 catch로 에러를 노출한다.
                val (letters, opens) = coroutineScope {
                    val lettersDeferred = async { letterUseCase.myLettersWithStatus() }
                    val opensDeferred = async {
                        // 취소 신호는 삼키지 않고 전파(runCatching은 CancellationException까지 잡는 함정).
                        try {
                            receiptUseCase.myLetterOpens()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            emptyList()
                        }
                    }
                    lettersDeferred.await() to opensDeferred.await()
                }
                val openMap = opens.associateBy { it.letterId }
                val rows = letters.map { lws ->
                    LetterRow(
                        letter = lws.letter,
                        openSummary = openMap[lws.letter.id],
                        isSent = lws.isSent,
                    )
                }
                _state.value = _state.value.copy(rows = rows)
                loadAccessLabels(rows.filter { it.isSent }.map { it.id })
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /** 목록에 상태를 합친다. 둘 다 실패하면 조용히 빈 값(서버 반영 전 RPC 없음) — 오류 표시 없음. */
    private fun loadAccessLabels(letterIds: List<String>) {
        if (letterIds.isEmpty()) return
        viewModelScope.launch {
            val (states, entitlements) = coroutineScope {
                val statesDeferred = async { letterAccessUseCase.accessStates(letterIds) }
                val entitlementsDeferred = async { ticketUseCase.entitlementsOrNull() }
                statesDeferred.await() to entitlementsDeferred.await()
            }
            val showPermanent = entitlements?.showsTicketUi == true
            val now = Instant.now()
            val labels = states.mapValues { (_, st) -> LetterAccessLabel.of(st, showPermanent, now) }
            _state.value = _state.value.copy(accessLabels = labels)
        }
    }

    /**
     * 스와이프 삭제 → 서버가 삭제함으로 옮긴다(30일 보관). 행은 바로 빼고, 성공하면 "되돌리기" 스낵바를 띄운다.
     * 실패하면 그 행만 원래 자리로 되돌린다(전체 스냅샷 복원은 연속 삭제 중 다른 행까지 부활시킨다).
     */
    fun delete(letterId: String) {
        val current = _state.value.rows
        val index = current.indexOfFirst { it.id == letterId }
        if (index < 0) return
        val removed = current[index]
        _state.value = _state.value.copy(rows = current.filterNot { it.id == letterId }, error = null)
        viewModelScope.launch {
            try {
                letterUseCase.delete(letterId)
                _state.value = _state.value.copy(undoLetterId = letterId)
            } catch (e: Throwable) {
                val latest = _state.value.rows.toMutableList()
                latest.add(index.coerceAtMost(latest.size), removed)
                val message = (e as? MutterError)?.userMessage ?: "삭제할 수 없었어요."
                _state.value = _state.value.copy(rows = latest, error = message)
            }
        }
    }

    /** 스낵바 "되돌리기" — 삭제함에서 되살린 뒤 목록을 다시 받는다(순서·상태를 서버 기준으로 맞춘다). */
    fun undoDelete(letterId: String) {
        undoDismissed(letterId)
        viewModelScope.launch {
            try {
                letterUseCase.restore(letterId)
                load()
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "되돌릴 수 없었어요."
                _state.value = _state.value.copy(error = message)
            }
        }
    }

    /** 스낵바가 닫혔다(시간 초과·되돌리기·화면 이탈). 그사이 다른 삭제로 바뀐 id는 건드리지 않는다. */
    fun undoDismissed(letterId: String) {
        if (_state.value.undoLetterId == letterId) {
            _state.value = _state.value.copy(undoLetterId = null)
        }
    }
}
