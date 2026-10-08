package com.efreedom.mutter.feature.compose

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.efreedom.mutter.domain.SoundCloudTrackPage
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.Hairline
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText

/** discover는 플레이리스트 위주라 곡을 바로 고르기 어렵다 — 검색 화면에서 시작한다. */
private const val START_URL = "https://m.soundcloud.com/search"

/**
 * 앱 안에서 soundcloud.com을 둘러보다가 트랙 페이지에서 "이 곡 넣기"로 바로 고르는 전체 화면 시트.
 * iOS SoundCloudPickerSheet 이식.
 *
 * 정책 준수(Google Play 웹뷰·지식재산권 정책, SoundCloud 약관 스크래핑·고지 변경 금지):
 * 페이지에 JS/CSS를 주입하지 않고 DOM을 읽지 않으며, 오디오를 가로채거나 내려받지 않는다.
 * 지금 보고 있는 URL 하나만 읽는다. 제목·작성자는 기존 oEmbed 검증이 채운다.
 *
 * ModalBottomSheet 대신 전체 화면 Dialog — 시트의 드래그 제스처가 WebView 세로 스크롤과 겹치고,
 * 뒤로가기를 WebView 히스토리로 먼저 돌려야 해서 BackHandler가 확실히 받는 Dialog 창이 맞다.
 */
@Composable
internal fun SoundCloudPickerSheet(
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 뒤로가기는 아래 BackHandler가 WebView 히스토리 → 닫기 순으로 처리한다.
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        PickerContent(onPick = onPick, onClose = onClose)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PickerContent(
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var trackUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            // SoundCloud 모바일은 SPA라 JS·DOM 저장소가 없으면 화면이 뜨지 않는다. 페이지 자체 JS만 실행한다.
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                // 곡 클릭은 history.pushState라 shouldOverrideUrlLoading/onPageStarted가 오지 않는다.
                // doUpdateVisitedHistory는 같은 문서 내 이동(pushState)까지 커밋될 때마다 불린다 — iOS URL KVO 대응.
                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    trackUrl = SoundCloudTrackPage.trackUrl(url)
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    isLoading = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    isLoading = false
                    trackUrl = SoundCloudTrackPage.trackUrl(url)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    return when (navigationPolicy(request.url)) {
                        NavigationPolicy.ALLOW -> false
                        NavigationPolicy.CANCEL -> true
                        NavigationPolicy.OPEN_EXTERNALLY -> {
                            try {
                                view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                            } catch (_: ActivityNotFoundException) {
                                // 열 앱이 없으면 조용히 무시 — 시트는 그대로 둔다.
                            }
                            true
                        }
                    }
                }
            }
            loadUrl(START_URL)
        }
    }

    // 시트가 닫히면 WebView를 파기해 SoundCloud 페이지 안에서 재생 중이던 오디오까지 멈춘다.
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                MutterIcon(id = DsR.drawable.ic_close, contentDescription = "닫기", tint = Ink)
            }
            Text(
                text = stringResource(R.string.compose_soundcloud_picker_title),
                style = MutterText.bodyMediumBold,
                color = Ink,
            )
        }
        HorizontalDivider(color = Hairline)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (isLoading) {
                CircularProgressIndicator(
                    color = Gold,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .size(24.dp),
                )
            }
        }

        HorizontalDivider(color = Hairline)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    if (trackUrl == null) R.string.compose_soundcloud_picker_hint
                    else R.string.compose_soundcloud_picker_ready,
                ),
                style = MutterText.caption,
                color = InkFaint,
            )
            MutterButton(
                text = stringResource(R.string.compose_soundcloud_pick),
                onClick = { trackUrl?.let(onPick) },
                enabled = trackUrl != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private enum class NavigationPolicy { ALLOW, CANCEL, OPEN_EXTERNALLY }

/**
 * SoundCloud 안에서는 자유롭게 탐색하고, 바깥 웹 링크는 외부 브라우저로 넘긴다.
 * 앱 스킴·스토어 유도(soundcloud://, intent://, market:// 등)는 시트를 벗어나지 않도록 막는다.
 */
private fun navigationPolicy(url: Uri): NavigationPolicy {
    val scheme = url.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") {
        return if (scheme == "about") NavigationPolicy.ALLOW else NavigationPolicy.CANCEL
    }
    val host = url.host?.lowercase().orEmpty()
    val isSoundCloud = host == "soundcloud.com" || host.endsWith(".soundcloud.com") ||
        host.endsWith(".sndcdn.com")
    return if (isSoundCloud) NavigationPolicy.ALLOW else NavigationPolicy.OPEN_EXTERNALLY
}
