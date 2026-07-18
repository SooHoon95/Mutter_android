package com.efreedom.mutter.core.audiosync

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * 호스팅 오디오(CC0 폴백·Storage) 재생 — Media3 ExoPlayer 기반.
 * 뷰 계층이 불필요(음성 전용) — Attachment()는 no-op.
 *
 * iOS HostedAudioSource(AVPlayer) 이식.
 */
class HostedAudioSource(
    context: Context,
    private val url: String,
    private val startMs: Int = 0,
) : TrackSource {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    override var onFinish: (() -> Unit)? = null
    override var onPlaybackStalled: (() -> Unit)? = null

    /**
     * ExoPlayer에 미디어 아이템을 설정하고 prepare()를 호출한다.
     * prepare()는 비동기로 버퍼링을 시작하며 실제 재생은 play() 호출 시 시작된다.
     * iOS load()의 AVPlayerItem 생성·setActive 대응.
     */
    override suspend fun load() {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (startMs > 0) player.seekTo(startMs.toLong())
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onFinish?.invoke()
                }
            }
        })
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seek(ms: Int) {
        player.seekTo(ms.toLong())
    }

    override fun setVolume(v: Double) {
        player.volume = v.toFloat().coerceIn(0f, 1f)
    }

    /** ExoPlayer는 뷰 계층이 필요 없다 — no-op. iOS의 attachmentView=nil 대응. */
    @Composable
    override fun Attachment() = Unit

    /** 플레이어 자원을 반환한다. 화면 종료 시 호출할 것. */
    fun release() {
        player.release()
    }
}
