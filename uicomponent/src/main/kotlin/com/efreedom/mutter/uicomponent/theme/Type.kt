package com.efreedom.mutter.uicomponent.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.efreedom.mutter.uicomponent.R

// ─── Font family ─────────────────────────────────────────────────────────────

/**
 * Pretendard 가변 폰트 패밀리.
 * res/font/pretendard_{regular,medium,semibold,bold,light}.ttf 기반.
 */
val PretendardFamily = FontFamily(
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

// ─── Named text styles (11 semantic tokens, iOS MutterFont 1:1) ──────────────

/**
 * Mutter 타이포그래피 토큰.
 *
 * - display/titleLarge/title: FontFamily.Serif (명조 감성, iOS .serif design과 대응)
 * - 나머지: PretendardFamily (sans)
 * - lineHeight는 iOS 값을 그대로 이식.
 */
object MutterText {
    /** 워드마크/히어로 — 명조 40 Bold */
    val display = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
    )

    /** 편지 제목 / 화면 큰 제목 — 명조 24 Bold */
    val titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    )

    /** 섹션 제목 — 명조 20 SemiBold */
    val title = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    )

    /** 헤드라인 — sans 18 SemiBold */
    val headline = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    )

    /** 버튼/강조 본문 — sans 17 Bold */
    val bodyLargeBold = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 26.sp,
    )

    /** 본문 큰 — sans 17 Regular */
    val bodyLarge = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
    )

    /** 본문 강조 — sans 16 SemiBold */
    val bodyMediumBold = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /** 본문 — sans 16 Regular */
    val bodyMedium = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /** 보조 본문 — sans 15 Regular */
    val bodySmall = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    /** 캡션 강조 — sans 13 SemiBold */
    val captionBold = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    /** 캡션 — sans 13 Regular */
    val caption = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}

// ─── Material3 Typography ─────────────────────────────────────────────────────

/**
 * Material3 [Typography] — MutterFont 토큰을 Material 슬롯에 매핑.
 *
 * 매핑 기준:
 *   displayLarge   → display       (40 Bold Serif)
 *   headlineLarge  → titleLarge    (24 Bold Serif)
 *   headlineMedium → title         (20 SemiBold Serif)
 *   titleLarge     → headline      (18 SemiBold Pretendard)
 *   titleMedium    → bodyMediumBold(16 SemiBold Pretendard)
 *   bodyLarge      → bodyLarge     (17 Regular Pretendard)
 *   bodyMedium     → bodyMedium    (16 Regular Pretendard)
 *   bodySmall      → bodySmall     (15 Regular Pretendard)
 *   labelLarge     → bodyLargeBold (17 Bold Pretendard)
 *   labelMedium    → captionBold   (13 SemiBold Pretendard)
 *   labelSmall     → caption       (13 Regular Pretendard)
 */
val MutterTypography = Typography(
    displayLarge = MutterText.display,
    headlineLarge = MutterText.titleLarge,
    headlineMedium = MutterText.title,
    titleLarge = MutterText.headline,
    titleMedium = MutterText.bodyMediumBold,
    bodyLarge = MutterText.bodyLarge,
    bodyMedium = MutterText.bodyMedium,
    bodySmall = MutterText.bodySmall,
    labelLarge = MutterText.bodyLargeBold,
    labelMedium = MutterText.captionBold,
    labelSmall = MutterText.caption,
)
