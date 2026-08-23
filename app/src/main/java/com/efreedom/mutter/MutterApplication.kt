package com.efreedom.mutter

import android.app.Application
import com.efreedom.mutter.push.PushNotifications
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
        // 알림 채널은 앱 프로세스가 살아날 때마다 보장해야 한다 — 백그라운드 수신 시점에는
        // 액티비티가 없을 수 있고, 채널이 없으면 Android 8+ 에서 알림이 조용히 버려진다.
        PushNotifications.ensureChannel(this)
    }
}
