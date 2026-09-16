package com.efreedom.mutter.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * 전달 링크를 공유 시트로 보낼 때 링크 위에 붙는 동봉 문구.
 * iOS `UIComponent/Share/LetterShareMessage.swift` 대응. 세 플랫폼 공통 원문은
 * Mutter 저장소 `marketing/plans/2026-09-16-week1-loop-spec.md` §0-1이 정본이다.
 *
 * 발신자 1인칭이고 브랜드명을 넣지 않는다 — 브랜드는 링크 미리보기 카드(OG)가 맡는다.
 * URL은 메신저가 미리보기를 붙일 수 있게 마지막 줄에 단독으로 둔다.
 */
object LetterShareMessage {
    const val BODY: String = "노래 한 곡을 담아 편지를 썼어요.\n열면 음악이 함께 흘러요. 앱 없이 바로 열 수 있어요."
    const val PASSWORD_HINT: String = "암호는 따로 알려줄게요."

    /** "%s에 열려요." — [revealAt]이 미래일 때만 붙는다. */
    fun revealLine(formattedTime: String): String = "${formattedTime}에 열려요."

    /**
     * @param url 전달 링크 전체 URL.
     * @param hasPassword 암호 보호 링크면 안내 한 줄을 덧붙인다(암호 값은 절대 넣지 않음).
     * @param revealAt 예약공개 시각. [now] 이후일 때만 "…에 열려요." 한 줄을 덧붙인다.
     * @param now 테스트 주입용 현재 시각.
     * @param zone 표시 시간대(기본 기기 시간대).
     * @param locale 날짜 표기 로케일(기본 기기 로케일).
     */
    fun text(
        url: String,
        hasPassword: Boolean,
        revealAt: Instant?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val lines = mutableListOf(BODY)
        if (hasPassword) lines += PASSWORD_HINT
        if (revealAt != null && revealAt.isAfter(now)) {
            lines += revealLine(formatRevealAt(revealAt, zone, locale))
        }
        lines += ""
        lines += url
        return lines.joinToString("\n")
    }

    /** iOS `formatted(date: .abbreviated, time: .shortened)`에 대응하는 "월 일 + 시:분" 축약형. */
    fun formatRevealAt(revealAt: Instant, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
            .format(revealAt)
}
