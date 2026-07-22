package com.efreedom.mutter.core.network

import android.content.Context
import android.content.Intent
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

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

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey,
    ) {
        install(Auth) {
            sessionManager = SettingsSessionManager(authSettings)
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
            autoSaveToStorage = true
        }
        install(Postgrest)
        install(Functions)
    }

    /**
     * OAuth 리다이렉트(예: Apple 웹 로그인 후 `mutter://login-callback`) 인텐트를 처리한다.
     * 세션 확립에 성공하면 [onSession]을 호출한다(앱 루트가 상태를 갱신하도록). 비-OAuth 인텐트는 무시된다.
     */
    fun handleOAuthCallback(intent: Intent, onSession: () -> Unit) {
        client.handleDeeplinks(intent) { onSession() }
    }
}
