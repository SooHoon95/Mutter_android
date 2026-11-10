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

/**
 * 받은 편지 삭제함 한 통 — get_my_deleted_inbox RPC row. 삭제 뒤 30일([purgeAt])이 지나면 받은 편지함에서만 지워진다
 * (보낸 사람의 편지는 그대로). 보낸 사람이 편지 자체를 지운 경우는 서버가 목록에서 뺀다.
 */
data class DeletedInboxItem(
    val letterId: String,
    val token: String,
    val title: String,
    val senderId: String?,
    val senderNickname: String?,
    val deletedAt: Instant,
    val purgeAt: Instant,
) {
    /** [now] 기준 완전 삭제까지 남은 일수 — [TrashRetention.daysUntilPurge]. */
    fun daysUntilPurge(now: Instant): Int = TrashRetention.daysUntilPurge(purgeAt, now)
}
