package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.DuplicateLetterRequest
import com.efreedom.mutter.data.model.DuplicateLetterResponse
import com.efreedom.mutter.data.model.KeepReceivedLetterParams
import com.efreedom.mutter.data.model.LetterAccessStateRow
import com.efreedom.mutter.data.model.LetterCoverDto
import com.efreedom.mutter.data.model.LetterIdParam
import com.efreedom.mutter.data.model.LetterIdsParam
import com.efreedom.mutter.data.model.LetterKeepRequestRow
import com.efreedom.mutter.data.model.LetterReaderRow
import com.efreedom.mutter.data.model.LetterTokenParam
import com.efreedom.mutter.data.model.RequestKeepLetterParams
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.model.LetterAccessState
import com.efreedom.mutter.domain.model.LetterCover
import com.efreedom.mutter.domain.model.LetterKeepRequest
import com.efreedom.mutter.domain.model.LetterReader
import com.efreedom.mutter.domain.repository.LetterAccessRepository
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 영구 편지권 계약의 편지 쪽 RPC(get_letter_cover·get_letter_access_states·get_letter_readers·
 * make_letter_permanent·keep_received_letter·request_keep_letter·get_letter_keep_requests) + Edge Function duplicate-letter.
 */
class LetterAccessRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : LetterAccessRepository {

    private val client get() = provider.client
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun cover(token: String): LetterCover = wrap {
        // returns json(단일 객체) → decodeAs.
        client.postgrest.rpc("get_letter_cover", LetterTokenParam(token = token))
            .decodeAs<LetterCoverDto>()
            .toDomain()
    }

    override suspend fun accessStates(letterIds: List<String>): List<LetterAccessState> = wrap {
        client.postgrest.rpc("get_letter_access_states", LetterIdsParam(letterIds = letterIds))
            .decodeList<LetterAccessStateRow>()
            .map { it.toDomain() }
    }

    override suspend fun readers(letterId: String): List<LetterReader> = wrap {
        client.postgrest.rpc("get_letter_readers", LetterIdParam(letterId = letterId))
            .decodeList<LetterReaderRow>()
            .map { it.toDomain() }
    }

    override suspend fun makePermanent(letterId: String) = wrap {
        client.postgrest.rpc("make_letter_permanent", LetterIdParam(letterId = letterId))
        Unit
    }

    override suspend fun keepReceived(token: String, password: String?) = wrap {
        client.postgrest.rpc("keep_received_letter", KeepReceivedLetterParams(token = token, password = password))
        Unit
    }

    override suspend fun requestKeep(token: String, password: String?): Boolean = wrap {
        // returns boolean(스칼라) → decodeAs<Boolean>.
        client.postgrest.rpc("request_keep_letter", RequestKeepLetterParams(token = token, password = password))
            .decodeAs<Boolean>()
    }

    override suspend fun keepRequests(letterId: String): List<LetterKeepRequest> = wrap {
        client.postgrest.rpc("get_letter_keep_requests", LetterIdParam(letterId = letterId))
            .decodeList<LetterKeepRequestRow>()
            .map { it.toDomain() }
    }

    override suspend fun duplicate(letterId: String): String = wrap {
        // 비-2xx(401·404·500)는 Functions 플러그인이 RestException으로 던진다 → wrap이 정규화.
        val response = client.functions.invoke(DUPLICATE_FUNCTION, DuplicateLetterRequest(letterId = letterId))
        json.decodeFromString(DuplicateLetterResponse.serializer(), response.bodyAsText()).letterId
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }

    private companion object {
        const val DUPLICATE_FUNCTION = "duplicate-letter"
    }
}
