package com.efreedom.mutter.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.SocialProvider
import com.efreedom.mutter.domain.usecase.AuthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 로그인 단계 — 이메일 매직코드(가입 겸용) + 비밀번호(iOS AuthModelData.step 대응). */
enum class AuthStep { ENTER_EMAIL, ENTER_CODE, ENTER_PASSWORD }

data class AuthUiState(
    val step: AuthStep = AuthStep.ENTER_EMAIL,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val authenticated: Boolean = false,
)

/** iOS AuthModelData 대응. 이메일 OTP + 비밀번호 로그인. 소셜은 토큰 획득 SDK 연동 후 활성화. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authUseCase: AuthUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val isEmailValid: Boolean get() = _state.value.email.trim().contains("@")

    fun onEmailChange(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun onCodeChange(value: String) {
        _state.value = _state.value.copy(code = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun requestCode() = run {
        authUseCase.requestCode(_state.value.email.trim())
        _state.value = _state.value.copy(step = AuthStep.ENTER_CODE)
    }

    fun verifyCode() = run {
        authUseCase.verifyCode(_state.value.email.trim(), _state.value.code.trim())
        _state.value = _state.value.copy(authenticated = true)
    }

    fun signInWithPassword() = run {
        authUseCase.signIn(_state.value.email.trim(), _state.value.password)
        _state.value = _state.value.copy(authenticated = true)
    }

    /** Google 로그인 — Credential Manager로 id_token 획득 → Edge Function 교환. */
    fun signInGoogle(context: Context) = social { OauthClient.googleIdToken(context) to SocialProvider.GOOGLE }

    /** Kakao 로그인 — 카카오 SDK로 id_token 획득 → Edge Function 교환. */
    fun signInKakao(context: Context) = social { OauthClient.kakaoIdToken(context) to SocialProvider.KAKAO }

    /** Apple — 안드로이드 네이티브 미지원(후속: 웹 OAuth). */
    fun appleComingSoon() {
        _state.value = _state.value.copy(error = "Apple 로그인은 곧 지원됩니다.")
    }

    private fun social(acquire: suspend () -> Pair<String, SocialProvider>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val (token, provider) = acquire()
                authUseCase.signInSocial(provider, token)
                _state.value = _state.value.copy(authenticated = true)
            } catch (e: OauthCancelledException) {
                // 사용자가 로그인 창을 닫음 — 조용히 무시.
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "로그인에 실패했어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun switchToPassword() {
        _state.value = _state.value.copy(step = AuthStep.ENTER_PASSWORD, code = "", error = null)
    }

    fun backToEmail() {
        _state.value = _state.value.copy(step = AuthStep.ENTER_EMAIL, code = "", password = "", error = null)
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
