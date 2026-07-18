package com.efreedom.mutter.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.usecase.AuthUseCase
import com.efreedom.mutter.domain.usecase.ProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val nickname: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedToast: Boolean = false,
    val signedOut: Boolean = false,
)

/** iOS ProfileModelData 대응. ProfileUseCase + AuthUseCase 주입. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileUseCase: ProfileUseCase,
    private val authUseCase: AuthUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val profile = runCatching { profileUseCase.myProfile() }.getOrNull()
            _state.value = _state.value.copy(nickname = profile?.nickname.orEmpty())
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
