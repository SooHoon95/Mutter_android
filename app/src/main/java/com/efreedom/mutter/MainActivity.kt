package com.efreedom.mutter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.component.MutterSplashScreen
import com.efreedom.mutter.uicomponent.theme.MutterTheme
import com.efreedom.mutter.feature.auth.AuthScreen
import com.efreedom.mutter.feature.auth.NicknameOnboardScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/** 루트가 그릴 화면(스플래시/로그인/닉네임/메인). */
private enum class RootScreen { SPLASH, AUTH, NICKNAME, MAIN }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 런치스크린(core-splashscreen) 설치 — super 이전에 호출. 첫 프레임 그려지면 자동 해제되고
        // Compose가 곧바로 Lottie 스플래시를 재생한다(launch 마크 → Lottie 자연 전환).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MutterTheme {
                val appViewModel: AppViewModel = hiltViewModel()
                val appState by appViewModel.state.collectAsStateWithLifecycle()

                // 최소 스플래시 노출시간 — 세션 확인이 즉시 끝나도 로고가 잠깐 보이도록(iOS MainView 대응).
                var minSplashDone by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    // Lottie 스플래시(약 2.2초)가 끝까지 재생되도록 최소 노출시간 확보(iOS와 동일).
                    delay(2300)
                    minSplashDone = true
                }

                val screen = when {
                    !minSplashDone || appState == AppState.LOADING -> RootScreen.SPLASH
                    appState == AppState.SIGNED_OUT -> RootScreen.AUTH
                    appState == AppState.NEEDS_NICKNAME -> RootScreen.NICKNAME
                    else -> RootScreen.MAIN
                }

                Crossfade(targetState = screen, animationSpec = tween(300), label = "app-root") { s ->
                    when (s) {
                        RootScreen.SPLASH -> MutterSplashScreen()
                        RootScreen.AUTH -> AuthScreen(onAuthenticated = { appViewModel.refresh() })
                        RootScreen.NICKNAME -> NicknameOnboardScreen(onComplete = { appViewModel.refresh() })
                        RootScreen.MAIN -> MutterNavHost(onSignedOut = { appViewModel.signOut() })
                    }
                }
            }
        }
    }
}
