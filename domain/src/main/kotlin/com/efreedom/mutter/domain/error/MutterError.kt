package com.efreedom.mutter.domain.error

import java.time.Instant

/**
 * 앱 전역 에러 타입(iOS MutterError + MutterErrorDefine 통합 이식).
 * 순수 Kotlin — 도메인이 에러 계약을 소유한다. raw 에러→MutterError 변환은 :data(SupabaseErrorMapper)가 담당.
 */
sealed class MutterError(open val userMessage: String) : Exception() {
    data object Network : MutterError("네트워크 연결을 확인해 주세요.")
    data object Unauthorized : MutterError("로그인이 필요해요.")
    data object NotFound : MutterError("찾을 수 없어요.")
    data object RateLimited : MutterError("잠시 후 다시 시도해 주세요.")
    data object WrongPassword : MutterError("암호가 맞지 않아요.")
    data object LinkRevoked : MutterError("더 이상 열 수 없는 편지예요.")
    data object LinkExpired : MutterError("편지 링크가 만료됐어요.")
    data class LinkNotYetRevealed(val revealAt: Instant) : MutterError("아직 열 수 없는 편지예요.")
    data object NotConnected : MutterError("연결된 사람이 아니에요. 먼저 연결해 주세요.")
    data object InviteAlreadyUsed : MutterError("이미 사용된 초대 링크예요. 새 초대를 요청해 주세요.")
    data class Server(override val userMessage: String) : MutterError(userMessage)
    data object Unknown : MutterError("문제가 생겼어요. 다시 시도해 주세요.")
}
