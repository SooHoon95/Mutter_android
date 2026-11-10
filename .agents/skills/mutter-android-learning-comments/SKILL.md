---
name: mutter-android-learning-comments
description: Use when introducing unfamiliar Android APIs, SDKs, or patterns into the codebase (Media3 ExoPlayer, MediaSession, WebView + JavascriptInterface bridge, AndroidView interop, Credential Manager, Hilt scopes, Flow operators 등). Trigger on user phrases like "처음 써봐", "주석 달아줘", "흐름 따라가게", "이해 안 가", "공부 중", "학습용". Overrides the default "minimize comments" rule for THAT specific block of code.
---

# 학습용 주석 (Android)

사용자가 처음 보는 API·패턴을 도입할 때, 코드를 읽으며 흐름을 따라갈 수 있게 **풍부한 한국어 주석**을 단다. 일반 코드는 `mutter-android-conventions`의 "왜만 쓴다" 규칙 그대로이고, 이 스킬이 트리거된 블록만 예외다.

## 적용 / 미적용

적용: 처음 도입하는 SDK(Media3·MediaSession·Credential Manager·Kakao SDK), Compose 고급 API(`AndroidView`, `CompositionLocal`, `SubcomposeLayout`, `snapshotFlow`), 처음 쓰는 Flow/코루틴 패턴(`callbackFlow`, `shareIn`, `SupervisorJob`), 순서 의존 시스템 API(WebView `addJavascriptInterface` 후 `loadDataWithBaseURL`), 사용자가 "주석/흐름/이해" 어휘를 쓴 코드.

미적용: 익숙한 패턴(`StateFlow` UiState, `hiltViewModel()`), 짧은 helper, 단순 수정·리팩토링, dead code 보존.

## 형식

1. **파일 상단 헤더 박스** — 무엇/왜 1~3줄 + 전체 흐름 ASCII.

```kotlin
// =============================================================================
// SoundCloudSource
//
// 숨김 WebView에 SoundCloud Widget을 띄워 소리만 재생한다.
// Compose에는 WebView가 없어 AndroidView로 트리에 올리고, 재생 이벤트는 JS→Kotlin 브리지로 받는다.
//
// 흐름:  Attachment()  ── 1dp WebView를 트리에 붙임(JS 실행 조건)
//          │
//          ▼  위젯 READY 수신 ── wantsPlay면 그때 play()
// =============================================================================
```

2. **단계 번호** — 호출 순서가 결과를 바꾸는 설정은 `── ① ── ② ──`.

```kotlin
// ── ① JS 브리지를 HTML 로드 "전에" 붙인다 — 뒤에 붙이면 첫 READY 이벤트를 놓친다 ──
addJavascriptInterface(ScBridge(), "Android")
// ── ② 그다음 위젯 HTML 로드 ──
webView.loadDataWithBaseURL(baseUrl, widgetHtml, "text/html", "utf-8", null)
```

3. **핵심 줄은 "안 하면 무슨 일"까지** — `// ⚠ mediaPlaybackRequiresUserGesture=false 안 하면 자동재생이 막혀 무음.`
4. **콜백은 누가/언제 호출하는지** — `/** 위젯이 postMessage할 때 WebView JS 스레드에서 호출된다(메인 스레드 아님 → UI 갱신은 post). */`
5. **타입에는 한 문장 요약.**

## 분량

짧은 줄 주석은 코드 한 줄당 1~2줄. 헤더는 6줄 안에 핵심. 주석이 코드보다 길어도 괜찮지만 자명한 줄엔 달지 않는다. 사용자가 익숙해져 "주석 줄여줘"라고 하면 정리 패스를 따로 한다.

## 이 프로젝트의 "낯선 API" 지점

- `:core:audiosync` `SoundCloudSource` — 숨김 WebView + JS 브리지(순서 의존·누수 주의), 재생 의도(`wantsPlay`) 보류.
- `:core:audiosync` `HostedAudioSource` — Media3 ExoPlayer 수명·오디오 포커스.
- `:app` OAuth 콜백(`handleOAuthCallback`) — supabase-kt `handleDeeplinks` 대신 쓰는 이유.
