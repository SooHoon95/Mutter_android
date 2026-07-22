package com.efreedom.mutter.data.repository

import com.efreedom.mutter.domain.model.ScValidation
import com.efreedom.mutter.domain.model.ScValidationFailReason
import com.efreedom.mutter.domain.repository.SoundCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

/**
 * iOS SoundCloudRepository 이식.
 * SoundCloud oEmbed 엔드포인트 검증 — 예외 없이 ScValidation 반환.
 * Supabase 미사용. HttpURLConnection, Dispatchers.IO.
 */
class SoundCloudRepositoryImpl @Inject constructor() : SoundCloudRepository {

    override suspend fun validate(url: String): ScValidation = withContext(Dispatchers.IO) {
        // 호스트 정확 매칭 — contains는 evilsoundcloud.com / soundcloud.com.evil.com 같은 위장 도메인을
        // 통과시킨다. iOS와 동일하게 정확히 soundcloud.com 또는 그 하위 도메인만 허용.
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase()
        val validHost = host == "soundcloud.com" || host?.endsWith(".soundcloud.com") == true
        if (!validHost) {
            return@withContext ScValidation.Fail(ScValidationFailReason.INVALID_URL)
        }
        try {
            fetchOembed(url)
        } catch (e: Throwable) {
            ScValidation.Fail(ScValidationFailReason.NETWORK)
        }
    }

    private fun fetchOembed(url: String): ScValidation {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val conn = URL("https://soundcloud.com/oembed?format=json&url=$encoded")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.requestMethod = "GET"
        return try {
            when (val code = conn.responseCode) {
                401, 403 -> ScValidation.Fail(ScValidationFailReason.PRIVATE_TRACK)
                200 -> parseOembed(conn.inputStream.bufferedReader().readText(), url)
                else -> ScValidation.Fail(ScValidationFailReason.NOT_FOUND)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseOembed(body: String, inputUrl: String): ScValidation {
        val html = extractJsonString(body, "html")
            ?: return ScValidation.Fail(ScValidationFailReason.EMBED_DISABLED)
        if (html.isBlank()) return ScValidation.Fail(ScValidationFailReason.EMBED_DISABLED)

        val title = extractJsonString(body, "title") ?: ""
        val author = extractJsonString(body, "author_name") ?: ""

        // iframe src의 url= 파라미터에서 canonical URL 추출. 파싱 실패 시 입력 URL로 폴백한다
        // (iOS 동작 — 200 + 정상 embed html이면 임베드 가능한 유효 트랙이므로 거부하지 않는다).
        val canonicalUrl = Regex("""src="([^"]+)"""").find(html)?.groupValues?.get(1)
            ?.let { src -> Regex("""[?&]url=([^&"]+)""").find(src)?.groupValues?.get(1) }
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?: inputUrl

        return ScValidation.Ok(title = title, author = author, canonicalUrl = canonicalUrl)
    }

    /** JSON 문자열 필드 추출(간단 정규식, 외부 파서 미사용). */
    private fun extractJsonString(json: String, field: String): String? =
        Regex(""""$field"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.replace("\\n", "\n")
            ?.replace("\\r", "\r")
            ?.replace("\\t", "\t")
}
