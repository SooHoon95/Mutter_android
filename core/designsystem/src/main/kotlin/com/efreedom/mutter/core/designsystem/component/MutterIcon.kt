package com.efreedom.mutter.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.core.designsystem.theme.Ink

/**
 * 단색 템플릿 아이콘 — [tint] 색으로 렌더한다(iOS `renderingMode(.template)` 대응).
 *
 * `core:designsystem`의 VectorDrawable(예: `R.drawable.tab_home`, `R.drawable.play`)을
 * 받아 지정 크기·틴트로 그린다. 색이 있는 일러스트는 [MutterImage]를 쓴다.
 */
@Composable
fun MutterIcon(
    @DrawableRes id: Int,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Ink,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * 풀컬러 일러스트/로고 — 원색을 유지한다(틴트 없음).
 *
 * 빈 상태 일러스트(`empty_sent`), 소셜 아이콘(`social_google`), 로고(`onboarding_logo`,
 * `app_mark`)처럼 색 정보를 살려야 하는 이미지를 그린다.
 */
@Composable
fun MutterImage(
    @DrawableRes id: Int,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
