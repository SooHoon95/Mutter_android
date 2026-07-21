package com.efreedom.mutter.core.designsystem.component

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.efreedom.mutter.core.designsystem.R
import com.efreedom.mutter.core.designsystem.theme.Ivory

/**
 * 앱 스플래시 — iOS `MutterSplashView`와 동일하게 Lottie(`SplashAnimation.json`)를
 * 아이보리 배경에 1회 재생한다. iOS UIComponent의 SplashAnimation.json을 그대로 가져왔다(res/raw).
 *
 * 텍스트 레이어 폰트 매핑(json fName → Android Typeface):
 *  - "Pretendard" → 번들 Pretendard(태그라인 "음악에 마음을 얹어")
 *  - "NanumMyeongjo" → serif(Noto Serif CJK, 명조체 — 워드마크 "Mutter"). iOS도 Nanum을 번들하지 않아
 *    시스템 serif로 폴백하므로 동일한 결과.
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
    val context = LocalContext.current
    val fontMap = remember {
        mapOf(
            "Pretendard" to context.resources.getFont(R.font.pretendard_medium),
            "NanumMyeongjo" to Typeface.create(Typeface.SERIF, Typeface.BOLD),
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            fontMap = fontMap,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
