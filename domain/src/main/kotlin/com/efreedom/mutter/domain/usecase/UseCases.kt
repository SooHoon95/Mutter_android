package com.efreedom.mutter.domain.usecase

import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Connection
import com.efreedom.mutter.domain.model.ConnectInvite
import com.efreedom.mutter.domain.model.Counterpart
import com.efreedom.mutter.domain.model.DeletedInboxItem
import com.efreedom.mutter.domain.model.DeletedLetter
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.InboxItem
import com.efreedom.mutter.domain.model.Letter
import com.efreedom.mutter.domain.model.LetterBlock
import com.efreedom.mutter.domain.model.LetterBlocks
import com.efreedom.mutter.domain.model.LetterAccessState
import com.efreedom.mutter.domain.model.LetterCover
import com.efreedom.mutter.domain.model.LetterDraft
import com.efreedom.mutter.domain.model.LetterOpenSummary
import com.efreedom.mutter.domain.model.LetterPayload
import com.efreedom.mutter.domain.model.LetterReader
import com.efreedom.mutter.domain.model.LetterWithStatus
import com.efreedom.mutter.domain.model.MusicCue
import com.efreedom.mutter.domain.model.MusicSource
import com.efreedom.mutter.domain.model.Profile
import com.efreedom.mutter.domain.model.SavedLetter
import com.efreedom.mutter.domain.model.ScValidation
import com.efreedom.mutter.domain.model.SentLetterSummary
import com.efreedom.mutter.domain.model.Session
import com.efreedom.mutter.domain.model.SocialAuthResult
import com.efreedom.mutter.domain.model.SocialProvider
import com.efreedom.mutter.domain.model.StorePurchaseResult
import com.efreedom.mutter.domain.model.TicketHistoryItem
import com.efreedom.mutter.domain.model.TicketProduct
import com.efreedom.mutter.domain.model.ThreadLetter
import com.efreedom.mutter.domain.model.TrackSourceSpec
import com.efreedom.mutter.domain.repository.AppUpdateRepository
import com.efreedom.mutter.domain.repository.AuthRepository
import com.efreedom.mutter.domain.repository.ConnectionRepository
import com.efreedom.mutter.domain.repository.DeliveryRepository
import com.efreedom.mutter.domain.repository.InboxRepository
import com.efreedom.mutter.domain.repository.LetterAccessRepository
import com.efreedom.mutter.domain.repository.LetterPhotoRepository
import com.efreedom.mutter.domain.repository.LetterRepository
import com.efreedom.mutter.domain.repository.ProfileRepository
import com.efreedom.mutter.domain.repository.PushTokenRepository
import com.efreedom.mutter.domain.repository.ReceiptRepository
import com.efreedom.mutter.domain.repository.SoundCloudRepository
import com.efreedom.mutter.domain.repository.TakedownRepository
import com.efreedom.mutter.domain.repository.ThreadRepository
import com.efreedom.mutter.domain.repository.TicketRepository
import com.efreedom.mutter.domain.repository.TicketStore
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.time.Instant
import javax.inject.Inject

// UseCase — Repository를 주입받는 얇은 계층(iOS *Usecase 이식). 대부분 pass-through,
// 로직 보유: AudioUseCase(cue→spec), ProfileUseCase(닉네임 trim/검증), PushTokenUseCase(platform="android").

class AuthUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend fun requestCode(email: String) = repository.requestCode(email)
    suspend fun verifyCode(email: String, code: String): Session = repository.verifyCode(email, code)
    suspend fun signIn(email: String, password: String): Session = repository.signIn(email, password)
    suspend fun signInApple(idToken: String, nonce: String): Session = repository.signInApple(idToken, nonce)
    suspend fun signInAppleWeb() = repository.signInAppleWeb()
    suspend fun startSocial(provider: SocialProvider, idToken: String): SocialAuthResult =
        repository.startSocial(provider, idToken)
    suspend fun registerSocial(provider: SocialProvider, idToken: String, nickname: String): Session =
        repository.registerSocial(provider, idToken, nickname)
    suspend fun signOut() = repository.signOut()
    suspend fun currentSession(): Session? = repository.currentSession()
}

class ProfileUseCase @Inject constructor(private val repository: ProfileRepository) {
    suspend fun myProfile(): Profile? = repository.myProfile()
    suspend fun updateNickname(nickname: String) {
        val trimmed = nickname.trim()
        if (trimmed.isEmpty()) throw MutterError.Server("닉네임을 입력해 주세요.")
        repository.updateNickname(trimmed)
    }
    suspend fun deleteAccount() = repository.deleteAccount()
}

class ConnectionUseCase @Inject constructor(private val repository: ConnectionRepository) {
    suspend fun createInvite(): String = repository.createInvite()
    suspend fun revokeInvite(token: String) = repository.revokeInvite(token)
    suspend fun invite(token: String): ConnectInvite = repository.invite(token)
    suspend fun accept(token: String) = repository.accept(token)
    suspend fun myConnections(): List<Connection> = repository.myConnections()
    suspend fun disconnect(otherUserId: String) = repository.disconnect(otherUserId)
    suspend fun send(letterId: String, recipientId: String) = repository.send(letterId, recipientId)
}

class DeliveryUseCase @Inject constructor(private val repository: DeliveryRepository) {
    suspend fun issue(letterId: String, password: String?, revealAt: Instant?): DeliveryLink =
        repository.issue(letterId, password, revealAt)
    suspend fun revoke(token: String) = repository.revoke(token)
    suspend fun links(letterId: String): List<DeliveryLink> = repository.links(letterId)
    suspend fun open(token: String, password: String?): LetterPayload = repository.open(token, password)
}

class InboxUseCase @Inject constructor(private val repository: InboxRepository) {
    suspend fun save(token: String) = repository.save(token)
    suspend fun myInbox(): List<InboxItem> = repository.myInbox()
    suspend fun remove(letterIds: List<String>) = repository.remove(letterIds)
    suspend fun deletedItems(): List<DeletedInboxItem> = repository.deletedItems()
    suspend fun restore(letterIds: List<String>): Int = repository.restore(letterIds)
    suspend fun purge(letterIds: List<String>): Int = repository.purge(letterIds)
}

class LetterUseCase @Inject constructor(
    private val repository: LetterRepository,
    private val photos: LetterPhotoRepository,
) {
    suspend fun create(draft: LetterDraft): Letter = repository.create(withoutPendingPhotos(draft))
    suspend fun update(id: String, draft: LetterDraft) = repository.update(id, draft)
    suspend fun letter(id: String): Letter? = repository.letter(id)
    suspend fun myLetters(): List<Letter> = repository.myLetters()
    suspend fun myLettersWithStatus(): List<LetterWithStatus> = repository.myLettersWithStatus()

    /**
     * 편지를 삭제함으로 옮긴다(서버가 30일 보관). 사진은 지우지 않는다 — 되살리면 사진도 그대로 돌아와야 하고,
     * 보관 중인 편지의 사진은 Storage 정책상 지워지지도 않는다. 사진 정리는 [purge]와 서버 30일 정리가 맡는다.
     */
    suspend fun delete(id: String) = repository.delete(id)

    suspend fun deletedLetters(): List<DeletedLetter> = repository.deletedLetters()
    suspend fun restore(id: String) = repository.restore(id)

    /** 영구 삭제 뒤 그 폴더의 사진도 지운다(best effort — 실패해도 영구 삭제는 성공으로 본다. 남은 객체는 고아가 될 뿐). */
    suspend fun purge(id: String) {
        repository.purge(id)
        runCatching { photos.deleteAll(id) }
    }

    /**
     * 사진 포함 저장(계약 §5). 순서가 중요하다 — Storage 경로에 letterId가 들어가므로 id가 먼저 있어야 한다.
     *  1. 새 편지면 먼저 생성해 id를 확보한다(이때는 아직 경로가 없는 사진을 뺀 채로 저장).
     *     생성 직후 [onCreated]로 id를 알린다 — 뒤 단계가 실패해도 호출부가 edit 모드로 승격해 중복 생성을 막게.
     *  2. 대기 중인 사진을 업로드해 path를 받는다. 하나라도 실패하면 throw(저장 실패). 이미 올라간 객체는
     *     남겨 둔다 — 다음 저장의 4단계가 정리한다.
     *  3. path가 채워진 paragraphs로 update한다.
     *  4. [previousPhotoPaths]에 있었지만 지금은 없는 경로를 지운다(best effort).
     */
    suspend fun saveWithPhotos(
        letterId: String?,
        draft: LetterDraft,
        previousPhotoPaths: Set<String>,
        onCreated: (String) -> Unit = {},
    ): SavedLetter {
        val hasPending = draft.blocks.any { it is LetterBlock.Photo && it.photo.path == null }
        val id = letterId ?: repository.create(withoutPendingPhotos(draft)).id.also(onCreated)

        val resolved = draft.blocks.map { block ->
            if (block is LetterBlock.Photo && block.photo.path == null) {
                val jpeg = block.photo.pendingJpeg
                    ?: throw MutterError.Server("사진을 올릴 수 없었어요. 다시 시도해 주세요.")
                val path = photos.upload(id, jpeg)
                LetterBlock.Photo(block.photo.copy(path = path, pendingJpeg = null))
            } else {
                block
            }
        }
        // 새 편지이고 올릴 사진도 없으면 1단계 생성 내용이 이미 최종본이다 — 같은 내용을 다시 update하지 않는다.
        if (letterId != null || hasPending) {
            repository.update(id, draft.copy(blocks = resolved))
        }

        val removed = previousPhotoPaths - LetterBlocks.photoPaths(resolved)
        if (removed.isNotEmpty()) runCatching { photos.delete(removed) }
        return SavedLetter(id = id, blocks = resolved)
    }

    /** 아직 업로드 전(path 없음) 사진 블록을 뺀다 — paragraphs에는 경로가 있는 사진만 들어갈 수 있다. */
    private fun withoutPendingPhotos(draft: LetterDraft): LetterDraft =
        draft.copy(blocks = draft.blocks.filterNot { it is LetterBlock.Photo && it.photo.path == null })
}

/** 사진 서명 URL 조회(뷰어·이어쓰기 썸네일). 서명 URL은 1시간 유효라 화면 진입 때 한 번만 받는다. */
class LetterPhotoUseCase @Inject constructor(private val repository: LetterPhotoRepository) {
    suspend fun signedUrls(token: String, password: String?): Map<String, String> =
        repository.signedUrls(token, password)
    suspend fun signedUrls(letterId: String): Map<String, String> = repository.signedUrls(letterId)
}

class ReceiptUseCase @Inject constructor(private val repository: ReceiptRepository) {
    suspend fun recordOpen(token: String) = repository.recordOpen(token)
    suspend fun myLetterOpens(): List<LetterOpenSummary> = repository.myLetterOpens()
}

class TakedownUseCase @Inject constructor(private val repository: TakedownRepository) {
    suspend fun report(letterId: String?, trackRef: String?, claimant: String, contact: String, reason: String) =
        repository.report(letterId, trackRef, claimant, contact, reason)
}

class ThreadUseCase @Inject constructor(private val repository: ThreadRepository) {
    suspend fun counterparts(): List<Counterpart> = repository.counterparts()
    suspend fun thread(counterpartId: String): List<ThreadLetter> = repository.thread(counterpartId)
    suspend fun sentWithRecipients(): List<SentLetterSummary> = repository.sentWithRecipients()
}

class PushTokenUseCase @Inject constructor(private val repository: PushTokenRepository) {
    suspend fun register(token: String, deviceId: String?) =
        repository.upsert(token = token, platform = "android", deviceId = deviceId)
}

class AppUpdateUseCase @Inject constructor(private val repository: AppUpdateRepository) {
    /**
     * 현재 앱 버전이 서버가 지정한 최소 지원 버전보다 낮으면 true(강제 업데이트 필요).
     * 최소 버전이 없거나 조회/파싱 실패면 false — iOS와 동일하게 fail-open(사용자를 막지 않음).
     */
    suspend fun isUpdateRequired(currentVersion: String): Boolean {
        val min = repository.minSupportedVersion("android") ?: return false
        return compareVersions(currentVersion, min) < 0
    }
}

/**
 * 점(.) 구분 숫자 버전 비교. a<b면 음수, 같으면 0, a>b면 양수.
 * 각 파트의 선행 숫자만 취한다("1-beta" → 1) — 프리릴리스 접미사로 게이트가 과발동하지 않도록.
 */
private fun compareVersions(a: String, b: String): Int {
    fun parts(v: String) = v.split(".").map { seg -> seg.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    val pa = parts(a)
    val pb = parts(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

class AudioUseCase @Inject constructor(private val soundCloud: SoundCloudRepository) {
    /** cue → 재생 사양. hosted는 절대 URL 필요, soundcloud는 파싱 가능해야 함(아니면 throw). */
    fun resolvePlayback(cue: MusicCue): TrackSourceSpec = when (cue.source) {
        MusicSource.HOSTED -> {
            val uri = runCatching { URI(cue.ref) }.getOrNull()
            if (uri?.scheme == null) throw MutterError.Server("재생할 수 없는 음원이에요.")
            TrackSourceSpec.Hosted(cue.ref, cue.startMs)
        }
        MusicSource.SOUNDCLOUD -> {
            runCatching { URI(cue.ref) }.getOrNull()
                ?: throw MutterError.Server("재생할 수 없는 음원이에요.")
            TrackSourceSpec.SoundCloud(cue.ref, cue.startMs)
        }
    }

    suspend fun validateSoundCloud(url: String): ScValidation = soundCloud.validate(url)
}

class LetterAccessUseCase @Inject constructor(private val repository: LetterAccessRepository) {
    suspend fun cover(token: String): LetterCover = repository.cover(token)

    /**
     * 목록에 합칠 상태(letterId → 상태). 실패(서버 반영 전 RPC 없음 등)는 빈 맵 — 목록은 기존 모습 그대로 보인다.
     */
    suspend fun accessStates(letterIds: List<String>): Map<String, LetterAccessState> {
        if (letterIds.isEmpty()) return emptyMap()
        return try {
            repository.accessStates(letterIds.distinct()).associateBy { it.letterId }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    suspend fun readers(letterId: String): List<LetterReader> = repository.readers(letterId)
    suspend fun makePermanent(letterId: String) = repository.makePermanent(letterId)
    suspend fun keepReceived(token: String, password: String?) = repository.keepReceived(token, password)
    suspend fun duplicate(letterId: String): String = repository.duplicate(letterId)
}

/** 구매 한 번의 결과(서버 검증까지 끝난 뒤). */
sealed interface TicketPurchaseOutcome {
    /** 지급(또는 이미 지급) 확인 후 소비까지 끝남. [tickets]는 지급 후 잔액. */
    data class Granted(val tickets: Int) : TicketPurchaseOutcome
    data object Pending : TicketPurchaseOutcome
    data object Cancelled : TicketPurchaseOutcome
}

/**
 * 편지권 — 잔액·내역 + 스토어 구매 순서(구매 → verify-purchase → 200이면 소비).
 * 검증이 실패하면 거래를 소비하지 않고 남겨 둔다 → [resendUnconsumed]가 다음 실행·화면 진입 때 다시 보낸다.
 */
class TicketUseCase @Inject constructor(
    private val repository: TicketRepository,
    private val store: TicketStore,
) {
    suspend fun entitlements(): Entitlements = repository.entitlements()

    /** 실패(서버 반영 전 등)는 null — 호출부는 편지권 UI를 숨긴 기존 화면으로 둔다. */
    suspend fun entitlementsOrNull(): Entitlements? = try {
        repository.entitlements()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    suspend fun history(): List<TicketHistoryItem> = repository.history()
    suspend fun products(): List<TicketProduct> = store.products(TicketProduct.ALL)

    suspend fun purchase(productId: String): TicketPurchaseOutcome =
        when (val result = store.purchase(productId)) {
            is StorePurchaseResult.Purchased -> {
                val tickets = repository.verifyPurchase(result.purchase.productId, result.purchase.purchaseToken)
                // 200을 받은 뒤에만 소비한다. 소비 실패는 다음 재전송이 같은 영수증을 granted=false로 받아 다시 소비한다.
                runCatching { store.consume(result.purchase.purchaseToken) }
                TicketPurchaseOutcome.Granted(tickets)
            }
            StorePurchaseResult.Pending -> TicketPurchaseOutcome.Pending
            StorePurchaseResult.Cancelled -> TicketPurchaseOutcome.Cancelled
        }

    /** 남은 거래를 다시 검증·소비한다. 하나라도 지급되면 true(잔액 새로고침 신호). 실패는 다음 기회로 넘긴다. */
    suspend fun resendUnconsumed(): Boolean {
        val pending = try {
            store.unconsumedPurchases()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return false
        }
        var resolved = false
        for (purchase in pending) {
            try {
                repository.verifyPurchase(purchase.productId, purchase.purchaseToken)
                runCatching { store.consume(purchase.purchaseToken) }
                resolved = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 검증 실패 — 거래를 남겨 두고 다음 기회에 다시 보낸다.
            }
        }
        return resolved
    }
}
