package com.efreedom.mutter.uicomponent.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.efreedom.mutter.uicomponent.R
import com.efreedom.mutter.uicomponent.theme.Ivory

/**
 * 앱 스플래시 — iOS `MutterSplashView`와 동일하게 Lottie(`SplashAnimation.json`)를
 * 아이보리 배경에 1회 재생한다. iOS UIComponent의 SplashAnimation.json을 그대로 가져왔다(res/raw).
 * 파일은 iOS 저장소의 `scripts/splash/build_splash_lottie.py`가 만든다 — 고칠 때는 거기서 만들어 두 곳에 넣는다.
 *
 * 글자("Mutter"·태그라인)는 윤곽선으로 들어 있어 글꼴 매핑이 필요 없다.
 * 화면 맞춤은 iOS(aspect fill)와 같은 Crop — Fit이면 세로로 긴 화면에서 iOS보다 작게 보인다.
 */
@Composable
fun MutterSplashScreen(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.splash_animation),
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
