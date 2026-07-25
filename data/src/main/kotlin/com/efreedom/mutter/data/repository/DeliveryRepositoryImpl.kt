package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.DeliveryLinkRow
import com.efreedom.mutter.data.model.IssueLinkParams
import com.efreedom.mutter.data.model.LetterPayloadDto
import com.efreedom.mutter.data.model.OpenLinkParams
import com.efreedom.mutter.data.model.RpcDeliveryLinkRow
import com.efreedom.mutter.data.model.TokenParam
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.data.support.TokenGenerator
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.LetterPayload
import com.efreedom.mutter.domain.repository.DeliveryRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import javax.inject.Inject

/** iOS DeliveryRepository 이식. issue_link/revoke_link/get_letter_by_token RPC + delivery_links 조회. */
class DeliveryRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : DeliveryRepository {

    private val client get() = provider.client

    override suspend fun issue(letterId: String, password: String?, revealAt: Instant?): DeliveryLink = wrap {
        val rows = client.postgrest.rpc(
            "issue_link",
            IssueLinkParams(
                letterId = letterId,
                token = TokenGenerator.make(),
                password = password,
                expiresAt = null,
                revealAt = revealAt?.toString(),
            ),
        ).decodeList<RpcDeliveryLinkRow>()
        rows.firstOrNull()?.toDomain() ?: throw MutterError.Server("링크 발급 응답이 비어 있어요.")
    }

    override suspend fun revoke(token: String) = wrap {
        client.postgrest.rpc("revoke_link", TokenParam(token = token))
        Unit
    }

    override suspend fun links(letterId: String): List<DeliveryLink> = wrap {
        client.postgrest.from("delivery_links")
            .select(Columns.list("letter_id", "token", "has_password", "expires_at", "reveal_at", "revoked")) {
                filter { eq("letter_id", letterId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<DeliveryLinkRow>()
            .map { it.toDomain() }
    }

    override suspend fun open(token: String, password: String?): LetterPayload = wrap {
        // get_letter_by_token은 `returns json`(단일 객체)이다. decodeSingle은 decodeList().first()라
        // 배열을 기대하므로 `{...}` 응답에서 역직렬화 예외가 나 편지가 안 열린다 → decodeAs를 쓴다.
        client.postgrest.rpc(
            "get_letter_by_token",
            OpenLinkParams(token = token, password = password),
        ).decodeAs<LetterPayloadDto>().toDomain()
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
