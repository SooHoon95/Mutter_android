package com.efreedom.mutter.core.audiosync

import androidx.compose.runtime.Composable

/**
 * 단일 음원 재생 추상화. 싱크 엔진/플레이어는 소스 타입(SoundCloud/호스팅)을 모른다.
 * 구현체: HostedAudioSource(ExoPlayer), SoundCloudSource(WebView + SC Widget API).
 *
 * iOS TrackSource 프로토콜 이식.
 */
interface TrackSource {
    /** 재생 준비(네트워크/위젯 초기화). 완료 후 play 가능. 실패 시 throw(폴백 트리거). */
    suspend fun load()

    fun play()

    fun pause()

    /** 지정 위치로 이동(ms). */
    fun seek(ms: Int)

    /** 볼륨 0.0..1.0(페이드용). */
    fun setVolume(v: Double)

    /** 재생 종료 콜백. */
    var onFinish: (() -> Unit)?

    /**
     * 재생을 시작했어야 하는데 소리가 나지 못한 경우(위젯 차단·오류 등) 통지.
     * 플레이어가 이 신호로 음악 없음 처리를 한다.
     */
    var onPlaybackStalled: (() -> Unit)?

    /**
     * 이 소스가 동작하려면 Compose 트리에 있어야 하는 숨김 뷰(SoundCloud=WebView).
     * ExoPlayer 기반 소스는 no-op. 수신 화면이 소스 타입을 모른 채 이 컴포저블만 배치한다.
     * iOS attachmentView(AnyView?) 대응.
     */
    @Composable
    fun Attachment()
}
