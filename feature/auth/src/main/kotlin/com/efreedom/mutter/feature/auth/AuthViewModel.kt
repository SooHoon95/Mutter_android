package com.efreedom.mutter.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.analytics.Analytics
import com.efreedom.mutter.domain.analytics.AnalyticsEvent
import com.efreedom.mutter.domain.analytics.LoginMethod
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
 * - ENTER_PASSWORD: 기본 단계 — 이메일 + 비밀번호 로그인(웹 구조와 맞춤).
 * - ENTER_EMAIL/CODE: 코드로 로그인(이메일 → 코드).
 * - SIGN_UP: 이메일로 회원가입 — 이메일·비밀번호·확인 → 코드 발송 → ENTER_CODE → 인증 즉시 비밀번호 저장.
 * - ENTER_SOCIAL_NICKNAME: 신규 소셜 회원이 닉네임을 입력하는 단계(가입은 이때 일어남).
 * - SET_PASSWORD: 코드 인증 직후 비밀번호를 정하는 선택 단계(이미 로그인된 상태).
 *   이메일 계정이 비밀번호를 정한 적 없을 때, 또는 회원가입 비밀번호 저장이 실패했을 때.
 */
enum class AuthStep { ENTER_EMAIL, ENTER_CODE, ENTER_PASSWORD, SIGN_UP, ENTER_SOCIAL_NICKNAME, SET_PASSWORD }

data class AuthUiState(
    val step: AuthStep = AuthStep.ENTER_PASSWORD,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    /** SIGN_UP·SET_PASSWORD 단계의 확인 입력. */
    val passwordConfirm: String = "",
    val nickname: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val authenticated: Boolean = false,
)

/** iOS AuthModelData 대응. 이메일 OTP + 비밀번호 + 소셜(Google/Kakao/Apple). */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authUseCase: AuthUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    // 신규 소셜 회원의 대기 중인 자격증명 — 닉네임 입력 후 registerSocial에 쓴다(계정은 그때 생성).
    private var pendingProvider: SocialProvider? = null
    private var pendingIdToken: String? = null

    // 회원가입 단계에서 입력한 비밀번호 — 코드 인증 성공 직후 setPassword에 쓴다. 다른 단계로 떠나면 비운다.
    private var pendingSignUpPassword: String? = null

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

    fun onPasswordConfirmChange(value: String) {
        _state.value = _state.value.copy(passwordConfirm = value, error = null)
    }

    fun onNicknameChange(value: String) {
        _state.value = _state.value.copy(nickname = value, error = null)
    }

    fun requestCode() = run {
        authUseCase.requestCode(_state.value.email.trim())
        pendingSignUpPassword = null
        _state.value = _state.value.copy(step = AuthStep.ENTER_CODE)
    }

    /**
     * 회원가입 "인증 코드 받기" — 비밀번호 규칙(길이 → 일치)을 먼저 검사하고 코드를 보낸다(createUser=true).
     * 비밀번호는 인증 성공 직후 저장하려고 [pendingSignUpPassword]에 둔다. 입력칸은 비우지 않는다 —
     * 코드 단계에서 "이메일 다시 입력"으로 돌아오면 비밀번호·확인이 그대로 남아 있다(iOS와 같다).
     */
    fun requestSignUpCode() = run {
        val password = _state.value.password
        authUseCase.validatePassword(password, _state.value.passwordConfirm)
        authUseCase.requestCode(_state.value.email.trim())
        pendingSignUpPassword = password
        _state.value = _state.value.copy(step = AuthStep.ENTER_CODE)
    }

    /**
     * 코드 확인. 진입(authenticated) 뒤의 닉네임 게이트(AppViewModel.refresh)는 그대로다 — 아래 단계는 그 앞에 끼울 뿐.
     * - 회원가입 중(대기 비밀번호 있음): 인증 즉시 비밀번호 저장 → 진입. 실패하면 SET_PASSWORD로 보내 오류를 보인다.
     *   same_password(이미 가입된 이메일이 같은 비밀번호를 입력)는 원하는 상태가 이미 된 것이라 성공으로 본다.
     * - 그 외: 이메일 계정이고 비밀번호를 정한 적 없으면 SET_PASSWORD, 아니면 진입. 상태 조회 실패는 단계를 건너뛴다.
     */
    fun verifyCode() = run {
        authUseCase.verifyCode(_state.value.email.trim(), _state.value.code.trim())
        val signUpPassword = pendingSignUpPassword
        // 코드 인증 = 로그인 성공. 회원가입 중이었으면 가입도 함께 기록한다(이후 비밀번호 저장 성패와 무관).
        if (signUpPassword != null) analytics.log(AnalyticsEvent.SignUpEmail)
        analytics.log(AnalyticsEvent.Login(LoginMethod.CODE))
        if (signUpPassword != null) {
            pendingSignUpPassword = null
            val error = try {
                authUseCase.setPassword(signUpPassword, signUpPassword)
                null
            } catch (e: MutterError.SamePassword) {
                null
            } catch (e: Throwable) {
                (e as? MutterError)?.userMessage ?: RETRY_LATER
            }
            _state.value = if (error == null) {
                _state.value.copy(authenticated = true)
            } else {
                _state.value.copy(step = AuthStep.SET_PASSWORD, password = "", passwordConfirm = "", error = error)
            }
            return@run
        }
        val needsSetup = runCatching { authUseCase.loginPasswordStatus() }.getOrNull()?.needsSetup == true
        _state.value = if (needsSetup) {
            _state.value.copy(step = AuthStep.SET_PASSWORD, password = "", passwordConfirm = "")
        } else {
            _state.value.copy(authenticated = true)
        }
    }

    /** "저장하고 시작" — 실패하면 오류만 보이고 단계에 머문다("나중에 하기"로 넘어갈 수 있음). */
    fun savePasswordAndStart() = run {
        authUseCase.setPassword(_state.value.password, _state.value.passwordConfirm)
        _state.value = _state.value.copy(authenticated = true)
    }

    /** "나중에 하기" — 기존 코드 로그인 직후 경로와 동일하게 진입. */
    fun skipPasswordSetup() {
        _state.value = _state.value.copy(authenticated = true, error = null)
    }

    fun signInWithPassword() = run {
        authUseCase.signIn(_state.value.email.trim(), _state.value.password)
        analytics.log(AnalyticsEvent.Login(LoginMethod.PASSWORD))
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
                val message = (e as? MutterError)?.userMessage ?: RETRY_LATER
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
                    is SocialAuthResult.Authenticated -> {
                        analytics.log(AnalyticsEvent.Login(provider.loginMethod))
                        _state.value = _state.value.copy(authenticated = true)
                    }
                    SocialAuthResult.NeedsNickname -> {
                        pendingProvider = provider
                        pendingIdToken = idToken
                        _state.value = _state.value.copy(step = AuthStep.ENTER_SOCIAL_NICKNAME, nickname = "")
                    }
                }
            } catch (e: OauthCancelledException) {
                // 사용자가 로그인 창을 닫음 — 조용히 무시.
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: RETRY_LATER
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
        analytics.log(AnalyticsEvent.Login(provider.loginMethod))
        // 소셜 신규 회원은 닉네임과 함께 가입한다 — 닉네임 온보딩 화면을 거치지 않으므로 가입 완료를 여기서 기록한다.
        analytics.log(AnalyticsEvent.OnboardComplete)
        pendingProvider = null
        pendingIdToken = null
        _state.value = _state.value.copy(authenticated = true)
    }

    /** "비밀번호로 로그인" — 기본 단계로. */
    fun switchToPassword() = move(AuthStep.ENTER_PASSWORD)

    /** "이메일로 회원가입하기" — 입력한 이메일만 남긴다. */
    fun switchToSignUp() = move(AuthStep.SIGN_UP)

    /** "로그인으로 돌아가기"(회원가입·소셜 닉네임) — 기본 단계인 비밀번호 로그인으로. */
    fun backToLogin() {
        clearSocialPending()
        move(AuthStep.ENTER_PASSWORD)
    }

    /** "코드로 로그인" — 코드 로그인 이메일 단계로. */
    fun backToEmail() {
        clearSocialPending()
        move(AuthStep.ENTER_EMAIL)
    }

    /**
     * 코드 단계의 "이메일 다시 입력" — 회원가입 중이었으면 입력해 둔 비밀번호·확인을 살려 회원가입 단계로,
     * 아니면 코드 로그인 이메일 단계로. [move]를 거치지 않는 유일한 전환이다(iOS `backFromCode`와 같다).
     */
    fun backFromCode() {
        val wasSigningUp = pendingSignUpPassword != null
        pendingSignUpPassword = null
        _state.value = _state.value.copy(
            step = if (wasSigningUp) AuthStep.SIGN_UP else AuthStep.ENTER_EMAIL,
            code = "",
            error = null,
        )
    }

    /** 로그인 완료 신호를 받았다 — 같은 화면에서 다시 로그인하면 신호가 다시 가도록 내린다. */
    fun consumeAuthenticated() {
        _state.value = _state.value.copy(authenticated = false)
    }

    /** 로그아웃 뒤 새 로그인 화면으로 — 앞 로그인의 단계·입력·대기값을 버린다(iOS는 로그인 화면마다 새 모델). */
    fun reset() {
        pendingProvider = null
        pendingIdToken = null
        pendingSignUpPassword = null
        _state.value = AuthUiState()
    }

    private fun clearSocialPending() {
        pendingProvider = null
        pendingIdToken = null
        _state.value = _state.value.copy(nickname = "")
    }

    /** 단계 전환 — 이메일만 남기고 입력·오류를 비운다(iOS `move(to:)`). */
    private fun move(next: AuthStep) {
        pendingSignUpPassword = null
        _state.value = _state.value.copy(
            step = next, code = "", password = "", passwordConfirm = "", error = null,
        )
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

/** 분석 `login.method` 값. */
private val SocialProvider.loginMethod: LoginMethod
    get() = when (this) {
        SocialProvider.GOOGLE -> LoginMethod.GOOGLE
        SocialProvider.KAKAO -> LoginMethod.KAKAO
    }

/** 일반 실패 문구(iOS `common.retryLater`). */
private const val RETRY_LATER = "잠시 후 다시 시도해 주세요."
