package com.efreedom.mutter.core.network

import android.content.Context
import android.content.Intent
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromFragment
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Apple 웹 OAuth 리다이렉트 주소. AuthRepository의 `signInAppleWeb()`가 넘기는 값과 같아야 한다. */
const val OAUTH_REDIRECT_URL = "mutter://login-callback"

private const val OAUTH_SCHEME = "mutter"
private const val OAUTH_HOST = "login-callback"

/**
 * Supabase 접속의 단일 진입점(iOS SupabaseProvider 대응).
 * 하나의 구성된 SupabaseClient를 :data Repository들이 공유한다.
 * url/anonKey는 생성자 주입(호출부 DI가 BuildConfig에서 읽어 넘긴다 — 기존 백엔드 재사용).
 *
 * 세션은 앱 전용 SharedPreferences에 영구 저장한다(iOS Keychain 대응) → 앱 재시작 시 자동 로그인.
 * supabase-kt 기본 no-arg SettingsSessionManager는 Android에서 컨텍스트 없는 Settings라 영구화가
 * 불안정하므로, SharedPreferences 기반 Settings를 명시적으로 주입한다.
 */
class SupabaseProvider(
    context: Context,
    supabaseUrl: String,
    supabaseKey: String,
) {
    private val authSettings = SharedPreferencesSettings(
        context.getSharedPreferences("mutter_supabase_auth", Context.MODE_PRIVATE),
    )

    // OAuth 콜백 전용 스코프. SupervisorJob으로 실패를 격리한다 —
    // supabase-kt의 내부 authScope는 SupervisorJob도 예외 핸들러도 없어 예외 하나가 프로세스를 죽인다.
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey,
    ) {
        install(Auth) {
            // scheme/host는 일부러 두지 않는다 — supabase-kt의 handleDeeplinks를 쓰지 않기 때문(아래 참고).
            // 채우면 defaultRedirectUrl()이 딥링크로 바뀌어 이메일 OTP 메일의 링크 목적지까지 함께 바뀐다.
            sessionManager = SettingsSessionManager(authSettings)
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
            autoSaveToStorage = true
        }
        install(Postgrest)
        install(Functions)
    }

    /**
     * Apple 웹 OAuth 리다이렉트(`mutter://login-callback#access_token=...`)를 세션으로 확립한다.
     * 성공 시에만 [onSession]을 호출한다. 우리 콜백이 아닌 인텐트(편지·초대 딥링크 등)는 무시된다.
     *
     * supabase-kt의 `handleDeeplinks`를 쓰지 않는 이유 — 정상 토큰이 아닌 콜백에서 앱이 죽는다:
     *  1. 사용자가 Apple 화면에서 취소하면 `#error=access_denied`로 돌아오는데,
     *     `parseSessionFromFragment`가 "No access token found"로 **메인 스레드에서** throw한다.
     *     (fragment 세그먼트에 '='가 없으면 IndexOutOfBounds도 난다.)
     *  2. 토큰이 만료·위조면 `retrieveUser`가 supabase-kt 내부 authScope에서 throw하는데,
     *     그 시점엔 `handleDeeplinks`가 이미 리턴한 뒤라 **호출부 try/catch로도 잡히지 않는다.**
     *
     * 취소는 에러가 아니므로 조용히 삼키고 로그인 화면을 유지한다.
     */
    fun handleOAuthCallback(intent: Intent, onSession: () -> Unit) {
        val data = intent.data ?: return
        if (data.scheme != OAUTH_SCHEME || data.host != OAUTH_HOST) return
        val fragment = data.fragment ?: return

        // 동기 파싱 — 취소/에러 콜백은 여기서 걸러진다.
        val session = runCatching { client.auth.parseSessionFromFragment(fragment) }.getOrNull() ?: return

        callbackScope.launch {
            runCatching {
                val user = client.auth.retrieveUser(session.accessToken)
                client.auth.importSession(session.copy(user = user), autoRefresh = true)
            }.onSuccess { onSession() }
        }
    }
}
