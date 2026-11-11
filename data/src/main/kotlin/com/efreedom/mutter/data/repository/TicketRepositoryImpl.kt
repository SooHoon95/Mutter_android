package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.EntitlementsDto
import com.efreedom.mutter.data.model.TicketHistoryRow
import com.efreedom.mutter.data.model.VerifyPurchaseRequest
import com.efreedom.mutter.data.model.VerifyPurchaseResponse
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.TicketHistoryItem
import com.efreedom.mutter.domain.repository.TicketRepository
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** 편지권 RPC(get_my_entitlements·get_my_ticket_history) + Edge Function verify-purchase. */
class TicketRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : TicketRepository {

    private val client get() = provider.client
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun entitlements(): Entitlements = wrap {
        client.postgrest.rpc("get_my_entitlements").decodeAs<EntitlementsDto>().toDomain()
    }

    override suspend fun history(): List<TicketHistoryItem> = wrap {
        client.postgrest.rpc("get_my_ticket_history").decodeList<TicketHistoryRow>().map { it.toDomain() }
    }

    override suspend fun verifyPurchase(productId: String, purchaseToken: String): Int = wrap {
        // 200만 여기까지 온다(granted false = 이미 지급, 역시 성공). 403 not_tester·400·501·500은 throw —
        // 호출부는 거래를 소비하지 않고 남겨 둔다.
        val response = client.functions.invoke(
            VERIFY_FUNCTION,
            VerifyPurchaseRequest(platform = "android", productId = productId, purchaseToken = purchaseToken),
        )
        json.decodeFromString(VerifyPurchaseResponse.serializer(), response.bodyAsText()).tickets
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }

    private companion object {
        const val VERIFY_FUNCTION = "verify-purchase"
    }
}
