package com.efreedom.mutter.domain.model

import java.time.Instant

/** 전달 링크(암호·예약공개·revoke). token이 곧 식별자. */
data class DeliveryLink(
    val token: String,
    val letterId: String,
    val hasPassword: Boolean,
    val expiresAt: Instant? = null,
    val revealAt: Instant? = null,
    val revoked: Boolean = false,
) {
    val id: String get() = token
}

/** 발신자 열람 롤업(읽음확인). */
data class LetterOpenSummary(
    val letterId: String,
    val openCount: Int,
    val lastOpenedAt: Instant,
) {
    val id: String get() = letterId
}
