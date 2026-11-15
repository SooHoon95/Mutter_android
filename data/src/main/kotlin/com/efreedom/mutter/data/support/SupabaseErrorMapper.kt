package com.efreedom.mutter.data.support

import com.efreedom.mutter.domain.SessionInvalidation
import com.efreedom.mutter.domain.error.MutterError
import io.github.jan.supabase.auth.exception.AuthRestException
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
        mapAuthError(error)?.let { return it }
        parseNotYetRevealed(message)?.let { return MutterError.LinkNotYetRevealed(it) }
        // 영구 편지권 계약 — `LETTER_LOCKED:<ISO8601>`은 삭제 예정 시각을 싣는다. NOT_FOUND 계열보다 먼저 본다.
        if (message.contains(LETTER_LOCKED)) return MutterError.LetterLocked(parseLetterLocked(message))

        return when {
            message.contains("LOGIN_REQUIRED") -> MutterError.LoginRequired
            message.contains("LETTER_GONE") -> MutterError.LetterGone
            message.contains("NO_TICKET") -> MutterError.NoTicket
            message.contains("WRONG_PASSWORD") -> MutterError.WrongPassword
            message.contains("LINK_REVOKED") -> MutterError.LinkRevoked
            message.contains("LINK_EXPIRED") -> MutterError.LinkExpired
            message.contains("NOT_CONNECTED") -> MutterError.NotConnected
            message.contains("INVITE_ALREADY_USED") -> MutterError.InviteAlreadyUsed
            message.contains("TOKEN_NOT_FOUND") ||
                message.contains("LETTER_NOT_FOUND") ||
                message.contains("NOT_FOUND") -> MutterError.NotFound
            message.contains("FORBIDDEN") ||
                message.contains("not_authorized") ||
                message.contains("AUTH_REQUIRED") -> MutterError.Unauthorized
            else -> MutterError.Unknown
        }
    }

    /**
     * Supabase Auth 오류 — 메시지 문자열 대신 구조화된 `error_code`로 판정한다(세 플랫폼 공통 문구).
     * errorCode가 enum에 없으면 원문 `error` 필드로 폴백.
     */
    private fun mapAuthError(error: Throwable): MutterError? {
        val auth = error as? AuthRestException ?: return null
        return when (auth.errorCode?.value ?: auth.error) {
            "invalid_credentials" -> MutterError.InvalidCredentials
            "email_not_confirmed" -> MutterError.EmailNotConfirmed
            "weak_password" -> MutterError.WeakPassword
            "same_password" -> MutterError.SamePassword
            "reauthentication_needed", "reauthentication_not_valid" -> MutterError.ReauthenticationNeeded
            "over_email_send_rate_limit" -> MutterError.EmailSendRateLimited
            "over_request_rate_limit" -> MutterError.RateLimited
            else -> null
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

    private const val LETTER_LOCKED = "LETTER_LOCKED:"

    /** 오류 메시지가 JSON에 감싸여 올 수 있어(`"LETTER_LOCKED:…"`) 시각 문자만 떼어 낸다. */
    private fun parseLetterLocked(message: String): Instant? {
        val idx = message.indexOf(LETTER_LOCKED)
        val iso = message.substring(idx + LETTER_LOCKED.length).trim()
            .takeWhile { it.isDigit() || it in "-:T.+Z" }
        return Timestamps.parse(iso)
    }

    private val SESSION_EXPIRED_MARKERS = listOf(
        "jwt expired", "jwt is expired", "token is expired", "token has expired",
        "invalid jwt", "jwt is invalid", "bad_jwt",
        "refresh token", "refresh_token", "session_not_found", "session not found",
    )
}
