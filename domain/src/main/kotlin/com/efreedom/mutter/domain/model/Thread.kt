package com.efreedom.mutter.domain.model

import java.time.Instant

/** 편지 방향 — 스레드에서 내가 보낸 것/받은 것. */
enum class LetterDirection(val raw: String) {
    SENT("sent"),
    RECEIVED("received"),
}

/** 보낸 편지 + 받은 사람 요약. */
data class SentLetterSummary(
    val letterId: String,
    val title: String,
    val sentAt: Instant,
    val recipientId: String?,
    val recipientNickname: String?,
) {
    val id: String get() = letterId
}

/** 스레드의 한 편지. received면 token으로 열람. */
data class ThreadLetter(
    val letterId: String,
    val direction: LetterDirection,
    val token: String?,
    val title: String,
    val sentAt: Instant,
) {
    val id: String get() = letterId
}
