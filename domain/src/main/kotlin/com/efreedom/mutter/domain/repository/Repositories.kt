package com.efreedom.mutter.domain.repository

import com.efreedom.mutter.domain.model.Connection
import com.efreedom.mutter.domain.model.ConnectInvite
import com.efreedom.mutter.domain.model.Counterpart
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.InboxItem
import com.efreedom.mutter.domain.model.Letter
import com.efreedom.mutter.domain.model.LetterDraft
import com.efreedom.mutter.domain.model.LetterOpenSummary
import com.efreedom.mutter.domain.model.LetterPayload
import com.efreedom.mutter.domain.model.LetterWithStatus
import com.efreedom.mutter.domain.model.Profile
import com.efreedom.mutter.domain.model.ScValidation
import com.efreedom.mutter.domain.model.SentLetterSummary
import com.efreedom.mutter.domain.model.Session
import com.efreedom.mutter.domain.model.SocialAuthResult
import com.efreedom.mutter.domain.model.SocialProvider
import com.efreedom.mutter.domain.model.ThreadLetter
import java.time.Instant

// Repository 인터페이스(구현은 :data). iOS *Repositorable 1:1 이식. 모두 suspend.

interface AuthRepository {
    suspend fun requestCode(email: String)
    suspend fun verifyCode(email: String, code: String): Session
    suspend fun signIn(email: String, password: String): Session
    suspend fun signInApple(idToken: String, nonce: String): Session
    /** 소셜 1단계 — idToken만 검증(checkOnly). 기존이면 세션 확립, 신규면 생성 없이 NeedsNickname. */
    suspend fun startSocial(provider: SocialProvider, idToken: String): SocialAuthResult
    /** 소셜 2단계 — 신규 회원을 닉네임과 함께 가입시키고 세션을 확립한다. */
    suspend fun registerSocial(provider: SocialProvider, idToken: String, nickname: String): Session
    suspend fun signOut()
    suspend fun currentSession(): Session?
}

interface ProfileRepository {
    suspend fun myProfile(): Profile?
    suspend fun updateNickname(nickname: String)
    suspend fun deleteAccount()
}

interface ConnectionRepository {
    suspend fun createInvite(): String
    suspend fun revokeInvite(token: String)
    suspend fun invite(token: String): ConnectInvite
    suspend fun accept(token: String)
    suspend fun myConnections(): List<Connection>
    suspend fun disconnect(otherUserId: String)
    suspend fun send(letterId: String, recipientId: String)
}

interface DeliveryRepository {
    suspend fun issue(letterId: String, password: String?, revealAt: Instant?): DeliveryLink
    suspend fun revoke(token: String)
    suspend fun links(letterId: String): List<DeliveryLink>
    suspend fun open(token: String, password: String?): LetterPayload
}

interface InboxRepository {
    suspend fun save(token: String)
    suspend fun myInbox(): List<InboxItem>
}

interface LetterRepository {
    suspend fun create(draft: LetterDraft): Letter
    suspend fun update(id: String, draft: LetterDraft)
    suspend fun letter(id: String): Letter?
    suspend fun myLetters(): List<Letter>
    suspend fun myLettersWithStatus(): List<LetterWithStatus>
    suspend fun delete(id: String)
}

interface ReceiptRepository {
    suspend fun recordOpen(token: String)
    suspend fun myLetterOpens(): List<LetterOpenSummary>
}

interface TakedownRepository {
    suspend fun report(
        letterId: String?,
        trackRef: String?,
        claimant: String,
        contact: String,
        reason: String,
    )
}

interface ThreadRepository {
    suspend fun counterparts(): List<Counterpart>
    suspend fun thread(counterpartId: String): List<ThreadLetter>
    suspend fun sentWithRecipients(): List<SentLetterSummary>
}

interface PushTokenRepository {
    suspend fun upsert(token: String, platform: String, deviceId: String?)
}

/** SoundCloud oEmbed 검증(비-Supabase, 예외 없이 결과 반환). */
interface SoundCloudRepository {
    suspend fun validate(url: String): ScValidation
}

/** 동기 캐시 세션 조회(iOS SessionProvidable). */
interface SessionProvider {
    val currentUserId: String?
}
