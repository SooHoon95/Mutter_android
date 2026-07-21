package com.efreedom.mutter

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.efreedom.mutter.core.designsystem.R as DsR
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.efreedom.mutter.core.designsystem.theme.Gold
import com.efreedom.mutter.core.designsystem.theme.InkSoft
import com.efreedom.mutter.core.designsystem.theme.Ivory
import com.efreedom.mutter.core.navigation.FeatureRoute
import com.efreedom.mutter.feature.compose.ComposeMode
import com.efreedom.mutter.feature.compose.ComposeScreen
import com.efreedom.mutter.feature.connections.ConnectInviteScreen
import com.efreedom.mutter.feature.connections.ConnectionsScreen
import com.efreedom.mutter.feature.delivery.DeliveryScreen
import com.efreedom.mutter.feature.home.HomeScreen
import com.efreedom.mutter.feature.inbox.InboxScreen
import com.efreedom.mutter.feature.legal.LegalDocScreen
import com.efreedom.mutter.feature.legal.TakedownScreen
import com.efreedom.mutter.feature.profile.ProfileScreen
import com.efreedom.mutter.feature.threads.ThreadsScreen
import com.efreedom.mutter.feature.viewer.ViewerScreen
import kotlinx.serialization.Serializable

// ─── 앱 루트 NavHost ──────────────────────────────────────────────────────────

/** 탭 호스트 루트 목적지 — 직렬화 가능하도록 @Serializable 선언. */
@Serializable
private data object MainTabs

/** 하단 탭 정의 — iOS 커스텀 탭 아이콘(라인/채움 페어) 사용. */
private enum class AppTab(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val iconFill: Int,
) {
    HOME(R.string.tab_home, DsR.drawable.tab_home, DsR.drawable.tab_home_fill),
    THREADS(R.string.tab_threads, DsR.drawable.tab_thread, DsR.drawable.tab_thread_fill),
    INBOX(R.string.tab_inbox, DsR.drawable.tab_inbox, DsR.drawable.tab_inbox_fill),
    CONNECTIONS(R.string.tab_connections, DsR.drawable.tab_people, DsR.drawable.tab_people_fill),
    PROFILE(R.string.tab_profile, DsR.drawable.tab_profile, DsR.drawable.tab_profile_fill),
}

/**
 * 앱 전체 NavigationGraph 진입점.
 *
 * - startDestination: MainTabs (탭 호스트)
 * - Detail 목적지: Compose/Viewer/Delivery/ConnectInvite/Legal
 * - 세션 만료(로그아웃) 시 [onSignedOut] 콜백을 통해 상위 AppViewModel 갱신.
 */
@Composable
fun MutterNavHost(
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = MainTabs,
        modifier = modifier,
    ) {
        // ─── 탭 호스트 ─────────────────────────────────────────────────────
        composable<MainTabs> {
            MainScaffold(navController = navController, onSignedOut = onSignedOut)
        }

        // ─── 편지 작성 ─────────────────────────────────────────────────────
        composable<FeatureRoute.ComposeNew> {
            ComposeScreen(
                mode = ComposeMode.New,
                onDone = { navController.popBackStack() },
            )
        }
        composable<FeatureRoute.ComposeEdit> { entry ->
            val route = entry.toRoute<FeatureRoute.ComposeEdit>()
            ComposeScreen(
                mode = ComposeMode.Edit(route.letterId),
                onDone = { navController.popBackStack() },
            )
        }
        composable<FeatureRoute.ComposeReply> { entry ->
            val route = entry.toRoute<FeatureRoute.ComposeReply>()
            ComposeScreen(
                mode = ComposeMode.Reply(route.recipientId),
                onDone = { navController.popBackStack() },
            )
        }

        // ─── 편지 열람 ─────────────────────────────────────────────────────
        composable<FeatureRoute.ViewerToken> { entry ->
            val route = entry.toRoute<FeatureRoute.ViewerToken>()
            ViewerScreen(
                token = route.token,
                onBack = { navController.popBackStack() },
            )
        }
        composable<FeatureRoute.ViewerMyLetter> { entry ->
            val route = entry.toRoute<FeatureRoute.ViewerMyLetter>()
            ViewerScreen(
                letterId = route.letterId,
                onBack = { navController.popBackStack() },
            )
        }

        // ─── 전달 관리 ─────────────────────────────────────────────────────
        composable<FeatureRoute.Delivery> { entry ->
            val route = entry.toRoute<FeatureRoute.Delivery>()
            DeliveryScreen(
                letterId = route.letterId,
                onBack = { navController.popBackStack() },
                onPreview = { navController.navigate(FeatureRoute.ViewerMyLetter(route.letterId)) },
            )
        }

        // ─── 연결 초대 ─────────────────────────────────────────────────────
        composable<FeatureRoute.ConnectInvite> { entry ->
            val route = entry.toRoute<FeatureRoute.ConnectInvite>()
            ConnectInviteScreen(
                token = route.token,
                onAccepted = { navController.popBackStack() },
            )
        }

        // ─── 법적 문서 ─────────────────────────────────────────────────────
        composable<FeatureRoute.Takedown> {
            TakedownScreen(onBack = { navController.popBackStack() })
        }
        composable<FeatureRoute.Terms> {
            LegalDocScreen(title = "이용약관", onBack = { navController.popBackStack() })
        }
        composable<FeatureRoute.Privacy> {
            LegalDocScreen(title = "개인정보처리방침", onBack = { navController.popBackStack() })
        }
    }
}

// ─── 탭 호스트 Scaffold ───────────────────────────────────────────────────────

/**
 * 5탭 NavigationBar + 탭별 화면 전환 Scaffold.
 *
 * 탭 선택 상태는 [rememberSaveable]로 재구성 후에도 유지.
 * 탭 콜백 → NavController로 Detail 화면 push.
 */
@Composable
private fun MainScaffold(
    navController: NavController,
    onSignedOut: () -> Unit,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = AppTab.entries

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Ivory) {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedIndex == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedIndex = index },
                        icon = {
                            Icon(
                                painter = painterResource(if (selected) tab.iconFill else tab.icon),
                                contentDescription = stringResource(tab.label),
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Gold,
                            selectedTextColor = Gold,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = InkSoft,
                            unselectedTextColor = InkSoft,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (tabs[selectedIndex]) {
                AppTab.HOME -> HomeScreen(
                    onCompose = { navController.navigate(FeatureRoute.ComposeNew) },
                    onEditDraft = { id -> navController.navigate(FeatureRoute.ComposeEdit(id)) },
                    onOpenDelivery = { id -> navController.navigate(FeatureRoute.Delivery(id)) },
                    modifier = Modifier.fillMaxSize(),
                )
                AppTab.THREADS -> ThreadsScreen(
                    onReply = { recipientId -> navController.navigate(FeatureRoute.ComposeReply(recipientId)) },
                    onOpen = { token -> navController.navigate(FeatureRoute.ViewerToken(token)) },
                    modifier = Modifier.fillMaxSize(),
                )
                AppTab.INBOX -> InboxScreen(
                    onOpen = { token -> navController.navigate(FeatureRoute.ViewerToken(token)) },
                    modifier = Modifier.fillMaxSize(),
                )
                AppTab.CONNECTIONS -> ConnectionsScreen(modifier = Modifier.fillMaxSize())
                AppTab.PROFILE -> ProfileScreen(
                    onSignedOut = onSignedOut,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
