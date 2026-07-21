package com.efreedom.mutter.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.efreedom.mutter.core.designsystem.R
import com.efreedom.mutter.core.designsystem.theme.InkSoft

// 워드마크 그라데이션 (iOS onboardingLogo iv-word: 로즈 → 모브 → 플럼)
private val WordmarkBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFE3A9CF), Color(0xFFC77BAE), Color(0xFF8E4E7E)),
)

/**
 * Mutter 브랜드 로고 락업 — 편지 마크 + "Mutter" 워드마크(serif 그라데이션) + 태그라인.
 *
 * iOS `onboardingLogo.svg`를 이식한다. SVG의 `<text>`는 VectorDrawable로 표현할 수 없어
 * (그래서 s2v가 워드마크를 드롭했다) 워드마크·태그라인을 Compose `Text`로 직접 그린다.
 */
@Composable
fun MutterWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 96.dp,
    wordmarkSize: Int = 40,
    showTagline: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.mutter_mark),
            contentDescription = null,
            modifier = Modifier.size(markSize),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Mutter",
            style = TextStyle(
                brush = WordmarkBrush,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = wordmarkSize.sp,
                letterSpacing = (-1).sp,
            ),
        )
        if (showTagline) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "음악에 마음을 얹어",
                style = TextStyle(fontSize = 14.sp),
                color = InkSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}
