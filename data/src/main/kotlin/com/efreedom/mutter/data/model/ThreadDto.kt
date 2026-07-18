package com.efreedom.mutter.data.model

import com.efreedom.mutter.domain.model.Counterpart
import com.efreedom.mutter.domain.model.LetterDirection
import com.efreedom.mutter.domain.model.SentLetterSummary
import com.efreedom.mutter.domain.model.ThreadLetter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** get_counterparts RPC 반환 row. */
@Serializable
data class CounterpartRow(
    @SerialName("counterpart_id") val counterpartId: String,
    val nickname: String? = null,
    @SerialName("letter_count") val letterCount: Int,
) {
    fun toDomain(): Counterpart = Counterpart(
        userId = counterpartId,
        nickname = nickname,
        exchangeCount = letterCount,
    )
}

/** get_thread RPC 파라미터. */
@Serializable
data class ThreadParams(
    @SerialName("p_counterpart") val counterpart: String,
)

/**
 * get_thread RPC 반환 row.
 * 서버 결함 대비 모든 필드 nullable+기본값 — 한 row가 깨져도 전체가 터지지 않는다.
 */
@Serializable
data class ThreadLetterRow(
    @SerialName("letter_id") val letterId: String? = null,
    val token: String? = null,
    val title: String? = null,
    val direction: String? = null,
    val at: String? = null,
) {
    fun toDomain(): ThreadLetter = ThreadLetter(
        letterId = letterId ?: "",
        direction = when (direction) {
            "received" -> LetterDirection.RECEIVED
            else -> LetterDirection.SENT
        },
        token = token,
        title = title ?: "",
        sentAt = at?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now(),
    )
}

/** get_my_sent_with_recipients RPC 반환 row. */
@Serializable
data class SentWithRecipientRow(
    @SerialName("letter_id") val letterId: String,
    val title: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("recipient_id") val recipientId: String? = null,
    @SerialName("recipient_nickname") val recipientNickname: String? = null,
) {
    fun toDomain(): SentLetterSummary = SentLetterSummary(
        letterId = letterId,
        title = title,
        sentAt = Instant.parse(createdAt),
        recipientId = recipientId,
        recipientNickname = recipientNickname,
    )
}
