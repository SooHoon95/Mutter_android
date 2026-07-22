package com.efreedom.mutter

import android.net.Uri

/**
 * 외부 링크 → 앱 내부 목적지 해석 (iOS `AppFoundation/Deeplink` 대응).
 *
 * 지원 형태:
 *  - Universal/App Link: `https://<host>/l/<token>`, `https://<host>/connect/<token>[?from=app|web]`
 *  - 커스텀 스킴:        `mutter://l/<token>`, `mutter://connect/<token>`
 *
 * 경로 첫 컴포넌트(키워드) + 두 번째(토큰)만 본다. 나머지 쿼리(`from` 등)는 무시.
 */
sealed interface Deeplink {
    /** 편지 열람 링크 — 비로그인이어도 즉시 뷰어를 연다(get_letter_by_token은 anon 허용). */
    data class Letter(val token: String) : Deeplink

    /** 연결 초대 링크 — 수락은 로그인 사용자만 가능하므로 로그인 후 소비한다. */
    data class Connect(val token: String) : Deeplink

    companion object {
        fun parse(uri: Uri?): Deeplink? {
            uri ?: return null
            return when (uri.scheme?.lowercase()) {
                "https", "http" -> {
                    // https://<host>/l/<token> → pathSegments = [l, token]
                    val segments = uri.pathSegments.filter { it.isNotBlank() }
                    fromKeyword(segments.getOrNull(0), segments.getOrNull(1))
                }
                "mutter" -> {
                    // mutter://l/<token> → host = "l", pathSegments = [token]
                    val segments = uri.pathSegments.filter { it.isNotBlank() }
                    fromKeyword(uri.host, segments.getOrNull(0))
                }
                else -> null
            }
        }

        private fun fromKeyword(keyword: String?, token: String?): Deeplink? {
            if (token.isNullOrBlank()) return null
            return when (keyword?.lowercase()) {
                "l" -> Letter(token)
                "connect" -> Connect(token)
                else -> null
            }
        }
    }
}
