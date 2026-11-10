package com.efreedom.mutter.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.DeletedInboxItem
import com.efreedom.mutter.domain.usecase.InboxUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxTrashUiState(
    val items: List<DeletedInboxItem> = emptyList(),
    val isLoading: Boolean = false,
    /** 첫 로드 전엔 빈 상태 문구를 띄우지 않으려고 둔다(로딩 → 빈 상태 깜빡임 방지). */
    val hasLoaded: Boolean = false,
    /** 진행 중인 행 동작(복구·영구 삭제)의 letterId — 같은 행 버튼을 중복으로 누르지 못하게 막는다. */
    val busyIds: Set<String> = emptySet(),
    val error: String? = null,
    /** 복구 성공 횟수 — 화면이 값이 바뀔 때마다 "편지를 되살렸어요" 스낵바를 띄운다(연속 복구도 매번). */
    val restoredCount: Int = 0,
)

/** 받은 편지 삭제함(계약 §6.3) — 보관 중 받은 편지 목록, 행별 복구·영구 삭제(받은 편지함에서만). */
@HiltViewModel
class InboxTrashViewModel @Inject constructor(
    private val inboxUseCase: InboxUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxTrashUiState())
    val state: StateFlow<InboxTrashUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                _state.value = _state.value.copy(items = inboxUseCase.deletedItems())
            } catch (e: Throwable) {
                _state.value = _state.value.copy(error = message(e, "삭제함을 불러올 수 없었어요."))
            } finally {
                _state.value = _state.value.copy(isLoading = false, hasLoaded = true)
            }
        }
    }

    fun restore(letterId: String) = act(letterId, failure = "되살릴 수 없었어요.", onSuccess = {
        it.copy(restoredCount = it.restoredCount + 1)
    }) { inboxUseCase.restore(listOf(letterId)) }

    fun purge(letterId: String) = act(letterId, failure = "완전히 삭제할 수 없었어요.") {
        inboxUseCase.purge(listOf(letterId))
    }

    /**
     * 행 동작 공통 흐름: 서버 성공 뒤에만 행을 뺀다(되돌릴 수 없는 영구 삭제라 낙관적 제거를 하지 않는다).
     * RPC는 처리한 수를 돌려주는데 0이어도(다른 기기에서 이미 복구·삭제됐거나 30일 정리로 사라짐) 결과가 같으므로 행만 뺀다.
     */
    private fun act(
        letterId: String,
        failure: String,
        onSuccess: (InboxTrashUiState) -> InboxTrashUiState = { it },
        block: suspend () -> Int,
    ) {
        if (letterId in _state.value.busyIds) return
        _state.value = _state.value.copy(busyIds = _state.value.busyIds + letterId, error = null)
        viewModelScope.launch {
            try {
                val count = block()
                val removed = _state.value.withoutItem(letterId)
                _state.value = if (count > 0) onSuccess(removed) else removed
            } catch (e: Throwable) {
                _state.value = _state.value.copy(error = message(e, failure))
            } finally {
                _state.value = _state.value.copy(busyIds = _state.value.busyIds - letterId)
            }
        }
    }

    private fun InboxTrashUiState.withoutItem(letterId: String) =
        copy(items = items.filterNot { it.letterId == letterId })

    private fun message(e: Throwable, fallback: String): String =
        (e as? MutterError)?.userMessage ?: fallback
}
