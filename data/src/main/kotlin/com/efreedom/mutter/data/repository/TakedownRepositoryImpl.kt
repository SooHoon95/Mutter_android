package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.TakedownParams
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.repository.TakedownRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject

/** iOS TakedownRepository 이식. report_takedown RPC. */
class TakedownRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : TakedownRepository {

    private val client get() = provider.client

    override suspend fun report(
        letterId: String?,
        trackRef: String?,
        claimant: String,
        contact: String,
        reason: String,
    ) = wrap {
        client.postgrest.rpc(
            "report_takedown",
            TakedownParams(
                letterId = letterId,
                trackRef = trackRef,
                claimant = claimant,
                contact = contact,
                reason = reason,
            ),
        )
        Unit
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
