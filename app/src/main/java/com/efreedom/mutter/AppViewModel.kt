package com.efreedom.mutter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.SessionInvalidation
import com.efreedom.mutter.domain.usecase.AppUpdateUseCase
import com.efreedom.mutter.domain.usecase.AuthUseCase
import com.efreedom.mutter.domain.usecase.ProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 앱 루트 상태 (iOS MainView + AuthViewWrapper 게이트 이식). */
enum class AppState {
    /** 세션/프로필 확인 중 */
    LOADING,

    /** 미로그인 → 로그인 화면 */
    SIGNED_OUT,

    /** 로그인됐으나 닉네임 미설정 → 닉네임 온보딩(회원가입 완료 전) */
    NEEDS_NICKNAME,

    /** 로그인 + 닉네임 완료 → 메인 */
    READY,
}

/**
 * 앱 루트 세션/온보딩 상태.
 *
 * 인증만으로 메인에 들여보내지 않는다 — iOS/웹과 동일하게 닉네임(프로필)이 있어야 [READY].
 * 닉네임 판정: `needsName = !profile?.nickname?.trim()` (웹 Home.tsx와 동일).
 * 프로필 생성(회원가입) API는 닉네임 화면의 저장 시점([ProfileUseCase.updateNickname])에만 호출된다.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val authUseCase: AuthUseCase,
    private val profileUseCase: ProfileUseCase,
    private val appUpdateUseCase: AppUpdateUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AppState.LOADING)
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** 강제 업데이트 필요 여부 — true면 어떤 화면 위에도 차단 오버레이를 띄운다(iOS ForceUpdateView 대응). */
    private val _forceUpdate = MutableStateFlow(false)
    val forceUpdate: StateFlow<Boolean> = _forceUpdate.asStateFlow()

    init {
        refresh()
        observeSessionInvalidation()
        checkForceUpdate()
    }

    /** 현재 버전이 서버 최소 지원 버전 미만이면 차단. 조회 실패는 fail-open(막지 않음). */
    private fun checkForceUpdate() {
        viewModelScope.launch {
            _forceUpdate.value =
                runCatching { appUpdateUseCase.isUpdateRequired(BuildConfig.VERSION_NAME) }.getOrDefault(false)
        }
    }

    /** 세션 만료 신호를 구독해 자동 로그아웃(iOS SessionManager.observeUnauthorized 대응). */
    private fun observeSessionInvalidation() {
        viewModelScope.launch {
            SessionInvalidation.events.collect {
                runCatching { authUseCase.signOut() }
                _state.value = AppState.SIGNED_OUT
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val session = runCatching { authUseCase.currentSession() }.getOrNull()
            if (session == null) {
                _state.value = AppState.SIGNED_OUT
                return@launch
            }
            // 닉네임 게이트 — 프로필 조회는 읽기 전용(SELECT). 회원가입 쓰기 아님.
            // 조회 실패(일시적 네트워크/500)는 기존 로그인 사용자를 닉네임 화면으로 내몰지 않는다.
            // myProfile()은 행이 없으면 null(예외 아님) → 신규 사용자는 여전히 NEEDS_NICKNAME으로 게이트된다.
            // iOS는 콜드스타트에서 닉네임을 재검사하지 않으므로, 실패 시엔 메인으로 진행한다.
            val profileResult = runCatching { profileUseCase.myProfile() }
            if (profileResult.isFailure) {
                _state.value = AppState.READY
                return@launch
            }
            val needsNickname = profileResult.getOrNull()?.nickname?.trim().isNullOrEmpty()
            _state.value = if (needsNickname) AppState.NEEDS_NICKNAME else AppState.READY
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { authUseCase.signOut() }
            refresh()
        }
    }
}
