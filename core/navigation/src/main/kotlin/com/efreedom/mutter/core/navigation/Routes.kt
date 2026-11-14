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

    /** 보낸 편지 삭제함(30일 보관) — 홈 상단 휴지통 버튼. */
    @Serializable data object Trash : FeatureRoute
    /** 받은 편지 삭제함(30일 보관) — 받은 편지함 헤더 휴지통 버튼. */
    @Serializable data object InboxTrash : FeatureRoute

    @Serializable data class Delivery(val letterId: String) : FeatureRoute
    /** 편지권(잔액·구매·내역) — 프로필 항목, 편지권 부족(NO_TICKET)에서 진입. */
    @Serializable data object Tickets : FeatureRoute
    /** 영구 편지를 보내려다 편지권이 없어 들어온 구매 화면 — 구매하면 닫히고 보내기로 돌아간다. */
    @Serializable data object TicketPurchase : FeatureRoute
    @Serializable data class ConnectInvite(val token: String) : FeatureRoute
    @Serializable data class Thread(val counterpartId: String) : FeatureRoute

    @Serializable data object Takedown : FeatureRoute
    @Serializable data object Terms : FeatureRoute
    @Serializable data object Privacy : FeatureRoute
}
