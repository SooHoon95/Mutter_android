package com.efreedom.mutter.domain.repository

import com.efreedom.mutter.domain.model.DeliveryLink
import java.time.Instant

/**
 * 영구 편지로 보내기 — 링크 발급·연결 발송과 편지권 1장 사용을 서버가 **한 트랜잭션으로** 처리한다
 * (`issue_link`·`send_to_connection`의 `p_permanent => true`). 어느 한쪽이 실패하면 둘 다 일어나지 않는다.
 * 계약 "편지권은 실제로 보낼 때만 쓴다". 편지권이 없으면 `MutterError.NoTicket`.
 *
 * 일반 발송([DeliveryRepository.issue]·[ConnectionRepository.send])과 같은 RPC지만 편지권이 함께 쓰이므로 진입점을 따로 둔다.
 */
interface PermanentSendRepository {
    suspend fun issueLink(letterId: String, password: String?, revealAt: Instant?): DeliveryLink
    suspend fun sendToConnection(letterId: String, recipientId: String)
}
