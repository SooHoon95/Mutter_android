package com.efreedom.mutter.domain.usecase

import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.repository.PermanentSendRepository
import java.time.Instant
import javax.inject.Inject

/**
 * 보내기 전 선택에서 영구 편지를 고른 편지를 보낸다 — 이때 비로소 편지권 1장이 쓰인다(서버가 발송과 함께 처리).
 * 고르기만 하고 보내지 않으면 편지권은 나가지 않는다. 편지권이 없으면 `MutterError.NoTicket`.
 */
class PermanentSendUseCase @Inject constructor(private val repository: PermanentSendRepository) {
    suspend fun issueLink(letterId: String, password: String?, revealAt: Instant?): DeliveryLink =
        repository.issueLink(letterId, password, revealAt)

    suspend fun sendToConnection(letterId: String, recipientId: String) =
        repository.sendToConnection(letterId, recipientId)
}
