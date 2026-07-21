package com.efreedom.mutter.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.core.designsystem.theme.BlushLavender
import com.efreedom.mutter.core.designsystem.theme.BlushPink
import com.efreedom.mutter.core.designsystem.theme.GoldLight
import com.efreedom.mutter.core.designsystem.theme.GoldSoft
import com.efreedom.mutter.core.designsystem.theme.Ink
import com.efreedom.mutter.core.designsystem.theme.MutterText
import com.efreedom.mutter.core.designsystem.theme.OnGold

// ─── Style enum ──────────────────────────────────────────────────────────────

/**
 * Mutter 버튼 스타일.
 *
 * - [Primary]: 골드 포일 그라데이션 CTA (goldLight→blushPink→blushLavender, 135° 대각선).
 * - [Secondary]: 연한 골드(GoldSoft) 표면 + Ink 텍스트.
 * - [Ghost]: 배경 없는 텍스트 버튼.
 */
enum class MutterButtonStyle {
    Primary,
    Secondary,
    Ghost,
}

// ─── Gradient brush (iOS MutterGradient.gold 이식) ───────────────────────────

/** goldLight → blushPink → blushLavender (topStart→bottomEnd, iOS와 동일 방향). */
private val GoldGradientBrush = Brush.linearGradient(
    colors = listOf(GoldLight, BlushPink, BlushLavender),
)

// ─── Composable ──────────────────────────────────────────────────────────────

/**
 * Mutter 기본 버튼 (iOS `MutterButton` 1:1 이식).
 *
 * Material3 [androidx.compose.material3.Button] 대신 단일 [Box]로 구현한다.
 * Material Button은 content padding + elevation 때문에 그라데이션 표면 주위로 회색 프레임이
 * 생겨서, iOS처럼 배경(그라데이션/표면)이 버튼 전체를 채우도록 직접 그린다. 그림자 없음(flat).
 */
@Composable
fun MutterButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MutterButtonStyle = MutterButtonStyle.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 프레스 시 살짝 줄어드는 효과 (iOS PressableButtonStyle 대응)
    val scale = if (isPressed) 0.97f else 1f
    val shape = RoundedCornerShape(12.dp) // MutterRadius.lg
    val active = enabled && !isLoading

    val foregroundColor: Color = when (style) {
        MutterButtonStyle.Primary -> OnGold
        MutterButtonStyle.Secondary, MutterButtonStyle.Ghost -> Ink
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(scale)
            .clip(shape)
            .then(
                when (style) {
                    MutterButtonStyle.Primary -> Modifier.background(GoldGradientBrush)
                    MutterButtonStyle.Secondary -> Modifier.background(GoldSoft)
                    MutterButtonStyle.Ghost -> Modifier
                },
            )
            .clickable(
                enabled = active,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(
            text = text,
            isLoading = isLoading,
            leadingIcon = leadingIcon,
            foregroundColor = foregroundColor,
        )
    }
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    leadingIcon: ImageVector?,
    foregroundColor: Color,
) {
    Box(contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null && !isLoading) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = foregroundColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MutterText.bodyLargeBold,
                color = if (isLoading) Color.Transparent else foregroundColor,
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = foregroundColor,
                strokeWidth = 2.dp,
            )
        }
    }
}
