package com.efreedom.mutter.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.SocialAuthResult
import com.efreedom.mutter.domain.model.SocialProvider
import com.efreedom.mutter.domain.usecase.AuthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 로그인 단계.
 * - ENTER_EMAIL/CODE/PASSWORD: 이메일 매직코드 + 비밀번호(iOS AuthModelData 대응).
 * - ENTER_SOCIAL_NICKNAME: 신규 소셜 회원이 닉네임을 입력하는 단계(가입은 이때 일어남).
 */
enum class AuthStep { ENTER_EMAIL, ENTER_CODE, ENTER_PASSWORD, ENTER_SOCIAL_NICKNAME }

data class AuthUiState(
    val step: AuthStep = AuthStep.ENTER_EMAIL,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val nickname: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val authenticated: Boolean = false,
)

/** iOS AuthModelData 대응. 이메일 OTP + 비밀번호 + 소셜(Google/Kakao). */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authUseCase: AuthUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    // 신규 소셜 회원의 대기 중인 자격증명 — 닉네임 입력 후 registerSocial에 쓴다(계정은 그때 생성).
    private var pendingProvider: SocialProvider? = null
    private var pendingIdToken: String? = null

    // 이메일 형식 최소 검증 — iOS AuthModelData와 동일하게 '@'와 '.' 모두 포함해야 유효.
    val isEmailValid: Boolean
        get() = _state.value.email.trim().let { it.contains("@") && it.contains(".") }
    val isNicknameValid: Boolean get() = _state.value.nickname.trim().isNotEmpty()

    fun onEmailChange(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun onCodeChange(value: String) {
        _state.value = _state.value.copy(code = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun onNicknameChange(value: String) {
        _state.value = _state.value.copy(nickname = value, error = null)
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

    /** Google — Credential Manager로 id_token 획득 → checkOnly. */
    fun signInGoogle(context: Context) = launchSocial(SocialProvider.GOOGLE) { OauthClient.googleIdToken(context) }

    /** Kakao — 카카오 SDK로 id_token 획득 → checkOnly. */
    fun signInKakao(context: Context) = launchSocial(SocialProvider.KAKAO) { OauthClient.kakaoIdToken(context) }

    /**
     * Apple — 안드로이드는 네이티브 SDK가 없어 브라우저 웹 OAuth로 로그인한다.
     * signInAppleWeb()는 브라우저(Custom Tab)를 열기만 하고, 세션은 리다이렉트를
     * MainActivity가 handleDeeplinks로 처리한 뒤 AppViewModel.refresh()로 반영된다.
     */
    fun launchApple() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                authUseCase.signInAppleWeb()
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "Apple 로그인을 시작할 수 없어요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 소셜 1단계 — 토큰 획득 → startSocial(checkOnly).
     * 기존 회원이면 즉시 로그인, 신규면 계정 생성 없이 닉네임 단계로 전환(닉네임 전 가입 방지).
     */
    private fun launchSocial(provider: SocialProvider, acquireToken: suspend () -> String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val idToken = acquireToken()
                when (authUseCase.startSocial(provider, idToken)) {
                    is SocialAuthResult.Authenticated ->
                        _state.value = _state.value.copy(authenticated = true)
                    SocialAuthResult.NeedsNickname -> {
                        pendingProvider = provider
                        pendingIdToken = idToken
                        _state.value = _state.value.copy(step = AuthStep.ENTER_SOCIAL_NICKNAME, nickname = "")
                    }
                }
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

    /** 소셜 2단계 — 닉네임과 함께 가입(이때만 계정 생성). */
    fun submitSocialNickname() = run {
        val provider = pendingProvider ?: return@run
        val idToken = pendingIdToken ?: return@run
        authUseCase.registerSocial(provider, idToken, _state.value.nickname.trim())
        pendingProvider = null
        pendingIdToken = null
        _state.value = _state.value.copy(authenticated = true)
    }

    fun switchToPassword() {
        // 입력한 코드는 유지(iOS 대응) — 비밀번호로 갔다가 돌아와도 코드가 남아 있게.
        _state.value = _state.value.copy(step = AuthStep.ENTER_PASSWORD, error = null)
    }

    fun backToEmail() {
        pendingProvider = null
        pendingIdToken = null
        _state.value = _state.value.copy(
            step = AuthStep.ENTER_EMAIL, code = "", password = "", nickname = "", error = null,
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
