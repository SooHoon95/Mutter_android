package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.repository.LetterPhotoRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * 수신자 모드 요청. password는 `= null` 기본값을 두지 않는다 — encodeDefaults=false라 null이 생략되면
 * 함수가 `{ token }`만 받게 된다. 계약(`password: string | null`)대로 항상 키를 보낸다.
 */
@Serializable
internal data class PhotoUrlsByTokenRequest(
    val token: String,
    val password: String?,
)

/** 소유자 모드 요청. 인증은 Functions 플러그인이 붙이는 사용자 JWT(Authorization)로 한다. */
@Serializable
internal data class PhotoUrlsByLetterRequest(
    val letterId: String,
)

/** `200 { urls: { <path>: <signedUrl> }, expiresIn }`. 응답 디코드용이라 누락 관용 기본값을 둔다. */
@Serializable
internal data class PhotoUrlsResponse(
    val urls: Map<String, String> = emptyMap(),
    val expiresIn: Int? = null,
)

/**
 * 편지 사진 Storage + 서명 URL(iOS LetterPhotoRepository 대응).
 * 버킷 `letter-photos`는 비공개 — RLS가 첫 폴더 = auth.uid()일 때만 insert·select·delete를 허용한다.
 */
class LetterPhotoRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : LetterPhotoRepository {

    private val client get() = provider.client
    private val bucket get() = client.storage.from(BUCKET)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun upload(letterId: String, jpeg: ByteArray): String = wrap {
        val path = "${uid()}/$letterId/${UUID.randomUUID().toString().lowercase()}.jpg"
        // upsert=false — RLS가 update를 허용하지 않고, 경로가 매번 새 uuid라 덮어쓸 일도 없다.
        bucket.upload(path, jpeg) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        path
    }

    override suspend fun delete(paths: Collection<String>) = wrap {
        if (paths.isNotEmpty()) bucket.delete(paths)
        Unit
    }

    override suspend fun deleteAll(letterId: String) = wrap {
        val folder = "${uid()}/$letterId"
        val paths = bucket.list(folder).map { "$folder/${it.name}" }
        if (paths.isNotEmpty()) bucket.delete(paths)
        Unit
    }

    override suspend fun signedUrls(token: String, password: String?): Map<String, String> =
        invokeUrls(PhotoUrlsByTokenRequest(token = token, password = password))

    override suspend fun signedUrls(letterId: String): Map<String, String> =
        invokeUrls(PhotoUrlsByLetterRequest(letterId = letterId))

    private suspend inline fun <reified T : Any> invokeUrls(request: T): Map<String, String> = wrap {
        // 비-2xx(403 등)는 Functions 플러그인이 RestException으로 던진다 → wrap이 MutterError로 정규화.
        val response = client.functions.invoke(FUNCTION, request)
        json.decodeFromString(PhotoUrlsResponse.serializer(), response.bodyAsText()).urls
    }

    private fun uid(): String = client.auth.currentUserOrNull()?.id ?: throw MutterError.Unauthorized

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }

    private companion object {
        const val BUCKET = "letter-photos"
        const val FUNCTION = "letter-photo-urls"
    }
}
