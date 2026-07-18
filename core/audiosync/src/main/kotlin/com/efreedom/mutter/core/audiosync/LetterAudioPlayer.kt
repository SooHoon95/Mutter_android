package com.efreedom.mutter.core.audiosync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.efreedom.mutter.domain.model.MusicCue
import com.efreedom.mutter.domain.model.TrackSourceSpec
import com.efreedom.mutter.domain.usecase.AudioUseCase

/**
 * 편지 1통의 음악 1곡을 재생하는 플레이어(Compose 미리듣기·Viewer 수신 공유).
 * 게이트 언락(▶) 시 시작해 끝까지 재생한다(스크롤 동기 없음 — PRD 단일트랙).
 * 음원은 SoundCloud 단일 — 로드/재생 실패 시 폴백 없이 "음악 없음"으로 정리한다(무음 허용, 사용자 결정).
 *
 * Compose mutableStateOf를 사용해 isPlaying/isReady/isUnavailable/currentSource가
 * 변경될 때 관찰 중인 컴포저블이 자동 리컴포즈된다.
 *
 * iOS LetterAudioPlayer(@Observable) 이식.
 */
class LetterAudioPlayer(
    private val context: Context,
    private val audioUseCase: AudioUseCase,
) {
    var isPlaying by mutableStateOf(false)
        private set

    var isReady by mutableStateOf(false)
        private set

    /** 재생이 불가로 판명됨(로드 실패·위젯 무음). UI가 "음악을 재생할 수 없어요" 표시에 사용. */
    var isUnavailable by mutableStateOf(false)
        private set

    /**
     * 현재 소스. SoundCloud면 Attachment() 컴포저블을 수신 화면이 배치해야 한다.
     * iOS currentSource: TrackSource? 대응.
     */
    var currentSource: TrackSource? by mutableStateOf(null)
        private set

    /** 소스 생성 지점(테스트 주입용 심 — 프로덕션은 TrackSourceFactory 그대로). */
    var makeSource: (Context, TrackSourceSpec) -> TrackSource =
        { ctx, spec -> TrackSourceFactory.make(ctx, spec) }

    /**
     * 큐를 준비한다. 실패하면 음악 없음(isUnavailable) — 폴백 없음.
     * SoundCloud 소스는 currentSource 노출 후 load()가 WebView READY를 기다리므로
     * 호출자는 이 함수 이전에 Attachment()가 Compose 트리에 올라가도록 보장해야 한다.
     * 반드시 Dispatchers.Main에서 호출해야 한다(WebView 생성·조작 Main 필요).
     */
    suspend fun prepare(cue: MusicCue) {
        isUnavailable = false
        val spec = runCatching { audioUseCase.resolvePlayback(cue) }.getOrNull()
        if (spec == null) {
            markUnavailable()
            return
        }
        val source = makeSource(context, spec)
        source.onFinish = { isPlaying = false }
        // 재생 불발(READY 후 무음·위젯 ERROR) — 재생 중인 척하지 않도록 상태를 정리한다.
        // source 동일성 검사: 새 prepare()가 호출돼 다른 소스가 설정됐을 때 이전 콜백을 무시한다.
        source.onPlaybackStalled = callback@{
            if (currentSource !== source) return@callback
            markUnavailable()
        }
        // attachmentView 마운트를 위해 source를 먼저 노출 → 이어서 load 대기
        // (SC는 WebView가 Compose 트리에 있어야 READY 이벤트가 발생한다).
        currentSource = source
        isReady = false
        runCatching { source.load() }
            .onSuccess {
                isReady = true
                // 로드 대기 중에 이미 ▶로 재생을 시작했다면 재생 의도를 이어간다.
                if (isPlaying) source.play()
            }
            .onFailure { markUnavailable() }
    }

    /** 게이트 언락(▶) 시 호출 — 사용자 제스처 컨텍스트에서 재생 시작(iOS audio unlock 대응). */
    fun start() = play()

    fun play() {
        if (isUnavailable) return
        currentSource?.play()
        isPlaying = true
    }

    fun pause() {
        currentSource?.pause()
        isPlaying = false
    }

    fun toggle() {
        if (isPlaying) pause() else play()
    }

    fun setVolume(volume: Double) {
        currentSource?.setVolume(volume)
    }

    private fun markUnavailable() {
        currentSource = null
        isReady = false
        isPlaying = false
        isUnavailable = true
    }
}
