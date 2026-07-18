package com.efreedom.mutter.core.navigation

import kotlinx.serialization.Serializable

/** 하단 탭(iOS AppRoute). */
enum class AppTab { HOME, THREADS, INBOX, CONNECTIONS, PROFILE }

/**
 * push 가능한 화면 라우트(iOS Router FeatureRoute 이식).
 * Navigation Compose type-safe 라우팅용 — 각 목적지는 @Serializable.
 */
@Serializable
sealed interface FeatureRoute {
    @Serializable data object SignIn : FeatureRoute
    @Serializable data object OnboardNickname : FeatureRoute

    @Serializable data object ComposeNew : FeatureRoute
    @Serializable data class ComposeEdit(val letterId: String) : FeatureRoute
    @Serializable data class ComposeReply(val recipientId: String) : FeatureRoute

    @Serializable data class ViewerToken(val token: String, val password: String? = null) : FeatureRoute
    @Serializable data class ViewerMyLetter(val letterId: String) : FeatureRoute

    @Serializable data class Delivery(val letterId: String) : FeatureRoute
    @Serializable data class ConnectInvite(val token: String) : FeatureRoute
    @Serializable data class Thread(val counterpartId: String) : FeatureRoute

    @Serializable data object Takedown : FeatureRoute
    @Serializable data object Terms : FeatureRoute
    @Serializable data object Privacy : FeatureRoute
}
