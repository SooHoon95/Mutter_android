package com.efreedom.mutter.domain.model

import java.time.Instant

/** 받은함에 보관한 편지 한 통. */
data class InboxItem(
    val letterId: String,
    val token: String,
    val title: String,
    val savedAt: Instant,
) {
    val id: String get() = token
}
