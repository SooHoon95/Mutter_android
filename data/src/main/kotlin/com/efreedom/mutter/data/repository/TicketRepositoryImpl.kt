package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.EntitlementsDto
import com.efreedom.mutter.data.model.TicketHistoryRow
import com.efreedom.mutter.data.model.VerifyPurchaseRequest
import com.efreedom.mutter.data.model.VerifyPurchaseResponse
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.TicketHistoryItem
import com.efreedom.mutter.domain.repository.TicketRepository
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        // 200만 여기까지 온다(granted false = 이미 지급, 역시 성공). 403 not_tester·account_mismatch·400·501·500은
        // throw — 호출부는 거래를 소비하지 않고 남겨 둔다.
        val response = try {
            client.functions.invoke(
                VERIFY_FUNCTION,
                VerifyPurchaseRequest(platform = "android", productId = productId, purchaseToken = purchaseToken),
            )
        } catch (e: RestException) {
            // 다른 계정으로 산 거래 — 소비하지 않고 남겨 두면 구매한 계정으로 로그인했을 때 재전송으로 지급된다.
            if (e.statusCode == 403 && errorCode(e) == "account_mismatch") throw MutterError.PurchaseAccountMismatch
            // 결제 테스트 대상이 아닌 계정 — iOS StoreKitTicketPurchaser와 같은 판정(상태 코드 + 본문 error).
            if (e.statusCode == 403 && errorCode(e) == "not_tester") throw MutterError.PurchaseUnavailable
            throw e
        }
        json.decodeFromString(VerifyPurchaseResponse.serializer(), response.bodyAsText()).tickets
    }

    /** Functions 플러그인은 비-2xx 응답 본문(`{"error":"…"}`)을 RestException.error에 그대로 싣는다. */
    private fun errorCode(e: RestException): String? = runCatching {
        json.parseToJsonElement(e.error).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }

    private companion object {
        const val VERIFY_FUNCTION = "verify-purchase"
    }
}
