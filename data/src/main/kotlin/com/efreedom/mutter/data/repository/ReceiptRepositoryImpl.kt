package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.LetterOpenRow
import com.efreedom.mutter.data.model.TokenParam
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.model.LetterOpenSummary
import com.efreedom.mutter.domain.repository.ReceiptRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject

/** iOS ReceiptRepository 이식. record_letter_open / get_my_letter_opens RPC. */
class ReceiptRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : ReceiptRepository {

    private val client get() = provider.client

    override suspend fun recordOpen(token: String) = wrap {
        client.postgrest.rpc("record_letter_open", TokenParam(token = token))
        Unit
    }

    override suspend fun myLetterOpens(): List<LetterOpenSummary> = wrap {
        client.postgrest.rpc("get_my_letter_opens")
            .decodeList<LetterOpenRow>()
            .map { it.toDomain() }
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
