package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Session
import com.efreedom.mutter.domain.model.SocialProvider
import com.efreedom.mutter.domain.repository.AuthRepository
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** iOS AuthRepository 이식. 이메일 OTP·비밀번호·Apple(IDToken)은 supabase-kt로,
 *  소셜(google/kakao)은 Edge Function(google-login/kakao-login)에 id_token을 넘겨 세션으로 교환. */
class AuthRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : AuthRepository {

    private val client get() = provider.client
    private val auth get() = provider.client.auth
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun requestCode(email: String) = wrap {
        auth.signInWith(OTP) {
            this.email = email
            createUser = true
        }
    }

    override suspend fun verifyCode(email: String, code: String): Session = wrap {
        auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = code)
        currentDomainSession() ?: throw MutterError.Unauthorized
    }

    override suspend fun signIn(email: String, password: String): Session = wrap {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        currentDomainSession() ?: throw MutterError.Unauthorized
    }

    override suspend fun signInApple(idToken: String, nonce: String): Session = wrap {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Apple
            this.nonce = nonce
        }
        currentDomainSession() ?: throw MutterError.Unauthorized
    }

    /**
     * 소셜 로그인 — provider SDK가 받은 id_token을 우리 Edge Function으로 보내
     * 서버가 검증·회원매핑 후 발급한 세션(access/refresh)으로 로그인 상태를 확립한다(iOS와 동일).
     * provider별 계정 분리를 위해 네이티브 OIDC가 아닌 Edge Function을 경유한다.
     */
    override suspend fun signInSocial(provider: SocialProvider, token: String): Session {
        val functionName = when (provider) {
            SocialProvider.GOOGLE -> "google-login"
            SocialProvider.KAKAO -> "kakao-login"
        }
        try {
            val response = client.functions.invoke(functionName, SocialLoginRequest(idToken = token))
            val raw = response.bodyAsText()
            if (!response.status.isSuccess()) throw mapEdgeError(raw, provider)
            val res = json.decodeFromString(SocialLoginResponse.serializer(), raw)
            // access/refresh로 세션 확립 — refresh로 완전한 세션을 받아 현재 세션으로 import(영구 저장).
            val session = auth.refreshSession(res.refreshToken)
            auth.importSession(session)
            return currentDomainSession() ?: throw MutterError.Unauthorized
        } catch (e: MutterError) {
            throw e
        } catch (e: Throwable) {
            throw SupabaseErrorMapper.map(e)
        }
    }

    private fun mapEdgeError(raw: String, provider: SocialProvider): MutterError {
        val code = runCatching { json.decodeFromString(EdgeError.serializer(), raw).error }.getOrNull()
        val label = if (provider == SocialProvider.KAKAO) "카카오" else "구글"
        return when (code) {
            "EMAIL_CONFLICT_DIFFERENT_PROVIDER", "EMAIL_CONFLICT_UNRESOLVED" ->
                MutterError.Server("이 이메일은 다른 로그인 방식으로 가입돼 있어요. 해당 방식으로 로그인해 주세요.")
            "EMAIL_UNAVAILABLE" ->
                MutterError.Server("$label 이메일 제공에 동의해야 로그인할 수 있어요.")
            // id_token 검증 실패(서명·발급자·aud·만료 불일치) 및 유효성 오류 — Edge Function 실제 코드에 정렬.
            "IDTOKEN_VERIFY_FAILED", "INVALID_TOKEN", "NO_ID_TOKEN", "MISSING_ID_TOKEN" ->
                MutterError.Server("$label 인증에 실패했어요. 다시 시도해 주세요.")
            "SERVER_MISCONFIGURED" ->
                MutterError.Server("$label 로그인 서버 설정에 문제가 있어요. 잠시 후 다시 시도해 주세요.")
            else ->
                MutterError.Server("$label 로그인에 실패했어요.")
        }
    }

    override suspend fun signOut() = wrap { auth.signOut() }

    override suspend fun currentSession(): Session? =
        runCatching { currentDomainSession() }.getOrNull()

    private fun currentDomainSession(): Session? =
        auth.currentUserOrNull()?.let { Session(userId = it.id, email = it.email) }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}

// ─── Edge Function DTO (iOS Request/Response와 동일 키) ───────────────────────

@Serializable
private data class SocialLoginRequest(val idToken: String)

@Serializable
private data class SocialLoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user_id") val userId: String? = null,
)

@Serializable
private data class EdgeError(val error: String? = null)
