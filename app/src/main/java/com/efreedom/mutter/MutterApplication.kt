package com.efreedom.mutter

import android.app.Application
import com.efreedom.mutter.domain.repository.TicketStore
import com.efreedom.mutter.push.PushNotifications
import com.kakao.sdk.common.KakaoSdk
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** 앱 진입점 — Hilt 의존성 그래프의 루트. (iOS MutterApp/AppDelegate 대응) */
@HiltAndroidApp
class MutterApplication : Application() {

    /**
     * 편지권 스토어(Play Billing)를 앱 시작 때 만든다. 결제 창에 쓸 Activity를 수명주기 콜백으로 추적하는데,
     * 처음 주입되는 시점이 Compose 첫 프레임(onResume 뒤)이면 지금 Activity를 놓친다 → 여기서 먼저 만든다.
     */
    @Inject lateinit var ticketStore: TicketStore

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
