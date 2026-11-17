package com.efreedom.mutter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterWordmark
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface

/**
 * 강제 업데이트 차단 오버레이 — iOS ForceUpdateView 대응.
 * 딤 배경 + 안내 카드(로고·제목·안내) + 스토어 이동 버튼. 뒤 화면의 터치(스크림이 소비)와 뒤로가기(BackHandler로 소비)를 모두 막아 우회할 수 없다.
 */
@Composable
fun ForceUpdateOverlay() {
    val context = LocalContext.current
    BackHandler(enabled = true) { /* 우회 차단 — 아무것도 하지 않음 */ }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.6f))
            // 스크림이 포인터 입력을 소비 — 아래 앱(탭·버튼)으로 탭이 새지 않게(iOS는 카드 뒤 상호작용 전부 차단).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(horizontal = 32.dp)
                .background(Surface, RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 경고 아이콘 대신 앱 로고(iOS onboardingLogo — 태그라인 포함, 높이 64pt에 가깝게).
            MutterWordmark(markSize = 32.dp, wordmarkSize = 20)
            Text(
                text = stringResource(R.string.force_update_title),
                style = MutterText.title,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.force_update_detail),
                style = MutterText.bodyMedium,
                color = InkSoft,
                textAlign = TextAlign.Center,
            )
            MutterButton(text = stringResource(R.string.force_update_button), onClick = { openStore(context) })
        }
    }
}

private fun openStore(context: Context) {
    val pkg = context.packageName
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Play 앱이 없으면 웹으로 폴백.
    runCatching { context.startActivity(market) }
        .onFailure { runCatching { context.startActivity(web) } }
}
