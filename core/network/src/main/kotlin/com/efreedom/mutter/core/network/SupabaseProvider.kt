package com.efreedom.mutter.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Supabase 접속의 단일 진입점(iOS SupabaseProvider 대응).
 * 하나의 구성된 SupabaseClient를 :data Repository들이 공유한다.
 * url/anonKey는 생성자 주입(호출부 DI가 BuildConfig에서 읽어 넘긴다 — 기존 백엔드 재사용).
 * Auth 세션은 supabase-kt 기본 저장소로 유지된다(autoRefresh 포함).
 */
class SupabaseProvider(
    supabaseUrl: String,
    supabaseKey: String,
) {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey,
    ) {
        install(Auth)
        install(Postgrest)
        install(Functions)
    }
}
