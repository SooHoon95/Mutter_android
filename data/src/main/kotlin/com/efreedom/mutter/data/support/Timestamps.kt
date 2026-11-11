package com.efreedom.mutter.data.support

import java.time.Instant
import java.time.OffsetDateTime

/**
 * 서버 timestamptz 문자열 → Instant. PostgREST JSON은 `+00:00` 오프셋을, RPC 예외 메시지는 `Z`를 쓸 수 있어
 * 둘 다 받는다(OffsetDateTime 파서는 둘 다 허용). 형식이 깨졌으면 null — 표시용 값 하나 때문에 목록이 깨지지 않게.
 */
internal object Timestamps {
    fun parse(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()
    }
}
