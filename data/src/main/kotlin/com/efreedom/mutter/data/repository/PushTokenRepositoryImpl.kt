package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.UpsertPushTokenParams
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.repository.PushTokenRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject

/** iOS PushTokenRepository 이식. upsert_push_token RPC. */
class PushTokenRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : PushTokenRepository {

    private val client get() = provider.client

    override suspend fun upsert(token: String, platform: String, deviceId: String?) = wrap {
        client.postgrest.rpc(
            "upsert_push_token",
            UpsertPushTokenParams(token = token, platform = platform, deviceId = deviceId),
        )
        Unit
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
