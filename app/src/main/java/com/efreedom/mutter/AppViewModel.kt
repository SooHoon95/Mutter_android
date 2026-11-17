package com.efreedom.mutter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.SessionInvalidation
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.usecase.AppUpdateUseCase
import com.efreedom.mutter.domain.usecase.AuthUseCase
import com.efreedom.mutter.domain.usecase.ProfileUseCase
import com.efreedom.mutter.domain.usecase.TicketUseCase
import com.efreedom.mutter.push.PushTokenRegistrar
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
    private val pushTokenRegistrar: PushTokenRegistrar,
    private val ticketUseCase: TicketUseCase,
    private val whatsNewStore: WhatsNewStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AppState.LOADING)
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** 강제 업데이트 필요 여부 — true면 어떤 화면 위에도 차단 오버레이를 띄운다(iOS ForceUpdateView 대응). */
    private val _forceUpdate = MutableStateFlow(false)
    val forceUpdate: StateFlow<Boolean> = _forceUpdate.asStateFlow()

    /**
     * 새 소식 안내를 띄울 차례면 그 화면에 넣을 서버 값(free_days·grace_days)을 담은 권한, 아니면 null.
     * 실제로 덮는 것은 메인 탭이 보일 때다(MutterNavHost) — 그때 [markWhatsNewSeen]으로 기록한다.
     */
    private val _whatsNew = MutableStateFlow<Entitlements?>(null)
    val whatsNew: StateFlow<Entitlements?> = _whatsNew.asStateFlow()

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

    /**
     * 세션 만료 신호를 구독해 자동 로그아웃(iOS SessionManager.observeUnauthorized 대응).
     * iOS `guard subject.value`처럼 로그인 상태(READY·NEEDS_NICKNAME)에서만 동작한다 — LOADING 중에 오면
     * 버린다(그때는 [refresh]가 Unauthorized를 보고 로그아웃한다. 여기서 SIGNED_OUT으로 바꾸면 refresh가 READY로 덮어쓴다).
     */
    private fun observeSessionInvalidation() {
        viewModelScope.launch {
            SessionInvalidation.events.collect {
                if (_state.value != AppState.READY && _state.value != AppState.NEEDS_NICKNAME) return@collect
                runCatching { authUseCase.signOut() }
                _state.value = AppState.SIGNED_OUT
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val session = runCatching { authUseCase.currentSession() }.getOrNull()
            if (session == null) {
                checkWhatsNew(signedIn = false)
                _state.value = AppState.SIGNED_OUT
                return@launch
            }
            // 권한 조회가 화면 진입을 늦추지 않게 따로 돈다.
            launch { checkWhatsNew(signedIn = true) }
            // 세션이 확인된 시점에 FCM 토큰을 등록한다. onNewToken은 토큰이 "바뀔 때"만 오므로
            // 이미 토큰이 있는 기기에서 로그인하면 이 경로가 유일한 등록 기회다.
            // 프로필 조회 결과와 상관없이(iOS처럼) 기다리지 않고 띄운다 — 오프라인에서 토큰 조회가 멈춰도 스플래시가 멈추지 않게.
            launch { pushTokenRegistrar.register() }
            // 검증·소비 못 하고 남은 편지권 거래를 다시 보낸다(서버 200 뒤에만 소비). 실패는 조용히 다음 기회로.
            launch { ticketUseCase.resendUnconsumed() }
            // 닉네임 게이트 — 프로필 조회는 읽기 전용(SELECT). 회원가입 쓰기 아님.
            // 조회 실패(일시적 네트워크/500)는 기존 로그인 사용자를 닉네임 화면으로 내몰지 않는다.
            // myProfile()은 행이 없으면 null(예외 아님) → 신규 사용자는 여전히 NEEDS_NICKNAME으로 게이트된다.
            // iOS와 다른 점(콜드 스타트 보호): iOS는 콜드스타트에서 닉네임을 재검사하지 않으므로, 실패 시엔 메인으로 진행한다.
            val profileResult = runCatching { profileUseCase.myProfile() }
            if (profileResult.exceptionOrNull() is MutterError.Unauthorized) {
                // 세션이 쓸모없다 — 로컬 세션을 지우고 로그인 화면으로(iOS signOut()). 수집기는 LOADING 중 신호를 버리므로 여기가 유일한 경로.
                // 알고 받아들이는 점: Unauthorized는 세션 만료 외에 FORBIDDEN·not_authorized·AUTH_REQUIRED에서도 나온다 — 본인 프로필 SELECT에서는 세션 문제로 본다.
                runCatching { authUseCase.signOut() }
                _state.value = AppState.SIGNED_OUT
                return@launch
            }
            if (profileResult.isFailure) {
                _state.value = AppState.READY
                return@launch
            }
            val needsNickname = profileResult.getOrNull()?.nickname?.trim().isNullOrEmpty()
            _state.value = if (needsNickname) AppState.NEEDS_NICKNAME else AppState.READY
        }
    }

    /**
     * 새 소식 안내를 띄울지 정한다. 사양 `Mutter/docs/specs/2026-11-15-whats-new.md` "언제 뜨나".
     * 1. 값이 없으면: 로그인돼 있으면 pending(이전 버전을 쓰던 사람), 아니면 seen(새로 설치 — 띄우지 않는다).
     * 2. pending이고 로그인돼 있으면 권한을 받아 `paid_enabled && !is_admin`일 때 띄운다.
     *    아니면 그대로 둔다 — 유료 전환 전에 업데이트한 사람은 전환된 뒤 처음 열 때 본다.
     * 3. 권한 조회가 실패하면 아무것도 하지 않는다(다음 실행 때 다시). 4. seen이면 다시 뜨지 않는다.
     */
    private suspend fun checkWhatsNew(signedIn: Boolean) {
        if (whatsNewStore.status == null) {
            whatsNewStore.status = if (signedIn) WhatsNewStore.Status.PENDING else WhatsNewStore.Status.SEEN
        }
        if (!signedIn) {
            // 로그아웃하면 띄우려던 것도 거둔다 — 다음 계정에게 앞 계정의 값으로 뜨지 않게.
            _whatsNew.value = null
            return
        }
        if (whatsNewStore.status != WhatsNewStore.Status.PENDING) return
        val entitlements = ticketUseCase.entitlementsOrNull() ?: return
        if (entitlements.paidEnabled && !entitlements.isAdmin) _whatsNew.value = entitlements
    }

    /** 새 소식 안내가 화면에 떴다 — 다시 뜨지 않게 기록한다. */
    fun markWhatsNewSeen() {
        whatsNewStore.status = WhatsNewStore.Status.SEEN
    }

    /** 새 소식 안내를 닫았다("건너뛰기"·"시작하기"·뒤로). */
    fun dismissWhatsNew() {
        _whatsNew.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            // 서버 토큰 행이 남아 다음 사용자가 이전 계정 알림을 받는 것을 막는다(로그아웃 전에 폐기).
            pushTokenRegistrar.unregister()
            runCatching { authUseCase.signOut() }
            refresh()
        }
    }
}
