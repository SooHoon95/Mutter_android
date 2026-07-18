package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.ProfileDto
import com.efreedom.mutter.data.model.ProfileUpsertDto
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Profile
import com.efreedom.mutter.domain.repository.ProfileRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.time.Instant
import javax.inject.Inject

/** iOS ProfileRepository 이식 — profiles 테이블 직접 + delete_my_account RPC. */
class ProfileRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : ProfileRepository {

    private val client get() = provider.client

    override suspend fun myProfile(): Profile? = wrap {
        client.postgrest.from("profiles")
            .select(Columns.list("id", "nickname")) {
                filter { eq("id", uid()) }
                limit(1)
            }
            .decodeList<ProfileDto>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun updateNickname(nickname: String) = wrap {
        client.postgrest.from("profiles")
            .upsert(
                ProfileUpsertDto(id = uid(), nickname = nickname, updatedAt = Instant.now().toString()),
            ) { onConflict = "id" }
        Unit
    }

    override suspend fun deleteAccount() = wrap {
        client.postgrest.rpc("delete_my_account")
        Unit
    }

    private fun uid(): String = client.auth.currentUserOrNull()?.id ?: throw MutterError.Unauthorized

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
