package com.efreedom.mutter.core.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Mutter 시그니처 그라데이션: goldLight → blushPink → blushLavender (topStart→bottomEnd).
 * iOS LinearGradient의 topLeading→bottomTrailing와 동일 방향.
 */
private val GoldGradientBrush = Brush.linearGradient(
    colors = listOf(GoldLight, BlushPink, BlushLavender),
)

// ─── Composable ──────────────────────────────────────────────────────────────

/**
 * Mutter 기본 버튼 (iOS `MutterButton` 1:1 이식).
 *
 * @param text 버튼 레이블.
 * @param onClick 탭 액션.
 * @param modifier Modifier.
 * @param style 버튼 스타일. 기본값 [MutterButtonStyle.Primary].
 * @param enabled 활성화 여부. false이면 opacity 0.5.
 * @param isLoading 로딩 중이면 텍스트를 숨기고 [CircularProgressIndicator]를 표시.
 * @param leadingIcon 텍스트 왼쪽에 표시할 아이콘(선택).
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

    val foregroundColor: Color = when (style) {
        MutterButtonStyle.Primary -> OnGold
        MutterButtonStyle.Secondary, MutterButtonStyle.Ghost -> Ink
    }

    val shape = RoundedCornerShape(12.dp) // MutterRadius.lg

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(scale),
        enabled = enabled && !isLoading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = when (style) {
                MutterButtonStyle.Primary -> Color.Transparent // gradient via Modifier.background
                MutterButtonStyle.Secondary -> GoldSoft
                MutterButtonStyle.Ghost -> Color.Transparent
            },
            contentColor = foregroundColor,
            disabledContainerColor = when (style) {
                MutterButtonStyle.Primary -> Color.Transparent
                MutterButtonStyle.Secondary -> GoldSoft.copy(alpha = 0.5f)
                MutterButtonStyle.Ghost -> Color.Transparent
            },
            disabledContentColor = foregroundColor.copy(alpha = 0.5f),
        ),
        elevation = if (style == MutterButtonStyle.Primary && enabled) {
            ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
        } else {
            ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        },
        interactionSource = interactionSource,
        // Primary 스타일은 gradient를 직접 그린다
        content = {
            // Primary 그라데이션은 Button 내부 Box로 처리
            if (style == MutterButtonStyle.Primary) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(shape)
                        .background(brush = GoldGradientBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    ButtonContent(
                        text = text,
                        isLoading = isLoading,
                        leadingIcon = leadingIcon,
                        foregroundColor = foregroundColor,
                    )
                }
            } else {
                ButtonContent(
                    text = text,
                    isLoading = isLoading,
                    leadingIcon = leadingIcon,
                    foregroundColor = foregroundColor,
                )
            }
        },
    )
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    leadingIcon: ImageVector?,
    foregroundColor: Color,
) {
    Box(contentAlignment = Alignment.Center) {
        // 텍스트/아이콘 row — 로딩 중엔 투명하게
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (isLoading) Modifier.then(Modifier) else Modifier,
        ) {
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

        // 로딩 스피너
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = foregroundColor,
                strokeWidth = 2.dp,
            )
        }
    }
}
