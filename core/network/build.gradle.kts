// :core:network — Supabase 접속 단일 진입점(iOS Network 모듈 대응). Hilt 없음(순수 라이브러리).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.efreedom.mutter.core.network"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform(libs.supabase.bom))
    api(libs.supabase.auth)
    api(libs.supabase.postgrest)
    api(libs.supabase.functions)
    // 편지 사진(비공개 버킷 letter-photos) 업로드·삭제.
    api(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    // OAuth 콜백을 자체 스코프에서 처리한다(SupabaseProvider.handleOAuthCallback).
    implementation(libs.kotlinx.coroutines.core)
    // 세션 영구 저장(SharedPreferences 기반 Settings) — 자동 로그인.
    implementation("com.russhwolf:multiplatform-settings:1.3.0")
}
