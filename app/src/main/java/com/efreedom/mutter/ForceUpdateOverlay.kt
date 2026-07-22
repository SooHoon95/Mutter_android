package com.efreedom.mutter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface

/**
 * 강제 업데이트 차단 오버레이 — iOS ForceUpdateView 대응.
 * 딤 배경 + 안내 카드 + 스토어 이동 버튼. 뒤로가기로 우회할 수 없다(BackHandler로 소비).
 */
@Composable
fun ForceUpdateOverlay() {
    val context = LocalContext.current
    BackHandler(enabled = true) { /* 우회 차단 — 아무것도 하지 않음 */ }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.6f)),
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
            Text(
                text = "업데이트가 필요해요",
                style = MutterText.titleLarge,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "원활한 사용을 위해 최신 버전으로 업데이트해 주세요.",
                style = MutterText.bodyMedium,
                color = InkSoft,
                textAlign = TextAlign.Center,
            )
            MutterButton(text = "스토어로 이동", onClick = { openStore(context) })
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
