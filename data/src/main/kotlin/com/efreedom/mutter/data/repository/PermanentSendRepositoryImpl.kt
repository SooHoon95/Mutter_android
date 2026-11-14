package com.efreedom.mutter.data.repository

import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.repository.PermanentSendRepository
import java.time.Instant
import javax.inject.Inject

/**
 * 영구 편지로 보내기 — 일반 발송과 같은 RPC(`issue_link`·`send_to_connection`)에 `p_permanent => true`를 실어 보낸다.
 * 호출 자체는 각 Repository 구현이 갖고 있고, 여기서는 영구로 보낸다는 것만 정한다.
 */
class PermanentSendRepositoryImpl @Inject constructor(
    private val delivery: DeliveryRepositoryImpl,
    private val connection: ConnectionRepositoryImpl,
) : PermanentSendRepository {

    override suspend fun issueLink(letterId: String, password: String?, revealAt: Instant?): DeliveryLink =
        delivery.issueLink(letterId, password, revealAt, permanent = true)

    override suspend fun sendToConnection(letterId: String, recipientId: String) =
        connection.sendToConnection(letterId, recipientId, permanent = true)
}
