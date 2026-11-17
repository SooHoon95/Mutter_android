package com.efreedom.mutter.data.analytics

import android.content.Context
import android.os.Bundle
import com.efreedom.mutter.domain.analytics.Analytics
import com.efreedom.mutter.domain.analytics.AnalyticsEvent
import com.efreedom.mutter.domain.analytics.AnalyticsScreen
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [Analytics]의 Firebase(GA4) 구현. 광고 ID 수집·광고 개인화 신호·자동 화면 기록은 매니페스트 meta-data로 끈다.
 * 사용자 ID(setUserId)·사용자 속성은 쓰지 않는다. 기록 실패는 삼킨다(fire-and-forget).
 */
@Singleton
class FirebaseAnalyticsTracker @Inject constructor(
    @ApplicationContext context: Context,
) : Analytics {

    private val firebase: FirebaseAnalytics? = runCatching { FirebaseAnalytics.getInstance(context) }.getOrNull()

    override fun log(event: AnalyticsEvent) {
        val bundle = Bundle().apply { event.params.forEach { (key, value) -> putString(key, value) } }
        runCatching { firebase?.logEvent(event.name, bundle) }
    }

    override fun screen(screen: AnalyticsScreen) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screen.value)
            // Compose는 액티비티 하나라 클래스 이름이 의미가 없다 — 화면 이름을 그대로 쓴다.
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screen.value)
        }
        runCatching { firebase?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle) }
    }
}
