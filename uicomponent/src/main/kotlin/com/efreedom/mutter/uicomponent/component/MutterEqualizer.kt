package com.efreedom.mutter.uicomponent.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.theme.Gold

/**
 * 재생 중 "now playing"을 나타내는 애니메이션 이퀄라이저 바 — iOS `EqualizerView` 대응.
 *
 * 3dp 폭 캡슐 [barCount]개가 4~16dp 사이를 각자 다른 위상/속도로 오르내린다.
 * 재생 바에서 `isPlaying`일 때만 배치한다(정지 시엔 정적 아이콘으로 대체).
 */
@Composable
fun MutterEqualizer(
    modifier: Modifier = Modifier,
    barColor: Color = Gold,
    barCount: Int = 4,
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(barCount) { index ->
            // 막대마다 지속시간·시작 오프셋을 달리해 서로 어긋나게(위상차) 움직이도록 한다.
            val barHeight by transition.animateFloat(
                initialValue = 4f,
                targetValue = 16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420 + index * 130),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150),
                ),
                label = "bar$index",
            )
            Spacer(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor),
            )
        }
    }
}
