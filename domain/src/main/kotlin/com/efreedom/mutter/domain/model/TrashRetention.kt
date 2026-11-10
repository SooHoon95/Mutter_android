package com.efreedom.mutter.domain.model

import java.time.Duration
import java.time.Instant

/** 삭제함(보낸 편지·받은 편지 공통) 보관 기간 계산. 보관 기간 자체(30일)는 서버가 purge_at으로 내려 준다. */
object TrashRetention {
    /**
     * [now] 기준 [purgeAt]까지 남은 일수(올림). 0 이하면 0 — 화면은 0을 "오늘 완전히 삭제"로 보인다.
     * 올림이라 1초만 남아도 1일이다(세 플랫폼 공통 계약: N = 남은 일수 올림, 최소 1).
     */
    fun daysUntilPurge(purgeAt: Instant, now: Instant): Int {
        val remaining = Duration.between(now, purgeAt)
        if (remaining.isNegative || remaining.isZero) return 0
        val days = remaining.toDays()
        return (if (remaining > Duration.ofDays(days)) days + 1 else days).toInt()
    }
}
