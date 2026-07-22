package com.efreedom.mutter.uicomponent.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.efreedom.mutter.uicomponent.theme.MutterText

/** SoundCloud 브랜드 오렌지(#FF5500). */
val SoundCloudOrange = Color(0xFFFF5500)

/**
 * SoundCloud 트랙 출처 표기 + 원곡 링크백. iOS `MusicPlayerBar`의 브랜딩/링크 대응.
 *
 * SoundCloud Widget 이용약관은 위젯으로 재생하는 화면에 (1) SoundCloud 브랜드 표기와
 * (2) 원곡 페이지로의 링크백을 요구한다. 재생 바에 이 컴포넌트를 노출해 준수한다.
 * 탭하면 [sourceUrl](원곡 트랙 페이지)을 외부 브라우저/앱으로 연다.
 */
@Composable
fun SoundCloudAttribution(
    sourceUrl: String,
    modifier: Modifier = Modifier,
    tint: Color = SoundCloudOrange,
) {
    val context = LocalContext.current
    Text(
        text = "SoundCloud ↗",
        style = MutterText.caption,
        color = tint,
        modifier = modifier.clickable {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    )
}
