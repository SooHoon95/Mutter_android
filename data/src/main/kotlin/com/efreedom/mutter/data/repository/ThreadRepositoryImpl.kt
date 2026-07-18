package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.CounterpartRow
import com.efreedom.mutter.data.model.SentWithRecipientRow
import com.efreedom.mutter.data.model.ThreadLetterRow
import com.efreedom.mutter.data.model.ThreadParams
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.model.Counterpart
import com.efreedom.mutter.domain.model.SentLetterSummary
import com.efreedom.mutter.domain.model.ThreadLetter
import com.efreedom.mutter.domain.repository.ThreadRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject

/** iOS ThreadRepository 이식. get_counterparts / get_thread / get_my_sent_with_recipients RPC. */
class ThreadRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : ThreadRepository {

    private val client get() = provider.client

    override suspend fun counterparts(): List<Counterpart> = wrap {
        client.postgrest.rpc("get_counterparts")
            .decodeList<CounterpartRow>()
            .map { it.toDomain() }
    }

    override suspend fun thread(counterpartId: String): List<ThreadLetter> = wrap {
        client.postgrest.rpc("get_thread", ThreadParams(counterpart = counterpartId))
            .decodeList<ThreadLetterRow>()
            .map { it.toDomain() }
    }

    override suspend fun sentWithRecipients(): List<SentLetterSummary> = wrap {
        client.postgrest.rpc("get_my_sent_with_recipients")
            .decodeList<SentWithRecipientRow>()
            .map { it.toDomain() }
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
