package com.efreedom.mutter.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.ConnectInvite
import com.efreedom.mutter.domain.usecase.ConnectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** iOS ConnectInviteModelData 대응. 초대 링크(/connect/:token) 열람·수락 화면 상태. */
sealed class ConnectInviteUiState {
    object Loading : ConnectInviteUiState()
    data class Ready(val invite: ConnectInvite) : ConnectInviteUiState()
    object Accepted : ConnectInviteUiState()
    data class Failed(val message: String) : ConnectInviteUiState()
}

@HiltViewModel
class ConnectInviteViewModel @Inject constructor(
    private val connectionUseCase: ConnectionUseCase,
    private val acceptedInviteStore: AcceptedInviteStore,
) : ViewModel() {

    private val _state = MutableStateFlow<ConnectInviteUiState>(ConnectInviteUiState.Loading)
    val state: StateFlow<ConnectInviteUiState> = _state.asStateFlow()

    private var token: String = ""

    /** Screen의 LaunchedEffect에서 한 번 호출. */
    fun start(token: String) {
        if (this.token == token) return
        this.token = token
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = ConnectInviteUiState.Loading
            // 이미 수락한 토큰이면 서버 재확인 없이 연결 완료 표시(멱등 처리).
            if (acceptedInviteStore.contains(token)) {
                _state.value = ConnectInviteUiState.Accepted
                return@launch
            }
            try {
                val invite = connectionUseCase.invite(token)
                _state.value = ConnectInviteUiState.Ready(invite)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "초대 정보를 불러올 수 없어요."
                _state.value = ConnectInviteUiState.Failed(message)
            }
        }
    }

    fun accept() {
        viewModelScope.launch {
            try {
                connectionUseCase.accept(token)
                acceptedInviteStore.markAccepted(token)
                _state.value = ConnectInviteUiState.Accepted
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "수락할 수 없었어요. 다시 시도해 주세요."
                _state.value = ConnectInviteUiState.Failed(message)
            }
        }
    }
}
