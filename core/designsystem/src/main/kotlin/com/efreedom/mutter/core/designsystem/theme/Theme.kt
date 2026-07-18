package com.efreedom.mutter.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ─── Color scheme ─────────────────────────────────────────────────────────────

/**
 * Mutter 라이트 컬러 스킴 (앱은 라이트 전용).
 *
 * Material3 역할 → Mutter 토큰 매핑:
 *   primary            → Gold          (로즈모브 강조)
 *   onPrimary          → OnGold        (강조 위 텍스트)
 *   primaryContainer   → GoldSoft      (연한 강조 표면)
 *   onPrimaryContainer → OnGold
 *   background         → Ivory         (앱 전체 배경)
 *   onBackground       → Ink           (배경 위 기본 텍스트)
 *   surface            → Surface       (카드/시트)
 *   onSurface          → Ink
 *   surfaceVariant     → GoldSoft      (보조 표면)
 *   onSurfaceVariant   → InkSoft
 *   outline            → Hairline      (구분선)
 *   error              → Danger
 */
private val MutterColorScheme = lightColorScheme(
    primary = Gold,
    onPrimary = OnGold,
    primaryContainer = GoldSoft,
    onPrimaryContainer = OnGold,
    secondary = GoldDeep,
    onSecondary = OnGold,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = OnGold,
    background = Ivory,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = GoldSoft,
    onSurfaceVariant = InkSoft,
    outline = Hairline,
    error = Danger,
    onError = Surface,
)

// ─── Theme entry point ────────────────────────────────────────────────────────

/**
 * Mutter 디자인 시스템 테마 래퍼.
 * 라이트 전용 — 다크 모드 미지원.
 */
@Composable
fun MutterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MutterColorScheme,
        typography = MutterTypography,
        content = content,
    )
}
