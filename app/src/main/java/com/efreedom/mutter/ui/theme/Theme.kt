package com.efreedom.mutter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// 웜 아이보리 + 골드 라이트 스킴(iOS 룩 시드). 단일 라이트 테마 — 앱은 Light 고정(iOS와 동일).
private val MutterLightColors = lightColorScheme(
    primary = Gold,
    onPrimary = Ivory,
    secondary = GoldDeep,
    background = Ivory,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    onSurfaceVariant = InkSoft,
)

@Composable
fun MutterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MutterLightColors,
        typography = MutterTypography,
        content = content,
    )
}
