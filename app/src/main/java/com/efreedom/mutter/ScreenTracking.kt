package com.efreedom.mutter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.efreedom.mutter.core.navigation.FeatureRoute
import com.efreedom.mutter.domain.analytics.Analytics
import com.efreedom.mutter.domain.analytics.AnalyticsScreen

/**
 * 앱 화면 트리가 쓰는 분석 기록기. [MainActivity]가 Hilt로 받은 구현을 넣는다.
 * 기본값은 no-op — 프리뷰처럼 연결되지 않은 트리에서도 그대로 동작한다.
 */
internal val LocalAnalytics = staticCompositionLocalOf<Analytics> { Analytics.NoOp }

/**
 * [screen]이 바뀔 때마다 `screen_view`를 한 번 보낸다(null이면 보내지 않는다).
 * 화면 기록은 이 함수 하나로 모은다 — 루트(로그인·닉네임), NavHost 목적지·탭, 편지 오버레이, 결제 안내 시트.
 */
@Composable
internal fun TrackScreen(screen: AnalyticsScreen?) {
    val analytics = LocalAnalytics.current
    LaunchedEffect(screen) { screen?.let(analytics::screen) }
}

/**
 * NavHost 목적지 → 계약의 화면 이름. 탭 호스트는 선택된 탭으로 정한다([tabScreen]).
 * 계약에 없는 목적지(새 소식 안내 다시 보기)는 null — 기록하지 않는다.
 */
internal fun NavDestination.analyticsScreen(tabScreen: AnalyticsScreen): AnalyticsScreen? =
    when {
        hasRoute<MainTabs>() -> tabScreen
        hasRoute<FeatureRoute.ComposeNew>() ||
            hasRoute<FeatureRoute.ComposeEdit>() ||
            hasRoute<FeatureRoute.ComposeReply>() -> AnalyticsScreen.COMPOSE
        hasRoute<FeatureRoute.ViewerToken>() || hasRoute<FeatureRoute.ViewerMyLetter>() -> AnalyticsScreen.VIEWER
        hasRoute<FeatureRoute.Delivery>() -> AnalyticsScreen.DELIVERY
        hasRoute<FeatureRoute.ConnectInvite>() -> AnalyticsScreen.CONNECT_INVITE
        hasRoute<FeatureRoute.Trash>() -> AnalyticsScreen.TRASH
        hasRoute<FeatureRoute.InboxTrash>() -> AnalyticsScreen.INBOX_TRASH
        hasRoute<FeatureRoute.Tickets>() -> AnalyticsScreen.TICKETS
        hasRoute<FeatureRoute.Takedown>() -> AnalyticsScreen.LEGAL_TAKEDOWN
        else -> null
    }
