package com.efreedom.mutter.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.LoginPasswordStatus
import com.efreedom.mutter.domain.usecase.AuthUseCase
import com.efreedom.mutter.domain.usecase.ProfileUseCase
import com.efreedom.mutter.domain.usecase.TicketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 저장 완료 토스트 종류. */
enum class ProfileSavedToast { NICKNAME, PASSWORD }

data class ProfileUiState(
    val nickname: String = "",
    val isLoading: Boolean = false,
    /** 당겨서 새로고침 진행 표시 — 저장/로그아웃 버튼의 isLoading과 분리. */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val savedToast: ProfileSavedToast? = null,
    val signedOut: Boolean = false,
    /** 편지권 — null이면(서버 반영 전·조회 실패) 편지권 항목을 숨긴다. */
    val entitlements: Entitlements? = null,
    /** 로그인 비밀번호 — null이거나 isAvailable=false(소셜 계정)면 항목을 숨긴다. */
    val passwordStatus: LoginPasswordStatus? = null,
    val isPasswordDialogVisible: Boolean = false,
    /** 비밀번호 다이얼로그 전용 오류·저장 중 — 화면의 [error]·[isLoading]과 분리(iOS passwordErrorMessage·isSavingPassword). */
    val passwordError: String? = null,
    val isSavingPassword: Boolean = false,
) {
    val showsLoginPassword: Boolean get() = passwordStatus?.isAvailable == true

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
            val passwordStatus = runCatching { authUseCase.loginPasswordStatus() }.getOrNull()
            _state.value = _state.value.copy(
                nickname = profile?.nickname.orEmpty(),
                entitlements = entitlements,
                passwordStatus = passwordStatus,
                isRefreshing = false,
            )
        }
    }

    fun onNicknameChange(value: String) {
        _state.value = _state.value.copy(nickname = value, error = null)
    }

    fun saveNickname() = run {
        profileUseCase.updateNickname(_state.value.nickname)
        _state.value = _state.value.copy(savedToast = ProfileSavedToast.NICKNAME)
    }

    fun onSavedToastShown() {
        _state.value = _state.value.copy(savedToast = null)
    }

    fun showPasswordDialog() {
        _state.value = _state.value.copy(isPasswordDialogVisible = true, passwordError = null)
    }

    fun dismissPasswordDialog() {
        _state.value = _state.value.copy(isPasswordDialogVisible = false, passwordError = null)
    }

    /** 로그인 비밀번호 저장 — 실패하면 다이얼로그 안에만 오류를 보이고 열어 둔다(화면 오류·로딩은 건드리지 않는다). */
    fun savePassword(password: String, confirm: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingPassword = true, passwordError = null)
            try {
                authUseCase.setPassword(password, confirm)
                _state.value = _state.value.copy(
                    isPasswordDialogVisible = false,
                    savedToast = ProfileSavedToast.PASSWORD,
                    passwordStatus = runCatching { authUseCase.loginPasswordStatus() }.getOrNull()
                        ?: _state.value.passwordStatus,
                )
            } catch (e: Throwable) {
                _state.value = _state.value.copy(passwordError = (e as? MutterError)?.userMessage ?: RETRY_LATER)
            } finally {
                _state.value = _state.value.copy(isSavingPassword = false)
            }
        }
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
                val message = (e as? MutterError)?.userMessage ?: RETRY_LATER
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}

/** 일반 실패 문구(iOS `common.retryLater`). */
private const val RETRY_LATER = "잠시 후 다시 시도해 주세요."
