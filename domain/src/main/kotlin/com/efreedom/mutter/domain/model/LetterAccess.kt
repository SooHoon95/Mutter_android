package com.efreedom.mutter.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
 */
data class LetterAccessState(
    val letterId: String,
    val kind: LetterAccessKind,
    val accessUntil: Instant?,
    val deleteAt: Instant?,
) {
    /** [now] 기준 잠기기까지 남은 일수(달력, 최소 1 — 목록 문구엔 '오늘'이 없다). 아직 아무도 안 열었거나 영구면 null. */
    fun daysLeft(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Int? =
        if (kind == LetterAccessKind.OPEN) accessUntil?.let { maxOf(1, LetterAccessDays.until(it, now, zone)) } else null

    /** [now] 기준 삭제까지 남은 일수(달력, 0 = 오늘). 삭제 시각을 모르면 null. 계약 "삭제 안내". */
    fun daysUntilDelete(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Int? =
        deleteAt?.let { LetterAccessDays.until(it, now, zone) }
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

/** 보내기 전 선택 카드(영구 / {free_days}일) — 계약 "보내기 전 선택(보낸 사람)". */
object SendChoice {
    /**
     * 아직 영구가 아닌 열린 편지([LetterAccessKind.OPEN]) · 유료 전환([Entitlements.paidEnabled]) ·
     * 발급된 링크 없음 · 이 화면에서 아직 고르지 않음([hasChosen] — 영구든 무료 기간이든) — 모두 참일 때만 보인다.
     */
    fun shows(
        access: LetterAccessState?,
        entitlements: Entitlements?,
        hasLinks: Boolean,
        hasChosen: Boolean,
    ): Boolean = access?.kind == LetterAccessKind.OPEN && entitlements?.paidEnabled == true &&
        !hasLinks && !hasChosen
}

/** 남은 일수 계산. 화면은 0을 "오늘"로 보인다. */
object LetterAccessDays {
    /** 기기 자정 기준 달력 일수(같은 날 = 0, 지났으면 0). iOS `LetterAccessCountdown.days`. */
    fun until(at: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Int =
        ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), at.atZone(zone).toLocalDate())
            .coerceAtLeast(0).toInt()
}

/** 목록 상태 표시(계약 문구 `영구` / `{n}일 남음` / 자물쇠 + `{n}일 뒤 삭제`). 문구는 화면이 리소스로 붙인다. */
sealed interface LetterAccessLabel {
    data object None : LetterAccessLabel
    data object Permanent : LetterAccessLabel
    data class DaysLeft(val days: Int) : LetterAccessLabel

    /**
     * 닫힌 편지. [daysUntilDelete]는 삭제까지 남은 일수(달력, 0 = "오늘 삭제").
     * null이면 삭제 시각을 모른다 — "닫힘"만 보인다.
     */
    data class Closed(val daysUntilDelete: Int?) : LetterAccessLabel

    companion object {
        /**
         * [state]가 없으면(서버 반영 전·조회 실패) 표시하지 않는다 — 기존 목록 그대로.
         * "영구"는 편지권 UI가 보이는 계정([showPermanent])에만 — 그 외 계정은 기존 편지가 전부 영구라 표시가 소음이 된다.
         * 아직 아무도 안 연 열린 편지(시각 없음)도 표시하지 않는다.
         */
        fun of(state: LetterAccessState?, showPermanent: Boolean, now: Instant): LetterAccessLabel = when (state?.kind) {
            null -> None
            LetterAccessKind.PERMANENT -> if (showPermanent) Permanent else None
            LetterAccessKind.OPEN -> state.daysLeft(now)?.let(::DaysLeft) ?: None
            // 닫힌 뒤 유예가 끝나면 삭제된다 — 목록에서 며칠 남았는지 함께 보인다(계약 "삭제 안내").
            LetterAccessKind.LOCKED -> Closed(state.daysUntilDelete(now))
            LetterAccessKind.GONE -> Closed(null)
        }
    }
}
