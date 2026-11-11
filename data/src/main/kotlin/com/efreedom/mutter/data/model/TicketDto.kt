package com.efreedom.mutter.data.model

import com.efreedom.mutter.data.support.Timestamps
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.TicketHistoryItem
import com.efreedom.mutter.domain.model.TicketReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** get_my_entitlements 반환(json 단일 객체). */
@Serializable
data class EntitlementsDto(
    @SerialName("is_admin") val isAdmin: Boolean = false,
    val tickets: Int = 0,
    @SerialName("paid_enabled") val paidEnabled: Boolean = false,
    @SerialName("free_days") val freeDays: Int,
    @SerialName("grace_days") val graceDays: Int,
) {
    fun toDomain(): Entitlements = Entitlements(
        isAdmin = isAdmin,
        tickets = tickets,
        paidEnabled = paidEnabled,
        freeDays = freeDays,
        graceDays = graceDays,
    )
}

/** get_my_ticket_history 한 행. */
@Serializable
data class TicketHistoryRow(
    @SerialName("created_at") val createdAt: String,
    val delta: Int,
    val reason: String? = null,
    @SerialName("letter_title") val letterTitle: String? = null,
) {
    fun toDomain(): TicketHistoryItem = TicketHistoryItem(
        createdAt = Timestamps.parse(createdAt) ?: Instant.EPOCH,
        delta = delta,
        reason = TicketReason.from(reason),
        letterTitle = letterTitle,
    )
}

/**
 * verify-purchase 요청 본문(Android). 세 키 모두 값이 있어 기본값을 두지 않는다
 * — platform에 기본값을 주면 encodeDefaults=false가 키를 빼 버린다.
 */
@Serializable
internal data class VerifyPurchaseRequest(
    val platform: String,
    val productId: String,
    val purchaseToken: String,
)

/** verify-purchase `200 {"granted": bool, "tickets": n}`. granted=false(이미 지급)도 성공이다. */
@Serializable
internal data class VerifyPurchaseResponse(
    val granted: Boolean = false,
    val tickets: Int = 0,
)
