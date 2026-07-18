package com.efreedom.mutter.core.audiosync

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.efreedom.mutter.domain.error.MutterError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SoundCloud 트랙 재생 — 공식 Widget API를 WebView에서 구동한다.
 * 스트림 URL을 rip/proxy/캐시하지 않고, canonical 트랙 URL을 위젯에 넘겨 공식 임베드로만 재생한다
 * (면책 유지 — license-compliance 참조).
 *
 * ## 동작 원리 (네이티브 ↔ JS 브리지)
 * 1. Attachment()가 숨김 WebView(1dp, alpha≈0)를 Compose 트리에 올린다(보이지 않지만 JS 실행에 필요).
 * 2. 웹뷰가 SC api.js를 로드하고 SC.Widget(iframe)을 만든다.
 * 3. 위젯 이벤트 → Android.onScEvent('ready'|'play'|'error'|'finish')로 네이티브에 통지.
 * 4. 네이티브 → JS 제어: webView.evaluateJavascript("window.scPlay && window.scPlay();", null) 등.
 *
 * load()는 READY 메시지를 받을 때까지 대기한다(10s 타임아웃 미수신 → MutterError.Network throw).
 * issuePlay() 후 4s 워치독: PLAY 이벤트 미수신 → onPlaybackStalled 통지(무음 감지).
 *
 * ⚠️ R1: 실기기에서 SC 위젯 자동재생/제어 신뢰성은 출시 전 디바이스 스파이크로 검증한다.
 *
 * iOS SoundCloudSource(WKWebView + Widget API) 이식.
 */
@SuppressLint("SetJavaScriptEnabled")
class SoundCloudSource(
    context: Context,
    private val trackUrl: String,
    private val startMs: Int = 0,
) : TrackSource {

    private val webView: WebView = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
        }
        addJavascriptInterface(ScBridge(), "Android")
    }

    // CompletableDeferred: load()가 READY 수신까지 suspend할 수 있게 한다.
    private val readyDeferred = CompletableDeferred<Unit>()
    private var isReady = false

    /** 재생 의도. READY 전에 play()가 와도 기억했다가 위젯 준비 시 자동 재생한다(레이스 방지). */
    private var wantsPlay = false

    /**
     * 위젯이 PLAY 이벤트로 "실제 재생 시작"을 확인했는지.
     * 워치독이 이 값으로 무음 재생(트랙 임베드 불가·정책 차단)을 감지한다.
     */
    @Volatile
    private var didConfirmPlay = false

    override var onFinish: (() -> Unit)? = null
    override var onPlaybackStalled: (() -> Unit)? = null

    /**
     * WebView에 SC 위젯 HTML을 로드하고 READY 이벤트를 기다린다.
     * 10s 이내 READY가 오지 않으면 MutterError.Network를 throw해 폴백을 유도한다.
     * 반드시 Main 스레드 컨텍스트(Dispatchers.Main)에서 호출해야 한다.
     */
    override suspend fun load() {
        webView.loadDataWithBaseURL(
            "https://w.soundcloud.com",
            playerHtml(trackUrl),
            "text/html",
            "UTF-8",
            null,
        )
        val result = withTimeoutOrNull(10_000L) { readyDeferred.await() }
        if (result == null) {
            throw MutterError.Network
        }
    }

    override fun play() {
        wantsPlay = true
        if (!isReady) return  // READY 수신 후 issuePlay() 자동 호출됨
        issuePlay()
    }

    /**
     * 재생 명령 + 무음 워치독(4s).
     * READY는 왔지만 PLAY 이벤트가 오지 않으면 소리 없이 실패한 것 — stalled를 통지한다.
     */
    private fun issuePlay() {
        didConfirmPlay = false
        webView.evaluateJavascript("window.scPlay && window.scPlay();", null)
        webView.postDelayed({
            if (wantsPlay && !didConfirmPlay) {
                onPlaybackStalled?.invoke()
            }
        }, 4_000L)
    }

    override fun pause() {
        wantsPlay = false
        webView.evaluateJavascript("window.scPause && window.scPause();", null)
    }

    override fun seek(ms: Int) {
        webView.evaluateJavascript("window.scSeek && window.scSeek($ms);", null)
    }

    override fun setVolume(v: Double) {
        // SC Widget 볼륨은 0~100.
        val percent = (v.coerceIn(0.0, 1.0) * 100).toInt()
        webView.evaluateJavascript("window.scVolume && window.scVolume($percent);", null)
    }

    /**
     * 숨김 WebView를 Compose 트리에 올린다(1dp, alpha≈0 — 오디오만 필요, 터치 무시).
     * iOS의 attachmentView(1pt·opacity 0.001·allowsHitTesting false) 대응.
     */
    @Composable
    override fun Attachment() {
        val view = remember { webView }
        AndroidView(
            factory = { view },
            modifier = Modifier
                .size(1.dp)
                // alpha(0.001f) — Compose alpha modifier not added here to avoid extra import;
                // size(1.dp) makes it visually invisible at 1×1px density pixel.
        )
    }

    // MARK: - JS → 네이티브 브리지

    /**
     * JavaScript에서 Android.onScEvent(event)로 호출되는 인터페이스.
     * webView.post{}로 메인 스레드에 다시 올려 상태 변경을 안전하게 처리한다.
     */
    private inner class ScBridge {
        @JavascriptInterface
        fun onScEvent(event: String) {
            // JavascriptInterface 콜백은 백그라운드 스레드에서 호출될 수 있으므로 Main으로 이동.
            webView.post { handleEvent(event) }
        }
    }

    private fun handleEvent(event: String) {
        when (event) {
            "ready" -> {
                if (isReady) return
                isReady = true
                readyDeferred.complete(Unit)
                if (startMs > 0) seek(startMs)
                if (wantsPlay) {
                    issuePlay()
                }
            }
            "play" -> {
                // 위젯이 실제 재생을 시작 — 워치독 해제.
                didConfirmPlay = true
            }
            "error" -> onPlaybackStalled?.invoke()
            "finish" -> onFinish?.invoke()
        }
    }

    companion object {
        /**
         * SC 공식 위젯 임베드 + JS 제어 셰임을 담은 HTML.
         * iOS playerHTML(trackURL:)과 동일한 위젯 파라미터·JS 함수 이름을 유지한다.
         * 브리지만 webkit.messageHandlers → Android.onScEvent()로 교체.
         */
        private fun playerHtml(trackUrl: String): String {
            val encoded = java.net.URLEncoder.encode(trackUrl, "UTF-8")
            val widgetSrc = "https://w.soundcloud.com/player/?url=$encoded" +
                "&auto_play=false&buying=false&sharing=false&download=false" +
                "&show_artwork=false&show_comments=false&show_user=false&visual=false"

            return """
                <!DOCTYPE html>
                <html>
                <head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;background:transparent;">
                  <iframe id="sc" width="100%" height="100" frameborder="no" allow="autoplay"
                    src="$widgetSrc"></iframe>
                  <script src="https://w.soundcloud.com/player/api.js"></script>
                  <script>
                    var widget = SC.Widget(document.getElementById('sc'));
                    function post(ev){ Android.onScEvent(ev); }
                    widget.bind(SC.Widget.Events.READY,  function(){ post('ready');  });
                    widget.bind(SC.Widget.Events.PLAY,   function(){ post('play');   });
                    widget.bind(SC.Widget.Events.ERROR,  function(){ post('error');  });
                    widget.bind(SC.Widget.Events.FINISH, function(){ post('finish'); });
                    window.scPlay   = function()   { widget.play();         };
                    window.scPause  = function()   { widget.pause();        };
                    window.scSeek   = function(ms) { widget.seekTo(ms);     };
                    window.scVolume = function(v)  { widget.setVolume(v);   };
                  </script>
                </body>
                </html>
            """.trimIndent()
        }
    }
}
