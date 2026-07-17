package com.efreedom.mutter.domain.model

/** 편지 음악 큐. ref: soundcloud=트랙 URL / hosted=Storage 경로·트랙 id. */
enum class MusicSource(val raw: String) {
    SOUNDCLOUD("soundcloud"),
    HOSTED("hosted"),
}

data class MusicCue(
    val source: MusicSource,
    val ref: String,
    val startMs: Int? = null,
    val title: String? = null,
    val author: String? = null,
    val sourceUrl: String? = null,
)

/** 편지 = 테마 본문 1장 + 음악 1곡(nil=무음). */
data class Letter(
    val id: String,
    val title: String,
    val body: String,
    val templateId: String,
    val cue: MusicCue? = null,
)

/** 작성 입력(Compose). */
data class LetterDraft(
    val title: String,
    val body: String,
    val templateId: String,
    val cue: MusicCue? = null,
)

/** 수신자 표시용(발신자 신원 없음). */
data class LetterPayload(
    val id: String,
    val title: String,
    val body: String,
    val templateId: String,
    val cue: MusicCue?,
    val audioDisabled: Boolean,
)

/** 파생 read-model: 전달 링크 존재 OR 타인 inbox 존재 → isSent. */
data class LetterWithStatus(
    val letter: Letter,
    val isSent: Boolean,
)
