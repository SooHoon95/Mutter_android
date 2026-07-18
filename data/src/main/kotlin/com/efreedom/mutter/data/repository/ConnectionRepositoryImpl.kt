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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
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
        client.postgrest.rpc("get_my_connections")
            .decodeList<ConnectionRow>()
            .map { it.toDomain() }
    }

    override suspend fun disconnect(otherUserId: String) = wrap {
        client.postgrest.rpc("disconnect_connection", DisconnectParams(otherUser = otherUserId))
        Unit
    }

    override suspend fun send(letterId: String, recipientId: String) = wrap {
        client.postgrest.rpc(
            "send_to_connection",
            SendToConnectionParams(
                letterId = letterId,
                recipient = recipientId,
                token = TokenGenerator.make(),
            ),
        )
        Unit
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
