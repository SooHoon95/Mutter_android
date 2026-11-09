package com.efreedom.mutter.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.model.InboxItem
import com.efreedom.mutter.domain.usecase.InboxUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val items: List<InboxItem> = emptyList(),
    val isLoading: Boolean = false,
    /** 숨겼지만 아직 서버에서 지우지 않은 항목 — 화면이 "되돌리기" 스낵바를 띄운다. */
    val pendingRemoval: InboxItem? = null,
)

/** 지연 삭제 대기 항목과 원래 위치(되돌리기 시 그 자리에 다시 넣는다). */
private data class PendingRemoval(val item: InboxItem, val index: Int)

/** iOS InboxModelData 대응. InboxUseCase 주입. */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val inboxUseCase: InboxUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private var pending: PendingRemoval? = null

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                // 지연 삭제 대기 중인 항목은 서버엔 아직 있으니 새로고침해도 다시 보이지 않게 뺀다.
                val hiddenId = pending?.item?.id
                val items = inboxUseCase.myInbox().filterNot { it.id == hiddenId }
                _state.value = _state.value.copy(items = items)
            } catch (_: Throwable) {
                // 빈 상태 유지 — 재시도는 pull-to-refresh로
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 스와이프 삭제 = 지연 삭제(삭제함 계약 §3.3). 목록에서 바로 숨기고 "되돌리기" 스낵바를 띄운 뒤,
     * 시간이 지나거나 화면을 떠나면 [commitRemoval]로 실제 delete를 보낸다. 되돌리면 요청 없이 다시 보여 준다.
     * 지웠다가 다시 insert하는 방식은 on_inbox_insert 푸시 트리거가 또 울려서 쓰지 않는다.
     * 대기 중에 다른 항목을 지우면 앞의 것은 바로 확정한다(스낵바는 한 번에 하나라 되돌릴 길이 없어지므로).
     */
    fun delete(item: InboxItem) {
        val current = _state.value.items
        val index = current.indexOfFirst { it.id == item.id }
        if (index < 0) return
        pending?.let { commitRemoval(it.item.id) }
        pending = PendingRemoval(item, index)
        _state.value = _state.value.copy(
            items = _state.value.items.filterNot { it.id == item.id },
            pendingRemoval = item,
        )
    }

    /** "되돌리기" — 서버 요청 없이 원래 자리에 다시 넣는다. */
    fun undoRemoval(itemId: String) {
        val target = pending?.takeIf { it.item.id == itemId } ?: return
        pending = null
        _state.value = _state.value.copy(items = reinserted(target), pendingRemoval = null)
    }

    /**
     * 스낵바가 끝났다(시간 초과·화면 이탈) → 실제로 지운다. 이미 되돌렸거나 확정한 항목이면 아무것도 안 한다.
     * 서버 삭제가 실패하면 그 항목만 되살린다(전체 스냅샷 복원은 연속 삭제 중 다른 항목까지 부활시킨다).
     */
    fun commitRemoval(itemId: String) {
        val target = pending?.takeIf { it.item.id == itemId } ?: return
        pending = null
        _state.value = _state.value.copy(pendingRemoval = null)
        viewModelScope.launch {
            try {
                inboxUseCase.remove(target.item.letterId)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(items = reinserted(target))
            }
        }
    }

    /**
     * 스낵바가 떠 있는 채로 ViewModel이 사라지면(탭 호스트가 내려가는 로그아웃·앱 종료) 대기 삭제를 확정한다.
     * viewModelScope는 이미 취소됐으니 독립 스코프로 보낸다 — 화면이 없어 실패해도 되살릴 곳이 없으므로 결과는 버린다.
     */
    override fun onCleared() {
        val target = pending ?: return
        pending = null
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { inboxUseCase.remove(target.item.letterId) }
        }
    }

    private fun reinserted(target: PendingRemoval): List<InboxItem> {
        val latest = _state.value.items.filterNot { it.id == target.item.id }.toMutableList()
        latest.add(target.index.coerceAtMost(latest.size), target.item)
        return latest
    }
}
