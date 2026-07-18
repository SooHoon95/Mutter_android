package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.InboxRow
import com.efreedom.mutter.data.model.TokenParam
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.model.InboxItem
import com.efreedom.mutter.domain.repository.InboxRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject

/** iOS InboxRepository 이식. save_to_inbox / get_my_inbox RPC. */
class InboxRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : InboxRepository {

    private val client get() = provider.client

    override suspend fun save(token: String) = wrap {
        client.postgrest.rpc("save_to_inbox", TokenParam(token = token))
        Unit
    }

    override suspend fun myInbox(): List<InboxItem> = wrap {
        client.postgrest.rpc("get_my_inbox")
            .decodeList<InboxRow>()
            .map { it.toDomain() }
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
