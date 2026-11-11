package com.efreedom.mutter.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.usecase.AuthUseCase
import com.efreedom.mutter.domain.usecase.ProfileUseCase
import com.efreedom.mutter.domain.usecase.TicketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val nickname: String = "",
    val isLoading: Boolean = false,
    /** 당겨서 새로고침 진행 표시 — 저장/로그아웃 버튼의 isLoading과 분리. */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val savedToast: Boolean = false,
    val signedOut: Boolean = false,
    /** 편지권 — null이면(서버 반영 전·조회 실패) 편지권 항목을 숨긴다. */
    val entitlements: Entitlements? = null,
) {
    /** 프로필 "편지권" 항목 노출(paid_enabled || is_admin). */
    val showsTickets: Boolean get() = entitlements?.showsTicketUi == true
}

/** iOS ProfileModelData 대응. ProfileUseCase + AuthUseCase 주입. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileUseCase: ProfileUseCase,
    private val authUseCase: AuthUseCase,
    private val ticketUseCase: TicketUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            val profile = runCatching { profileUseCase.myProfile() }.getOrNull()
            val entitlements = ticketUseCase.entitlementsOrNull()
            _state.value = _state.value.copy(
                nickname = profile?.nickname.orEmpty(),
                entitlements = entitlements,
                isRefreshing = false,
            )
        }
    }

    fun onNicknameChange(value: String) {
        _state.value = _state.value.copy(nickname = value, error = null)
    }

    fun saveNickname() = run {
        profileUseCase.updateNickname(_state.value.nickname)
        _state.value = _state.value.copy(savedToast = true)
    }

    fun onSavedToastShown() {
        _state.value = _state.value.copy(savedToast = false)
    }

    fun signOut() = run {
        authUseCase.signOut()
        _state.value = _state.value.copy(signedOut = true)
    }

    fun deleteAccount() = run {
        profileUseCase.deleteAccount()
        runCatching { authUseCase.signOut() }
        _state.value = _state.value.copy(signedOut = true)
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
