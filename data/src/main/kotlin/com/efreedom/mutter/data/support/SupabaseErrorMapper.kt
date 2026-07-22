package com.efreedom.mutter.data.support

import com.efreedom.mutter.domain.SessionInvalidation
import com.efreedom.mutter.domain.error.MutterError
import java.io.IOException
import java.time.Instant

/**
 * Supabase/네트워크 예외를 도메인 MutterError로 정규화(iOS SupabaseErrorMapper 이식).
 * 서버 RPC가 raise하는 코드 문자열(웹/iOS와 동일 계약)을 의미 케이스로 매핑. 순서 = 첫 매치 우선.
 * 세션 만료를 감지하면 [SessionInvalidation]으로 앱 전역 로그아웃 신호를 쏜다(iOS와 동일).
 */
object SupabaseErrorMapper {
    fun map(error: Throwable): MutterError {
        if (error is MutterError) return error
        // 연결 실패(오프라인/타임아웃/DNS)는 ktor/OkHttp가 IOException 계열로 던진다 → Network로 매핑.
        if (isNetworkError(error)) return MutterError.Network
        val message = error.message ?: ""
        val lower = message.lowercase()

        if (isSessionExpired(lower)) {
            // iOS SupabaseErrorMapper.isSessionExpired → SessionInvalidation.notifyUnauthorized() 대응.
            SessionInvalidation.notifyUnauthorized()
            return MutterError.Unauthorized
        }
        parseNotYetRevealed(message)?.let { return MutterError.LinkNotYetRevealed(it) }

        return when {
            message.contains("WRONG_PASSWORD") -> MutterError.WrongPassword
            message.contains("LINK_REVOKED") -> MutterError.LinkRevoked
            message.contains("LINK_EXPIRED") -> MutterError.LinkExpired
            message.contains("NOT_CONNECTED") -> MutterError.NotConnected
            message.contains("INVITE_ALREADY_USED") -> MutterError.InviteAlreadyUsed
            message.contains("TOKEN_NOT_FOUND") ||
                message.contains("LETTER_NOT_FOUND") ||
                message.contains("NOT_FOUND") -> MutterError.NotFound
            message.contains("FORBIDDEN") || message.contains("not_authorized") -> MutterError.Unauthorized
            else -> MutterError.Unknown
        }
    }

    private fun isSessionExpired(lower: String): Boolean =
        SESSION_EXPIRED_MARKERS.any { lower.contains(it) }

    /** 원인 체인에 IOException(연결 실패/타임아웃/UnknownHost 등)이 있으면 네트워크 오류로 본다. */
    private fun isNetworkError(error: Throwable): Boolean {
        var e: Throwable? = error
        while (e != null) {
            if (e is IOException) return true
            e = e.cause
        }
        return false
    }

    private fun parseNotYetRevealed(message: String): Instant? {
        val marker = "NOT_YET_REVEALED:"
        val idx = message.indexOf(marker)
        if (idx < 0) return null
        val iso = message.substring(idx + marker.length).trim().takeWhile { !it.isWhitespace() }
        return runCatching { Instant.parse(iso) }.getOrNull()
    }

    private val SESSION_EXPIRED_MARKERS = listOf(
        "jwt expired", "jwt is expired", "token is expired", "token has expired",
        "invalid jwt", "jwt is invalid", "bad_jwt",
        "refresh token", "refresh_token", "session_not_found", "session not found",
    )
}
