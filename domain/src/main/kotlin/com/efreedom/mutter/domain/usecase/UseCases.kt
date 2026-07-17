package com.efreedom.mutter.domain.usecase

import com.efreedom.mutter.domain.error.MutterError
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
import com.efreedom.mutter.domain.model.MusicCue
import com.efreedom.mutter.domain.model.MusicSource
import com.efreedom.mutter.domain.model.Profile
import com.efreedom.mutter.domain.model.ScValidation
import com.efreedom.mutter.domain.model.SentLetterSummary
import com.efreedom.mutter.domain.model.Session
import com.efreedom.mutter.domain.model.SocialProvider
import com.efreedom.mutter.domain.model.ThreadLetter
import com.efreedom.mutter.domain.model.TrackSourceSpec
import com.efreedom.mutter.domain.repository.AuthRepository
import com.efreedom.mutter.domain.repository.ConnectionRepository
import com.efreedom.mutter.domain.repository.DeliveryRepository
import com.efreedom.mutter.domain.repository.InboxRepository
import com.efreedom.mutter.domain.repository.LetterRepository
import com.efreedom.mutter.domain.repository.ProfileRepository
import com.efreedom.mutter.domain.repository.PushTokenRepository
import com.efreedom.mutter.domain.repository.ReceiptRepository
import com.efreedom.mutter.domain.repository.SoundCloudRepository
import com.efreedom.mutter.domain.repository.TakedownRepository
import com.efreedom.mutter.domain.repository.ThreadRepository
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
    suspend fun signInSocial(provider: SocialProvider, token: String): Session = repository.signInSocial(provider, token)
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
}

class LetterUseCase @Inject constructor(private val repository: LetterRepository) {
    suspend fun create(draft: LetterDraft): Letter = repository.create(draft)
    suspend fun update(id: String, draft: LetterDraft) = repository.update(id, draft)
    suspend fun letter(id: String): Letter? = repository.letter(id)
    suspend fun myLetters(): List<Letter> = repository.myLetters()
    suspend fun myLettersWithStatus(): List<LetterWithStatus> = repository.myLettersWithStatus()
    suspend fun delete(id: String) = repository.delete(id)
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
