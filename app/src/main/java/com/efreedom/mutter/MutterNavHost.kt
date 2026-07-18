package com.efreedom.mutter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.efreedom.mutter.core.designsystem.theme.Gold
import com.efreedom.mutter.core.designsystem.theme.GoldSoft
import com.efreedom.mutter.core.designsystem.theme.InkSoft
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

/** 하단 탭 정의 (라벨·아이콘). */
private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Default.Home),
    THREADS("편지함", Icons.Default.Email),
    INBOX("받은함", Icons.Default.Inbox),
    CONNECTIONS("연결", Icons.Default.People),
    PROFILE("프로필", Icons.Default.Person),
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
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = {
                            Icon(imageVector = tab.icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Gold,
                            selectedTextColor = Gold,
                            indicatorColor = GoldSoft,
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
