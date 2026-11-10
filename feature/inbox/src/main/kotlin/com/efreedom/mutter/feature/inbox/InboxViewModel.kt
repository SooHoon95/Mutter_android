package com.efreedom.mutter.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.InboxItem
import com.efreedom.mutter.domain.usecase.InboxUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 방금 삭제함으로 옮긴 받은 편지들 — 화면이 "되돌리기" 스낵바를 띄운다. [key]는 연속 삭제마다 새 스낵바를 띄우려는 구분자. */
data class InboxUndo(val key: Long, val letterIds: List<String>)

data class InboxUiState(
    val items: List<InboxItem> = emptyList(),
    val isLoading: Boolean = false,
    /** 편집 모드(계약 §6.2) — 카드 탭이 열기 대신 선택 토글, 스와이프·당겨서 새로고침은 끈다. */
    val isEditing: Boolean = false,
    /** 편집 모드에서 고른 letterId. */
    val selectedIds: Set<String> = emptySet(),
    val undo: InboxUndo? = null,
    val error: String? = null,
) {
    val isAllSelected: Boolean get() = items.isNotEmpty() && items.all { it.letterId in selectedIds }
}

/** iOS InboxModelData 대응. InboxUseCase 주입. 받은 편지 목록 + 편집(여러 통 삭제) + 삭제 되돌리기. */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val inboxUseCase: InboxUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private var undoSeq = 0L

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val items = inboxUseCase.myInbox()
                // 그사이 사라진 편지는 선택에서도 뺀다 — "삭제 (N)"이 화면에 없는 편지까지 세지 않게.
                val ids = items.mapTo(HashSet()) { it.letterId }
                _state.value = _state.value.copy(items = items, selectedIds = _state.value.selectedIds intersect ids)
            } catch (_: Throwable) {
                // 빈 상태 유지 — 재시도는 pull-to-refresh로
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    // ─── 편집 모드 ─────────────────────────────────────────────────────────

    fun toggleEditing() {
        _state.value = _state.value.copy(isEditing = !_state.value.isEditing, selectedIds = emptySet())
    }

    fun toggleSelection(letterId: String) {
        val selected = _state.value.selectedIds
        _state.value = _state.value.copy(
            selectedIds = if (letterId in selected) selected - letterId else selected + letterId,
        )
    }

    /** "전체 선택" / "선택 해제" — 모두 골라져 있으면 해제, 아니면 전부 고른다. */
    fun toggleSelectAll() {
        val state = _state.value
        _state.value = state.copy(
            selectedIds = if (state.isAllSelected) emptySet() else state.items.mapTo(HashSet()) { it.letterId },
        )
    }

    fun deleteSelected() {
        val selected = _state.value.selectedIds
        delete(_state.value.items.filter { it.letterId in selected })
    }

    // ─── 삭제 · 되돌리기 ─────────────────────────────────────────────────────

    /**
     * 삭제(편집 모드·스와이프 공통, 계약 §6.2). 목록에서 바로 빼고 편집 모드를 끝낸 뒤 서버에 delete를 보낸다
     * (inbox 뷰 트리거가 삭제함 30일 보관으로 바꾼다). 성공하면 "되돌리기" 스낵바, 실패하면 뺀 항목만 원래 자리로 되살린다
     * (전체 스냅샷 복원은 연속 삭제 중 다른 항목까지 부활시킨다).
     */
    fun delete(items: List<InboxItem>) {
        if (items.isEmpty()) return
        val current = _state.value.items
        val ids = items.mapTo(HashSet()) { it.letterId }
        // 원래 위치를 오름차순으로 기억해 두면 되살릴 때 앞에서부터 넣어 순서가 그대로 맞는다.
        val removed = current.withIndex().filter { it.value.letterId in ids }
        if (removed.isEmpty()) return
        _state.value = _state.value.copy(
            items = current.filterNot { it.letterId in ids },
            isEditing = false,
            selectedIds = emptySet(),
            error = null,
        )
        val letterIds = removed.map { it.value.letterId }
        viewModelScope.launch {
            try {
                inboxUseCase.remove(letterIds)
                _state.value = _state.value.copy(undo = InboxUndo(key = ++undoSeq, letterIds = letterIds))
            } catch (e: Throwable) {
                val latest = _state.value.items.toMutableList()
                removed.forEach { (index, item) -> latest.add(index.coerceAtMost(latest.size), item) }
                val message = (e as? MutterError)?.userMessage ?: "삭제할 수 없었어요."
                _state.value = _state.value.copy(items = latest, error = message)
            }
        }
    }

    /** 스낵바 "되돌리기" — restore_inbox_items로 되살린 뒤 목록을 다시 받는다(순서·제목을 서버 기준으로 맞춘다). */
    fun undoDelete(undo: InboxUndo) {
        undoDismissed(undo.key)
        viewModelScope.launch {
            try {
                inboxUseCase.restore(undo.letterIds)
                load()
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "되돌릴 수 없었어요."
                _state.value = _state.value.copy(error = message)
            }
        }
    }

    /** 스낵바가 닫혔다(시간 초과·되돌리기·화면 이탈). 그사이 다른 삭제로 바뀐 것은 건드리지 않는다. */
    fun undoDismissed(key: Long) {
        if (_state.value.undo?.key == key) {
            _state.value = _state.value.copy(undo = null)
        }
    }
}
