package com.efreedom.mutter.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.model.InboxItem
import com.efreedom.mutter.domain.usecase.InboxUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val items: List<InboxItem> = emptyList(),
    val isLoading: Boolean = false,
)

/** iOS InboxModelData 대응. InboxUseCase 주입. */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val inboxUseCase: InboxUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val items = inboxUseCase.myInbox()
                _state.value = _state.value.copy(items = items)
            } catch (_: Throwable) {
                // 빈 상태 유지 — 재시도는 pull-to-refresh로
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 스와이프 삭제 — 낙관적으로 목록에서 먼저 제거하고, 서버 삭제 실패 시 그 항목만 되살린다.
     * (전체 스냅샷 복원은 연속 삭제 중 다른 항목까지 부활시키는 레이스가 있어 항목 단위로 롤백한다.)
     */
    fun delete(item: InboxItem) {
        val current = _state.value.items
        val index = current.indexOfFirst { it.id == item.id }
        if (index < 0) return
        _state.value = _state.value.copy(items = current.filterNot { it.id == item.id })
        viewModelScope.launch {
            try {
                inboxUseCase.remove(item.letterId)
            } catch (_: Throwable) {
                val latest = _state.value.items.toMutableList()
                latest.add(index.coerceAtMost(latest.size), item)
                _state.value = _state.value.copy(items = latest)
            }
        }
    }
}
