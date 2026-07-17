package com.efreedom.mutter.domain.model

/** SoundCloud oEmbed 검증 결과. */
sealed interface ScValidation {
    data class Ok(val title: String, val author: String, val canonicalUrl: String) : ScValidation
    data class Fail(val reason: ScValidationFailReason) : ScValidation
}

enum class ScValidationFailReason {
    INVALID_URL, NETWORK, PRIVATE_TRACK, NOT_FOUND, EMBED_DISABLED
}

/** 재생 사양 — AudioSync 엔진이 소비(SoundCloud=WebView Widget / Hosted=Media3). */
sealed interface TrackSourceSpec {
    data class Hosted(val url: String, val startMs: Int?) : TrackSourceSpec
    data class SoundCloud(val trackUrl: String, val startMs: Int?) : TrackSourceSpec
}
