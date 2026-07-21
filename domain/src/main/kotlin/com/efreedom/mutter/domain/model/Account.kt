package com.efreedom.mutter.domain.model

/** 소셜 로그인 제공자(Apple은 전용 경로라 여기 없음 — iOS와 동일). */
enum class SocialProvider(val raw: String) {
    GOOGLE("google"),
    KAKAO("kakao"),
}

data class Session(val userId: String, val email: String?)

data class Profile(val id: String, val nickname: String? = null)

/**
 * 소셜 로그인 1단계(checkOnly) 결과.
 * 닉네임 전 가입을 막기 위해, 신규 회원이면 계정 생성 없이 [NeedsNickname]만 반환한다.
 */
sealed interface SocialAuthResult {
    /** 기존 회원 — 세션 확립 완료(바로 로그인). */
    data class Authenticated(val session: Session) : SocialAuthResult

    /** 신규 회원 — 아직 계정 미생성. 닉네임을 받아 [AuthRepository.registerSocial]로 가입해야 한다. */
    data object NeedsNickname : SocialAuthResult
}
