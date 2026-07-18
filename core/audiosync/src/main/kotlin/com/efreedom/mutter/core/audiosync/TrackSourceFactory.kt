package com.efreedom.mutter.core.audiosync

import android.content.Context
import com.efreedom.mutter.domain.model.TrackSourceSpec

/**
 * 재생 사양(TrackSourceSpec)에서 적절한 TrackSource 구현을 만든다.
 * 싱크 엔진은 이 팩토리만 거치고 구체 타입을 모른다.
 *
 * iOS TrackSourceFactory(enum) 이식.
 */
object TrackSourceFactory {
    fun make(context: Context, spec: TrackSourceSpec): TrackSource = when (spec) {
        is TrackSourceSpec.Hosted ->
            HostedAudioSource(context, spec.url, spec.startMs ?: 0)
        is TrackSourceSpec.SoundCloud ->
            SoundCloudSource(context, spec.trackUrl, spec.startMs ?: 0)
    }
}
