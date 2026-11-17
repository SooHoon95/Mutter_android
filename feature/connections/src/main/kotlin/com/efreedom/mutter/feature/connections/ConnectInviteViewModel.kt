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
    /**
     * 연결됨. [justAccepted]는 이 화면에서 방금 accept()가 성공했을 때만 true — 화면은 그때만 닫힌다.
     * 이미 수락한 링크를 다시 열면 false로 "연결됐어요"에 머문다(iOS ConnectInviteModelData와 같음).
     */
    data class Accepted(val justAccepted: Boolean) : ConnectInviteUiState()
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

    /** 실패 화면의 "다시 시도" — 같은 토큰이라도 다시 불러온다(start()의 같은-토큰 가드는 회전 재로드 방지용). */
    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = ConnectInviteUiState.Loading
            // 이미 수락한 토큰이면 서버 재확인 없이 연결 완료 표시(멱등 처리).
            if (acceptedInviteStore.contains(token)) {
                _state.value = ConnectInviteUiState.Accepted(justAccepted = false)
                return@launch
            }
            try {
                val invite = connectionUseCase.invite(token)
                _state.value = ConnectInviteUiState.Ready(invite)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "초대를 불러올 수 없어요."
                _state.value = ConnectInviteUiState.Failed(message)
            }
        }
    }

    fun accept() {
        viewModelScope.launch {
            try {
                connectionUseCase.accept(token)
                acceptedInviteStore.markAccepted(token)
                _state.value = ConnectInviteUiState.Accepted(justAccepted = true)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "수락에 실패했어요."
                _state.value = ConnectInviteUiState.Failed(message)
            }
        }
    }

    /** 화면이 닫기 콜백을 부르기 직전에 호출 — 재구성·코루틴 재시작에도 닫기가 두 번 나가지 않게 한다. */
    fun onAcceptedHandled() {
        if ((_state.value as? ConnectInviteUiState.Accepted)?.justAccepted == true) {
            _state.value = ConnectInviteUiState.Accepted(justAccepted = false)
        }
    }
}
