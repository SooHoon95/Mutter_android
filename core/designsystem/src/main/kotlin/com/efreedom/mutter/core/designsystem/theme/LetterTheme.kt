package com.efreedom.mutter.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Data model ──────────────────────────────────────────────────────────────

/**
 * 편지 템플릿 테마 (iOS `LetterTheme` 1:1 이식, 웹 `templates.ts` TemplateTheme 대응).
 *
 * [id]는 웹/DB Letter.templateId와 호환되는 문자열 슬러그.
 * [fontFamily]: serif→FontFamily.Serif, monospaced→FontFamily.Monospace, default→PretendardFamily.
 * [lineHeightMultiplier]: 웹 line-height 배수. 본문 TextStyle에서 lineHeight = bodySize * multiplier.
 */
data class LetterTheme(
    val id: String,
    val name: String,
    val summary: String,
    // Colors
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val accent: Color,
    val border: Color,
    // Typography
    val fontFamily: FontFamily,
    val bodySize: TextUnit,
    val headingSize: TextUnit,
    val lineHeightMultiplier: Float,
    // Layout
    val cornerRadius: Dp,
)

// ─── Theme catalog (웹 templates.ts 7종 이식) ─────────────────────────────────

object LetterThemes {

    /** 클래식 세리프 — 크림 바탕, 앤티크 골드 강조, 명조 */
    val classicSerif = LetterTheme(
        id = "classic-serif",
        name = "클래식 세리프",
        summary = "고전적이고 우아한 분위기",
        background = ClassicSerifBg,
        foreground = ClassicSerifFg,
        muted = ClassicSerifMuted,
        accent = ClassicSerifAccent,
        border = ClassicSerifBorder,
        fontFamily = FontFamily.Serif,
        bodySize = 17.sp,
        headingSize = 22.sp,
        lineHeightMultiplier = 1.85f,
        cornerRadius = 4.dp,
    )

    /** 모던 미니멀 — 화이트 바탕, 블루 강조, sans */
    val modernMinimal = LetterTheme(
        id = "modern-minimal",
        name = "모던 미니멀",
        summary = "깔끔하고 세련된 현대적 감각",
        background = ModernMinimalBg,
        foreground = ModernMinimalFg,
        muted = ModernMinimalMuted,
        accent = ModernMinimalAccent,
        border = ModernMinimalBorder,
        fontFamily = PretendardFamily,
        bodySize = 16.sp,
        headingSize = 20.sp,
        lineHeightMultiplier = 1.75f,
        cornerRadius = 2.dp,
    )

    /** 따뜻한 크래프트 — 황토 바탕, 테라코타 강조, 명조 */
    val warmCraft = LetterTheme(
        id = "warm-craft",
        name = "따뜻한 크래프트",
        summary = "손으로 쓴 듯 따뜻한 감성",
        background = WarmCraftBg,
        foreground = WarmCraftFg,
        muted = WarmCraftMuted,
        accent = WarmCraftAccent,
        border = WarmCraftBorder,
        fontFamily = FontFamily.Serif,
        bodySize = 16.sp,
        headingSize = 21.sp,
        lineHeightMultiplier = 1.80f,
        cornerRadius = 6.dp,
    )

    /** 밤하늘 — 딥 네이비 바탕, 골드 강조, 명조 */
    val nightSky = LetterTheme(
        id = "night-sky",
        name = "밤하늘",
        summary = "별빛 아래 쓴 밤의 편지",
        background = NightSkyBg,
        foreground = NightSkyFg,
        muted = NightSkyMuted,
        accent = NightSkyAccent,
        border = NightSkyBorder,
        fontFamily = FontFamily.Serif,
        bodySize = 17.sp,
        headingSize = 22.sp,
        lineHeightMultiplier = 1.90f,
        cornerRadius = 8.dp,
    )

    /** 봄날 — 핑크 화이트 바탕, 로즈 강조, sans */
    val springDay = LetterTheme(
        id = "spring-day",
        name = "봄날",
        summary = "벚꽃처럼 산뜻한 봄 편지",
        background = SpringDayBg,
        foreground = SpringDayFg,
        muted = SpringDayMuted,
        accent = SpringDayAccent,
        border = SpringDayBorder,
        fontFamily = PretendardFamily,
        bodySize = 16.sp,
        headingSize = 21.sp,
        lineHeightMultiplier = 1.80f,
        cornerRadius = 12.dp,
    )

    /** 빈티지 타자기 — 오래된 종이 바탕, 먹 강조, monospaced */
    val vintageTypewriter = LetterTheme(
        id = "vintage-typewriter",
        name = "빈티지 타자기",
        summary = "오래된 타자기로 찍은 듯한 감성",
        background = VintageTypewriterBg,
        foreground = VintageTypewriterFg,
        muted = VintageTypewriterMuted,
        accent = VintageTypewriterAccent,
        border = VintageTypewriterBorder,
        fontFamily = FontFamily.Monospace,
        bodySize = 15.sp,
        headingSize = 20.sp,
        lineHeightMultiplier = 1.90f,
        cornerRadius = 0.dp,
    )

    /** 순백 공간 — 라이트 그레이 바탕, 다크 강조, sans */
    val pureSpace = LetterTheme(
        id = "pure-space",
        name = "순백 공간",
        summary = "여백으로 말하는 미니멀리즘",
        background = PureSpaceBg,
        foreground = PureSpaceFg,
        muted = PureSpaceMuted,
        accent = PureSpaceAccent,
        border = PureSpaceBorder,
        fontFamily = PretendardFamily,
        bodySize = 18.sp,
        headingSize = 24.sp,
        lineHeightMultiplier = 2.00f,
        cornerRadius = 0.dp,
    )

    /** 피커가 순회하는 전체 목록. 웹 templates.ts 순서 유지. */
    val all: List<LetterTheme> = listOf(
        classicSerif,
        modernMinimal,
        warmCraft,
        nightSky,
        springDay,
        vintageTypewriter,
        pureSpace,
    )

    /** 기본 테마 */
    val default: LetterTheme = classicSerif

    /**
     * id로 테마 조회.
     * 알 수 없는 id는 [default]로 안전 폴백(수신자 렌더 불변식).
     */
    fun byId(id: String): LetterTheme = all.firstOrNull { it.id == id } ?: default
}
