package com.efreedom.mutter.domain.model

import java.time.Instant

/** get_my_entitlements — 관리자 여부·편지권 잔액·유료 전환 여부·무료/유예 일수·닫힘 예고 일수. */
data class Entitlements(
    val isAdmin: Boolean,
    val tickets: Int,
    val paidEnabled: Boolean,
    val freeDays: Int,
    val graceDays: Int,
    /** 받은 사람에게 닫힘 예고를 보이는 기준(남은 기간 ≤ warnDays). */
    val warnDays: Int = DEFAULT_WARN_DAYS,
) {
    /** 편지권 관련 UI(프로필 항목·구매·"영구 편지로 만들기")를 보일지. 계약 "화면 노출 규칙". */
    val showsTicketUi: Boolean get() = paidEnabled || isAdmin

    /** 받은 사람의 "간직하기"(편지 끝·닫힘 예고·잠김 화면, 앱). 잔액이 있으면 유료 전환 전에도 보인다. 계약 "받은 사람의 간직하기". */
    val canKeepReceived: Boolean get() = tickets > 0 || showsTicketUi

    /** 구매 UI — 관리자는 차감 없이 영구가 되므로 구매 UI를 숨긴다. */
    val canPurchase: Boolean get() = paidEnabled && !isAdmin

    companion object {
        /** 계약 — warn_days 키가 없으면 2로 본다. */
        const val DEFAULT_WARN_DAYS = 2
    }
}

/** 편지권 내역 사유. 모르는 값은 [OTHER]. */
enum class TicketReason(val raw: String) {
    PURCHASE("purchase"),
    USE("use"),
    GRANT("grant"),
    REFUND("refund"),
    OTHER(""),
    ;

    companion object {
        fun from(raw: String?): TicketReason = entries.firstOrNull { it.raw == raw && it != OTHER } ?: OTHER
    }
}

/** get_my_ticket_history 한 행(최근 순). */
data class TicketHistoryItem(
    val createdAt: Instant,
    val delta: Int,
    val reason: TicketReason,
    val letterTitle: String?,
)

/** 스토어 상품(편지권 1장·5장). [price]는 스토어가 준 현지화 가격 문자열. */
data class TicketProduct(
    val id: String,
    val price: String,
) {
    companion object {
        const val ONE = "mutter.ticket.1"
        const val FIVE = "mutter.ticket.5"
        val ALL = listOf(ONE, FIVE)
    }
}

/** 스토어 거래 한 건(Play Billing의 구매 토큰). 서버 검증(verify-purchase) 200 뒤에만 소비한다. */
data class StorePurchase(
    val productId: String,
    val purchaseToken: String,
)

/** 구매 흐름 결과(스토어 단계). */
sealed interface StorePurchaseResult {
    data class Purchased(val purchase: StorePurchase) : StorePurchaseResult
    /** 결제 대기(현금 결제 등) — 완료되면 다음 실행 때 미소비 거래로 다시 보낸다. */
    data object Pending : StorePurchaseResult
    data object Cancelled : StorePurchaseResult
}
