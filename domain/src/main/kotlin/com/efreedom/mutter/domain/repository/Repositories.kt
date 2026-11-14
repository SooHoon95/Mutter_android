package com.efreedom.mutter.domain.repository

import com.efreedom.mutter.domain.model.Connection
import com.efreedom.mutter.domain.model.ConnectInvite
import com.efreedom.mutter.domain.model.Counterpart
import com.efreedom.mutter.domain.model.DeletedInboxItem
import com.efreedom.mutter.domain.model.DeletedLetter
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.InboxItem
import com.efreedom.mutter.domain.model.Letter
import com.efreedom.mutter.domain.model.LetterAccessState
import com.efreedom.mutter.domain.model.LetterCover
import com.efreedom.mutter.domain.model.LetterDraft
import com.efreedom.mutter.domain.model.LetterOpenSummary
import com.efreedom.mutter.domain.model.LetterPayload
import com.efreedom.mutter.domain.model.LetterReader
import com.efreedom.mutter.domain.model.LetterWithStatus
import com.efreedom.mutter.domain.model.Profile
import com.efreedom.mutter.domain.model.ScValidation
import com.efreedom.mutter.domain.model.SentLetterSummary
import com.efreedom.mutter.domain.model.Session
import com.efreedom.mutter.domain.model.SocialAuthResult
import com.efreedom.mutter.domain.model.SocialProvider
import com.efreedom.mutter.domain.model.StorePurchase
import com.efreedom.mutter.domain.model.StorePurchaseResult
import com.efreedom.mutter.domain.model.TicketHistoryItem
import com.efreedom.mutter.domain.model.TicketProduct
import com.efreedom.mutter.domain.model.ThreadLetter
import java.time.Instant

// Repository 인터페이스(구현은 :data). iOS *Repositorable 1:1 이식. 모두 suspend.

interface AuthRepository {
    suspend fun requestCode(email: String)
    suspend fun verifyCode(email: String, code: String): Session
    suspend fun signIn(email: String, password: String): Session
    suspend fun signInApple(idToken: String, nonce: String): Session
    /** Apple 웹 OAuth(브라우저) 시작 — Android는 네이티브 SDK가 없어 브라우저 리다이렉트로 로그인. 세션은 리다이렉트 처리 후 확립. */
    suspend fun signInAppleWeb()
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
    /** [permanent]면 서버가 발송과 편지권 1장 사용을 한 트랜잭션으로 처리한다(`p_permanent`). 편지권이 없으면 `MutterError.NoTicket`. */
    suspend fun send(letterId: String, recipientId: String, permanent: Boolean = false)
}

interface DeliveryRepository {
    /** [permanent]면 서버가 링크 발급과 편지권 1장 사용을 한 트랜잭션으로 처리한다(`p_permanent`). 편지권이 없으면 `MutterError.NoTicket`. */
    suspend fun issue(letterId: String, password: String?, revealAt: Instant?, permanent: Boolean = false): DeliveryLink
    suspend fun revoke(token: String)
    suspend fun links(letterId: String): List<DeliveryLink>
    suspend fun open(token: String, password: String?): LetterPayload
}

interface InboxRepository {
    suspend fun save(token: String)
    suspend fun myInbox(): List<InboxItem>
    /** 받은 편지들을 삭제함으로 옮긴다(서버 뷰 트리거가 30일 보관으로 바꾼다). */
    suspend fun remove(letterIds: List<String>)
    /** 받은 편지 삭제함 목록(최근 삭제순). */
    suspend fun deletedItems(): List<DeletedInboxItem>
    /** 삭제함 → 받은 편지함으로 되살린다. 되살린 수를 돌려준다. */
    suspend fun restore(letterIds: List<String>): Int
    /** 받은 편지함에서 영구 삭제한다(보낸 사람 편지는 그대로). 지운 수를 돌려준다. */
    suspend fun purge(letterIds: List<String>): Int
}

interface LetterRepository {
    suspend fun create(draft: LetterDraft): Letter
    suspend fun update(id: String, draft: LetterDraft)
    suspend fun letter(id: String): Letter?
    suspend fun myLetters(): List<Letter>
    suspend fun myLettersWithStatus(): List<LetterWithStatus>
    /** 서버(뷰 letters의 INSTEAD OF DELETE 트리거)가 영구 삭제 대신 삭제함으로 보관한다. */
    suspend fun delete(id: String)
    /** 삭제함 목록(최근 삭제순). */
    suspend fun deletedLetters(): List<DeletedLetter>
    /** 삭제함 → 원래 목록으로 되살린다. 대상이 없으면 NotFound. */
    suspend fun restore(id: String)
    /** 삭제함에 있는 편지를 영구 삭제한다(되돌릴 수 없음). 사진 정리는 호출부 몫. */
    suspend fun purge(id: String)
}

/**
 * 편지 사진 저장소(Storage 버킷 `letter-photos`) + 서명 URL 발급(Edge Function `letter-photo-urls`).
 * 버킷이 비공개라 사진은 항상 서명 URL로만 본다. iOS LetterPhotoRepository 대응.
 */
interface LetterPhotoRepository {
    /** JPEG을 `<uid>/<letterId>/<uuid>.jpg`에 올리고 그 경로를 돌려준다. */
    suspend fun upload(letterId: String, jpeg: ByteArray): String
    suspend fun delete(paths: Collection<String>)
    /** `<uid>/<letterId>/` 아래 객체를 모두 지운다(편지 영구 삭제 시 — 삭제함 보관 중엔 사진을 남긴다). */
    suspend fun deleteAll(letterId: String)
    /** 수신자 모드 — 링크 토큰(+암호)으로 path→서명 URL. */
    suspend fun signedUrls(token: String, password: String?): Map<String, String>
    /** 소유자 모드 — 로그인 사용자 JWT + letterId로 path→서명 URL. */
    suspend fun signedUrls(letterId: String): Map<String, String>
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

/** 강제 업데이트용 최소 지원 버전 조회(iOS AppUpdateRepositorable 대응). */
interface AppUpdateRepository {
    /** 플랫폼("android")의 최소 지원 versionName. 값이 없거나 조회 실패면 null(fail-open). */
    suspend fun minSupportedVersion(platform: String): String?
}

/**
 * 편지 접근 상태(영구 편지권 계약) — 봉투·목록 상태·열람자·영구화·사본.
 * 서버 반영 전에는 RPC가 없어 실패한다 → 호출부(목록)는 조용히 기존 동작으로 돌아간다.
 */
interface LetterAccessRepository {
    /** 로그인 전·잠김 화면용 봉투(본문 없음, 열람 기록 없음). */
    suspend fun cover(token: String): LetterCover
    /** 내가 보냈거나 받은 편지함에 가진 편지만 돌아온다. 빈 목록이면 호출하지 않는다. */
    suspend fun accessStates(letterIds: List<String>): List<LetterAccessState>
    /** 소유자만. 처음 연 순. */
    suspend fun readers(letterId: String): List<LetterReader>
    /** 보낸 사람이 편지권 1장으로 영구화(이미 영구·관리자는 차감 없음). */
    suspend fun makePermanent(letterId: String)
    /** 받은 사람이 편지권 1장으로 간직. 성공하면 받은 편지함에 들어간다. */
    suspend fun keepReceived(token: String, password: String?)
    /** 같은 내용으로 새 편지를 만든다(duplicate-letter). 새 편지 id. 링크는 없다. */
    suspend fun duplicate(letterId: String): String
}

/** 편지권 잔액·내역·구매 검증. */
interface TicketRepository {
    suspend fun entitlements(): Entitlements
    suspend fun history(): List<TicketHistoryItem>
    /** verify-purchase. HTTP 200(지급됨·이미 지급됨)이면 지급 후 잔액, 그 외는 throw. */
    suspend fun verifyPurchase(productId: String, purchaseToken: String): Int
}

/**
 * 스토어 결제(구현은 :data의 Play Billing). 거래 소비는 서버 검증 200 뒤에만 한다 — 순서는 [TicketUseCase]가 쥔다.
 */
interface TicketStore {
    /** 찾은 상품만 돌아온다(Play Console에 아직 없으면 빈 목록). */
    suspend fun products(productIds: List<String>): List<TicketProduct>
    suspend fun purchase(productId: String): StorePurchaseResult
    /** 결제는 끝났지만 아직 소비하지 않은 거래(앱 종료·검증 실패로 남은 것). */
    suspend fun unconsumedPurchases(): List<StorePurchase>
    suspend fun consume(purchaseToken: String)
}
