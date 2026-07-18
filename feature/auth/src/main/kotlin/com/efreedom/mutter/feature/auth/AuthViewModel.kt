package com.efreedom.mutter.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.usecase.AuthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthStep { ENTER_EMAIL, ENTER_CODE }

data class AuthUiState(
    val step: AuthStep = AuthStep.ENTER_EMAIL,
    val email: String = "",
    val code: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val authenticated: Boolean = false,
)

/** iOS AuthModelData(이메일 OTP 흐름) 대응. 소셜/Apple은 후속. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authUseCase: AuthUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun onCodeChange(value: String) {
        _state.value = _state.value.copy(code = value, error = null)
    }

    fun requestCode() = run {
        authUseCase.requestCode(_state.value.email.trim())
        _state.value = _state.value.copy(step = AuthStep.ENTER_CODE)
    }

    fun verifyCode() = run {
        authUseCase.verifyCode(_state.value.email.trim(), _state.value.code.trim())
        _state.value = _state.value.copy(authenticated = true)
    }

    fun backToEmail() {
        _state.value = _state.value.copy(step = AuthStep.ENTER_EMAIL, code = "", error = null)
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
