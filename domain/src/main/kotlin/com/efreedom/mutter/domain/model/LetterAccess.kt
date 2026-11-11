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

/** get_letter_access_states 한 행. [accessUntil]은 잠기는(잠긴) 시각, [deleteAt]은 삭제 예정 시각. */
data class LetterAccessState(
    val letterId: String,
    val kind: LetterAccessKind,
    val accessUntil: Instant?,
    val deleteAt: Instant?,
) {
    /** [now] 기준 잠기기까지 남은 일수(올림). 아직 아무도 안 열었거나 영구면 null. */
    fun daysLeft(now: Instant): Int? =
        if (kind == LetterAccessKind.OPEN) accessUntil?.let { LetterAccessDays.until(it, now) } else null
}

/**
 * 봉투 — get_letter_cover. 본문 없음. [sealed]면 이유를 구분할 수 없는 상태라 나머지 값이 비어 있다.
 * [loginRequired]는 "이 편지가 로그인을 요구하는가"이며 호출자의 로그인 여부와 무관하다.
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
)

/** 내 편지를 연 사람 — get_letter_readers 한 행(처음 연 순). */
data class LetterReader(
    val userId: String,
    val nickname: String?,
    val firstOpenedAt: Instant,
    val lastOpenedAt: Instant,
    val openCount: Int,
)

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
