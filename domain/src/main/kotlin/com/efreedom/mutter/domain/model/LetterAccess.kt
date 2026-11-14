package com.efreedom.mutter.domain.model

import java.time.Duration
import java.time.Instant

/**
 * 편지 접근 상태(영구 편지권 계약). 서버 문자열 `permanent`·`open`·`locked`·`gone`.
 * 기존 편지·영구 편지권·관리자 편지는 [PERMANENT]다.
 */
enum class LetterAccessKind(val raw: String) {
    PERMANENT("permanent"),
    OPEN("open"),
    LOCKED("locked"),
    GONE("gone"),
    ;

    companion object {
        /** 모르는 값은 기존 동작(제한 없음)으로 본다 — 새 상태가 생겨도 목록이 깨지지 않게. */
        fun from(raw: String?): LetterAccessKind = entries.firstOrNull { it.raw == raw } ?: PERMANENT
    }
}

/**
 * get_letter_access_states 한 행. [accessUntil]은 잠기는(잠긴) 시각, [deleteAt]은 삭제 예정 시각.
 * [keptByRecipient] — 받은 사람이 자기 편지권으로 영구로 만든 편지(보낸 사람에게만 true, 삭제 확인 경고용).
 * [keepRequestCount] — 간직 요청 수. 보낸 사람에게만, 아직 영구가 아닌 편지(open·locked)에만 0보다 크다.
 */
data class LetterAccessState(
    val letterId: String,
    val kind: LetterAccessKind,
    val accessUntil: Instant?,
    val deleteAt: Instant?,
    val keptByRecipient: Boolean = false,
    val keepRequestCount: Int = 0,
) {
    /** [now] 기준 잠기기까지 남은 일수(올림). 아직 아무도 안 열었거나 영구면 null. */
    fun daysLeft(now: Instant): Int? =
        if (kind == LetterAccessKind.OPEN) accessUntil?.let { LetterAccessDays.until(it, now) } else null
}

/**
 * 봉투 — get_letter_cover. 본문 없음. [sealed]면 이유를 구분할 수 없는 상태라 나머지 값이 비어 있다.
 * [loginRequired]는 "이 편지가 로그인을 요구하는가"이며 호출자의 로그인 여부와 무관하다.
 * [keepRequested]는 호출한 로그인 사용자가 이 편지에 간직 요청을 이미 보냈는지(익명이면 false).
 */
data class LetterCover(
    val sealed: Boolean,
    val templateId: String?,
    val title: String?,
    val senderNickname: String?,
    val kind: LetterAccessKind,
    val accessUntil: Instant?,
    val deleteAt: Instant?,
    val loginRequired: Boolean,
    val keepRequested: Boolean = false,
)

/** 내 편지를 연 사람 — get_letter_readers 한 행(처음 연 순). */
data class LetterReader(
    val userId: String,
    val nickname: String?,
    val firstOpenedAt: Instant,
    val lastOpenedAt: Instant,
    val openCount: Int,
)

/** 내 편지를 간직하고 싶어 하는 사람 — get_letter_keep_requests 한 행(요청한 순). */
data class LetterKeepRequest(
    val userId: String,
    val nickname: String?,
    val requestedAt: Instant,
)

/** 보내기 전 선택 카드(영구 / {free_days}일) — 계약 "보내기 전 선택(보낸 사람)". */
object SendChoice {
    /**
     * 아직 영구가 아닌 열린 편지([LetterAccessKind.OPEN]) · 유료 전환([Entitlements.paidEnabled]) ·
     * 발급된 링크 없음 · 이 화면에서 무료 기간을 고르지 않음 — 모두 참일 때만 보인다.
     */
    fun shows(
        access: LetterAccessState?,
        entitlements: Entitlements?,
        hasLinks: Boolean,
        choseFreeDays: Boolean,
    ): Boolean = access?.kind == LetterAccessKind.OPEN && entitlements?.paidEnabled == true &&
        !hasLinks && !choseFreeDays
}

/** 남은 일수 계산(올림). 0 이하면 0 — 화면은 0을 "오늘"로 보인다. */
object LetterAccessDays {
    fun until(at: Instant, now: Instant): Int {
        val remaining = Duration.between(now, at)
        if (remaining.isNegative || remaining.isZero) return 0
        val days = remaining.toDays()
        return (if (remaining > Duration.ofDays(days)) days + 1 else days).toInt()
    }

    /** 받은 사람에게 닫힘 예고를 보이는 기준(남은 기간 ≤ 3일). 계약 문구 표. */
    const val CLOSING_SOON_DAYS = 3
}

/** 목록 상태 표시(계약 문구 `영구` / `{n}일 남음` / `닫힘`). 문구는 화면이 리소스로 붙인다. */
sealed interface LetterAccessLabel {
    data object None : LetterAccessLabel
    data object Permanent : LetterAccessLabel
    data class DaysLeft(val days: Int) : LetterAccessLabel
    data object Closed : LetterAccessLabel

    companion object {
        /**
         * [state]가 없으면(서버 반영 전·조회 실패) 표시하지 않는다 — 기존 목록 그대로.
         * "영구"는 편지권 UI가 보이는 계정([showPermanent])에만 — 그 외 계정은 기존 편지가 전부 영구라 표시가 소음이 된다.
         * 아직 아무도 안 연 열린 편지(시각 없음)도 표시하지 않는다.
         */
        fun of(state: LetterAccessState?, showPermanent: Boolean, now: Instant): LetterAccessLabel = when (state?.kind) {
            null -> None
            LetterAccessKind.PERMANENT -> if (showPermanent) Permanent else None
            LetterAccessKind.OPEN -> when (val days = state.daysLeft(now)) {
                null -> None
                0 -> Closed
                else -> DaysLeft(days)
            }
            LetterAccessKind.LOCKED, LetterAccessKind.GONE -> Closed
        }
    }
}
