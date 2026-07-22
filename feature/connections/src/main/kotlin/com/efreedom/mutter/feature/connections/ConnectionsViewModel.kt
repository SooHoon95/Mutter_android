package com.efreedom.mutter.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Connection
import com.efreedom.mutter.domain.usecase.ConnectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionsUiState(
    val connections: List<Connection> = emptyList(),
    val inviteToken: String? = null,
    val isLoading: Boolean = false,
    /** 당겨서 새로고침 진행 표시 — 초대 생성/취소 버튼의 isLoading과 분리. */
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

/** iOS ConnectionsModelData 대응. N:N 연결 목록 + 초대 링크 생성/취소/해제. */
@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val connectionUseCase: ConnectionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionsUiState())
    val state: StateFlow<ConnectionsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                _state.value = _state.value.copy(connections = connectionUseCase.myConnections())
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }

    fun createInvite() = run {
        _state.value = _state.value.copy(inviteToken = connectionUseCase.createInvite())
    }

    fun revokeInvite() {
        val token = _state.value.inviteToken ?: return
        run {
            connectionUseCase.revokeInvite(token)
            _state.value = _state.value.copy(inviteToken = null)
        }
    }

    fun disconnect(otherUserId: String) = run {
        connectionUseCase.disconnect(otherUserId)
        _state.value = _state.value.copy(
            connections = _state.value.connections.filter { it.userId != otherUserId },
        )
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                block()
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}
