package com.efreedom.mutter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.component.MutterSplashScreen
import com.efreedom.mutter.uicomponent.theme.MutterTheme
import com.efreedom.mutter.feature.auth.AuthScreen
import com.efreedom.mutter.feature.auth.NicknameOnboardScreen
import com.efreedom.mutter.feature.viewer.ViewerScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** 루트가 그릴 화면(스플래시/로그인/닉네임/메인). */
private enum class RootScreen { SPLASH, AUTH, NICKNAME, MAIN }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 들어온 딥링크 이벤트(콜드스타트 intent + onNewIntent 공통). 컴포즈가 소비 후 null로 되돌린다.
    private val incomingDeeplink = MutableStateFlow<Deeplink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 런치스크린(core-splashscreen) 설치 — super 이전에 호출. 첫 프레임 그려지면 자동 해제되고
        // Compose가 곧바로 Lottie 스플래시를 재생한다(launch 마크 → Lottie 자연 전환).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 최초 실행에서만 파싱 — 회전/다크모드 등 구성 변경 재생성 때 같은 링크를 재처리하지 않도록.
        if (savedInstanceState == null) {
            incomingDeeplink.value = Deeplink.parse(intent?.data)
        }
        setContent {
            MutterTheme {
                val appViewModel: AppViewModel = hiltViewModel()
                val appState by appViewModel.state.collectAsStateWithLifecycle()
                val deeplink by incomingDeeplink.collectAsStateWithLifecycle()

                // 최소 스플래시 노출시간 — 세션 확인이 즉시 끝나도 로고가 잠깐 보이도록(iOS MainView 대응).
                var minSplashDone by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    // Lottie 스플래시(약 2.2초)가 끝까지 재생되도록 최소 노출시간 확보(iOS와 동일).
                    delay(2300)
                    minSplashDone = true
                }

                // 딥링크 라우팅 상태 — 편지는 즉시 전체화면 오버레이, 초대는 로그인까지 보관 후 소비.
                // rememberSaveable — 구성 변경(회전 등)에도 유지돼, 닫은 뷰어가 다시 뜨거나 초대가 재이동되지 않게.
                var letterToken by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingConnectToken by rememberSaveable { mutableStateOf<String?>(null) }
                LaunchedEffect(deeplink) {
                    when (val d = deeplink) {
                        is Deeplink.Letter -> letterToken = d.token
                        is Deeplink.Connect -> pendingConnectToken = d.token
                        null -> Unit
                    }
                    if (deeplink != null) incomingDeeplink.value = null
                }

                val screen = when {
                    !minSplashDone || appState == AppState.LOADING -> RootScreen.SPLASH
                    appState == AppState.SIGNED_OUT -> RootScreen.AUTH
                    appState == AppState.NEEDS_NICKNAME -> RootScreen.NICKNAME
                    else -> RootScreen.MAIN
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(targetState = screen, animationSpec = tween(300), label = "app-root") { s ->
                        when (s) {
                            RootScreen.SPLASH -> MutterSplashScreen()
                            RootScreen.AUTH -> AuthScreen(onAuthenticated = { appViewModel.refresh() })
                            RootScreen.NICKNAME -> NicknameOnboardScreen(onComplete = { appViewModel.refresh() })
                            RootScreen.MAIN -> MutterNavHost(
                                onSignedOut = { appViewModel.signOut() },
                                pendingConnectToken = pendingConnectToken,
                                onConnectConsumed = { pendingConnectToken = null },
                            )
                        }
                    }

                    // 편지 링크 — 인증 상태와 무관하게 전체화면 뷰어로 덮는다(iOS unauthenticatedViewerCover 대응).
                    // get_letter_by_token은 anon 허용이라 미로그인에서도 열람 가능. 뒤로가기로 오버레이 해제.
                    letterToken?.let { token ->
                        // 오버레이는 NavHost 백스택 밖이므로 시스템 뒤로가기를 직접 가로채 해제한다.
                        BackHandler { letterToken = null }
                        ViewerScreen(token = token, onBack = { letterToken = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingDeeplink.value = Deeplink.parse(intent.data)
    }
}
