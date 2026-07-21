package com.efreedom.mutter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val _state = MutableStateFlow(AppState.LOADING)
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val session = runCatching { authUseCase.currentSession() }.getOrNull()
            if (session == null) {
                _state.value = AppState.SIGNED_OUT
                return@launch
            }
            // 닉네임 게이트 — 프로필 조회는 읽기 전용(SELECT). 회원가입 쓰기 아님.
            val profile = runCatching { profileUseCase.myProfile() }.getOrNull()
            val needsNickname = profile?.nickname?.trim().isNullOrEmpty()
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
