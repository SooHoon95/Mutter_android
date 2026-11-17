package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.ConnectInviteRow
import com.efreedom.mutter.data.model.ConnectionRow
import com.efreedom.mutter.data.model.DisconnectParams
import com.efreedom.mutter.data.model.SendToConnectionParams
import com.efreedom.mutter.data.model.TokenParam
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.data.support.TokenGenerator
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.ConnectInvite
import com.efreedom.mutter.domain.model.Connection
import com.efreedom.mutter.domain.repository.ConnectionRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** iOS ConnectionRepository 이식. 초대 생성/수락/해제 + 연결 목록 + 편지 발송 RPC. */
class ConnectionRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : ConnectionRepository {

    private val client get() = provider.client

    override suspend fun createInvite(): String = wrap {
        val token = TokenGenerator.make()
        client.postgrest.rpc("create_connect_invite", TokenParam(token = token))
        token
    }

    override suspend fun revokeInvite(token: String) = wrap {
        client.postgrest.rpc("revoke_connect_invite", TokenParam(token = token))
        Unit
    }

    override suspend fun invite(token: String): ConnectInvite = wrap {
        client.postgrest.rpc("get_connect_invite", TokenParam(token = token))
            .decodeList<ConnectInviteRow>()
            .firstOrNull()
            ?.toDomain()
            ?: throw MutterError.NotFound
    }

    override suspend fun accept(token: String) = wrap {
        client.postgrest.rpc("accept_connect_invite", TokenParam(token = token))
        Unit
    }

    override suspend fun myConnections(): List<Connection> = wrap {
        // 연결 탭은 앱 복귀(ON_RESUME) 때 이 목록을 다시 받는다. supabase-kt 3.1.4는 ON_STOP에서 세션 상태를
        // Initializing으로 되돌리고 ON_START에서 저장소 세션을 비동기로 다시 읽는다 — 그 사이 요청은 사용자 토큰 없이
        // 나가 서버가 AUTH_REQUIRED로 거절한다. 세션 로드가 끝날 때까지 기다린다(로그인 상태면 곧바로 지나간다).
        // 저장된 세션을 못 읽으면 끝없이 기다릴 수 있어 3초에서 끊고, 시간이 지나면 요청은 그대로 나가 서버가 답한 대로 처리된다.
        withTimeoutOrNull(3_000) { client.auth.awaitInitialization() }
        client.postgrest.rpc("get_my_connections")
            .decodeList<ConnectionRow>()
            .map { it.toDomain() }
    }

    override suspend fun disconnect(otherUserId: String) = wrap {
        client.postgrest.rpc("disconnect_connection", DisconnectParams(otherUser = otherUserId))
        Unit
    }

    /** send_to_connection. [permanent]면 서버가 발송과 편지권 1장 사용을 한 트랜잭션으로 처리한다. */
    override suspend fun send(letterId: String, recipientId: String, permanent: Boolean) = wrap {
        client.postgrest.rpc(
            "send_to_connection",
            SendToConnectionParams(
                letterId = letterId,
                recipient = recipientId,
                token = TokenGenerator.make(),
                permanent = permanent,
            ),
        )
        Unit
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
