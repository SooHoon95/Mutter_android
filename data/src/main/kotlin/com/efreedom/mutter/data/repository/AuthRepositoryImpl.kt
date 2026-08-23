package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.OAUTH_REDIRECT_URL
import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Session
import com.efreedom.mutter.domain.model.SocialAuthResult
import com.efreedom.mutter.domain.model.SocialProvider
import com.efreedom.mutter.domain.repository.AuthRepository
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.user.UserSession
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

    // Apple 웹 OAuth — 브라우저(Custom Tab)로 Supabase Apple provider 인증 흐름을 연다.
    // 세션은 리다이렉트(mutter://login-callback)를 MainActivity가 handleDeeplinks로 처리해 확립한다.
    // 사전조건(콘솔 작업): Apple Services ID·.p8 시크릿 + Supabase Apple provider + 리다이렉트 URL 허용.
    // iOS 네이티브(Bundle ID)와 별개 설정이다 — 절차는 docs/apple-signin-setup.md 참고.
    override suspend fun signInAppleWeb() = wrap {
        auth.signInWith(Apple, redirectUrl = OAUTH_REDIRECT_URL)
    }

    /**
     * 소셜 로그인 — provider SDK가 받은 id_token을 우리 Edge Function으로 보내
     * 서버가 검증·회원매핑 후 발급한 세션(access/refresh)으로 로그인 상태를 확립한다(iOS와 동일).
     * provider별 계정 분리를 위해 네이티브 OIDC가 아닌 Edge Function을 경유한다.
     */
    /**
     * 소셜 1단계 — idToken만 Edge Function으로 검증(checkOnly=true, 계정 생성 X).
     * 기존 회원이면 세션을 확립해 [SocialAuthResult.Authenticated], 신규면 [SocialAuthResult.NeedsNickname].
     * "닉네임 전 가입" 방지: 신규 회원은 여기서 아무 것도 만들지 않는다.
     */
    override suspend fun startSocial(provider: SocialProvider, idToken: String): SocialAuthResult {
        val res = invokeSocial(provider, SocialLoginRequest(idToken = idToken, checkOnly = true))
        val access = res.accessToken
        val refresh = res.refreshToken
        if (res.isNew || access == null || refresh == null) return SocialAuthResult.NeedsNickname
        establishSession(access, refresh, res.expiresAt)
        return SocialAuthResult.Authenticated(currentDomainSession() ?: throw MutterError.Unauthorized)
    }

    /** 소셜 2단계 — 신규 회원을 닉네임과 함께 가입(이때만 계정 생성)시키고 세션을 확립한다. */
    override suspend fun registerSocial(provider: SocialProvider, idToken: String, nickname: String): Session {
        val res = invokeSocial(provider, SocialLoginRequest(idToken = idToken, nickname = nickname))
        val access = res.accessToken ?: throw MutterError.Unauthorized
        val refresh = res.refreshToken ?: throw MutterError.Unauthorized
        establishSession(access, refresh, res.expiresAt)
        return currentDomainSession() ?: throw MutterError.Unauthorized
    }

    /** Edge Function(google-login/kakao-login) 호출 + 응답/에러 정규화. */
    private suspend fun invokeSocial(provider: SocialProvider, request: SocialLoginRequest): SocialLoginResponse {
        val functionName = when (provider) {
            SocialProvider.GOOGLE -> "google-login"
            SocialProvider.KAKAO -> "kakao-login"
        }
        try {
            val response = client.functions.invoke(functionName, request)
            val raw = response.bodyAsText()
            if (!response.status.isSuccess()) throw mapEdgeError(raw, provider)
            return json.decodeFromString(SocialLoginResponse.serializer(), raw)
        } catch (e: MutterError) {
            throw e
        } catch (e: Throwable) {
            throw SupabaseErrorMapper.map(e)
        }
    }

    /**
     * Edge Function이 발급한 세션(access/refresh)을 그대로 현재 세션으로 import(iOS setSession 대응, 영구 저장).
     *
     * refreshSession(추가 grant_type=refresh_token 교환)이나 expiresIn=0으로의 import는 supabase-kt가
     * 세션을 "이미 만료됨"으로 보고 즉시 refresh를 돌리게 해, 서버가 방금 발급한 토큰을 못 찾는
     * `refresh_token_not_found`로 실패한다(구글=매번, 카카오=간헐). 서버가 준 expires_at으로 미래 만료를
     * 채워 즉시 refresh를 막고, retrieveUser로 유저를 채워 currentUserOrNull()이 바로 유효하게 한다.
     */
    private suspend fun establishSession(accessToken: String, refreshToken: String, expiresAtEpoch: Long?) {
        val nowSec = System.currentTimeMillis() / 1000
        val expiresIn = expiresAtEpoch?.let { it - nowSec }?.takeIf { it > 0 } ?: 3600L
        val user = auth.retrieveUser(accessToken)
        val session = UserSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            tokenType = "bearer",
            user = user,
        )
        auth.importSession(session, autoRefresh = true)
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
        runCatching {
            // autoLoadFromStorage는 비동기다. 콜드스타트에서 저장된 세션 로드가 끝나기 전에 읽으면
            // currentUserOrNull()이 null을 돌려줘 로그인돼 있어도 SIGNED_OUT으로 튄다.
            // 초기화(스토리지 로드 + 최초 refresh)가 Authenticated/NotAuthenticated/RefreshError로
            // 확정될 때까지 대기한 뒤 읽어야 자동 로그인이 유지된다.
            auth.awaitInitialization()
            currentDomainSession()
        }.getOrNull()

    private fun currentDomainSession(): Session? =
        auth.currentUserOrNull()?.let { Session(userId = it.id, email = it.email) }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}

// ─── Edge Function DTO (iOS Request/Response와 동일 키) ───────────────────────

@Serializable
private data class SocialLoginRequest(
    val idToken: String,
    // checkOnly=true → 신규 여부만 확인(생성 X). nickname 동반 → 그때 가입. null 필드는 직렬화에서 생략.
    val checkOnly: Boolean? = null,
    val nickname: String? = null,
)

@Serializable
private data class SocialLoginResponse(
    // checkOnly-신규 응답은 { isNew:true }만 오므로 세션 토큰은 nullable.
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    // GoTrue 세션 만료(Unix 초). 세션 import 시 미래 만료로 채워 즉시 refresh를 막는 데 쓴다.
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val isNew: Boolean = false,
)

@Serializable
private data class EdgeError(val error: String? = null)
