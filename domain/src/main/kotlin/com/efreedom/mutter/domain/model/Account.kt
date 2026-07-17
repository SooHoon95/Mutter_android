package com.efreedom.mutter.domain.model

/** 소셜 로그인 제공자(Apple은 전용 경로라 여기 없음 — iOS와 동일). */
enum class SocialProvider(val raw: String) {
    GOOGLE("google"),
    KAKAO("kakao"),
}

data class Session(val userId: String, val email: String?)

data class Profile(val id: String, val nickname: String? = null)
