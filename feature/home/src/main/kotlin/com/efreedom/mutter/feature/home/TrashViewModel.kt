package com.efreedom.mutter.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.DeletedLetter
import com.efreedom.mutter.domain.usecase.LetterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val letters: List<DeletedLetter> = emptyList(),
    val isLoading: Boolean = false,
    /** 첫 로드 전엔 빈 상태 문구를 띄우지 않으려고 둔다(로딩 → 빈 상태 깜빡임 방지). */
    val hasLoaded: Boolean = false,
    /** 진행 중인 행 동작(복구·영구 삭제) id — 같은 행 버튼을 중복으로 누르지 못하게 막는다. */
    val busyIds: Set<String> = emptySet(),
    val error: String? = null,
    /** 복구 성공 횟수 — 화면이 값이 바뀔 때마다 "편지를 되살렸어요" 스낵바를 띄운다(연속 복구도 매번). */
    val restoredCount: Int = 0,
)

/** 삭제함(30일 보관) — 보관 중 편지 목록, 행별 복구·영구 삭제. 서버 계약은 LetterUseCase 삭제함 RPC. */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val letterUseCase: LetterUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                _state.value = _state.value.copy(letters = letterUseCase.deletedLetters())
            } catch (e: Throwable) {
                _state.value = _state.value.copy(error = message(e, "삭제함을 불러올 수 없었어요."))
            } finally {
                _state.value = _state.value.copy(isLoading = false, hasLoaded = true)
            }
        }
    }

    fun restore(id: String) = act(id, failure = "되살릴 수 없었어요.", onSuccess = {
        it.copy(restoredCount = it.restoredCount + 1)
    }) { letterUseCase.restore(id) }

    fun purge(id: String) = act(id, failure = "완전히 삭제할 수 없었어요.") { letterUseCase.purge(id) }

    /**
     * 행 동작 공통 흐름: 서버 성공 뒤에만 행을 뺀다(되돌릴 수 없는 영구 삭제라 낙관적 제거를 하지 않는다).
     * 이미 없는 편지(NotFound — 다른 기기에서 복구·삭제됐거나 30일 정리로 사라짐)는 결과가 같으므로 행만 뺀다.
     */
    private fun act(
        id: String,
        failure: String,
        onSuccess: (TrashUiState) -> TrashUiState = { it },
        block: suspend () -> Unit,
    ) {
        if (id in _state.value.busyIds) return
        _state.value = _state.value.copy(busyIds = _state.value.busyIds + id, error = null)
        viewModelScope.launch {
            try {
                block()
                _state.value = onSuccess(_state.value.withoutLetter(id))
            } catch (_: MutterError.NotFound) {
                _state.value = _state.value.withoutLetter(id)
            } catch (e: Throwable) {
                _state.value = _state.value.copy(error = message(e, failure))
            } finally {
                _state.value = _state.value.copy(busyIds = _state.value.busyIds - id)
            }
        }
    }

    private fun TrashUiState.withoutLetter(id: String) = copy(letters = letters.filterNot { it.id == id })

    private fun message(e: Throwable, fallback: String): String =
        (e as? MutterError)?.userMessage ?: fallback
}
