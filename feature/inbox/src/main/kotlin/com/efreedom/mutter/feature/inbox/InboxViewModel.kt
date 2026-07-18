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
}
