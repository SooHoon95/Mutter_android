package com.efreedom.mutter

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import dagger.hilt.android.HiltAndroidApp

/** 앱 진입점 — Hilt 의존성 그래프의 루트. (iOS MutterApp/AppDelegate 대응) */
@HiltAndroidApp
class MutterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Kakao SDK 초기화(iOS AppDelegate의 KakaoSDK.initSDK 대응). 키는 BuildConfig←local.properties.
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotEmpty()) {
            KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
    }
}
