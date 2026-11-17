package com.efreedom.mutter.domain.analytics

/**
 * 제품 분석(GA4) 기록 — iOS·웹과 같은 이름으로 보낸다. 계약: Mutter `docs/specs/2026-11-16-analytics.md`.
 *
 * - 기록은 fire-and-forget. 실패해도 앱 동작에 영향이 없어야 한다(구현이 예외를 삼킨다).
 * - 개인정보(이메일·닉네임·편지 제목/본문·링크 토큰·사용자 ID)는 보내지 않는다 — 파라미터는 [AnalyticsEvent]가 정한 값뿐이다.
 * - feature는 이 인터페이스만 주입받는다. Firebase 구현은 `:data`에 있다.
 */
interface Analytics {
    fun log(event: AnalyticsEvent)

    /** 화면 이름으로 `screen_view`를 보낸다(SDK 자동 화면 기록은 매니페스트에서 끈다). */
    fun screen(screen: AnalyticsScreen)

    /** 연결 전(프리뷰·테스트·수동 생성) 기본값 — 아무것도 보내지 않는다. */
    object NoOp : Analytics {
        override fun log(event: AnalyticsEvent) = Unit
        override fun screen(screen: AnalyticsScreen) = Unit
    }
}

/** `screen_view.screen_name` 값 — 계약의 화면 이름 표 그대로. */
enum class AnalyticsScreen(val value: String) {
    SIGN_IN("sign_in"),
    ONBOARD_NICKNAME("onboard_nickname"),
    HOME("home"),
    INBOX("inbox"),
    THREADS("threads"),
    CONNECTIONS("connections"),
    PROFILE("profile"),
    COMPOSE("compose"),
    VIEWER("viewer"),
    DELIVERY("delivery"),
    CONNECT_INVITE("connect_invite"),
    TRASH("trash"),
    INBOX_TRASH("inbox_trash"),
    TICKETS("tickets"),
    TICKET_CHECKOUT("ticket_checkout"),
    LEGAL_TERMS("legal_terms"),
    LEGAL_PRIVACY("legal_privacy"),
    LEGAL_TAKEDOWN("legal_takedown"),
}

/** `login.method` 값. */
enum class LoginMethod(val value: String) {
    PASSWORD("password"),
    CODE("code"),
    APPLE("apple"),
    GOOGLE("google"),
    KAKAO("kakao"),
}

/** `letter_open.source` 값. */
enum class LetterOpenSource(val value: String) {
    LINK("link"),
    MY_LETTER("my_letter"),
}

/**
 * 계약의 이벤트 표. bool 파라미터는 GA4가 bool을 받지 않는 플랫폼이 있어 `"true"`/`"false"` 문자열로 보낸다.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, String> = emptyMap()) {

    /** 로그인 성공(가입 포함). */
    class Login(method: LoginMethod) : AnalyticsEvent("login", mapOf("method" to method.value))

    /** 이메일 회원가입 코드 인증 성공. */
    data object SignUpEmail : AnalyticsEvent("sign_up", mapOf("method" to "email"))

    /** 가입 후 닉네임 저장 성공. */
    data object OnboardComplete : AnalyticsEvent("onboard_complete")

    /** 새 편지 첫 저장 성공(이후 저장은 갱신이라 안 보냄). */
    class LetterCreate(reply: Boolean) : AnalyticsEvent("letter_create", mapOf("reply" to reply.toString()))

    /** 전달 링크 발급 성공(작성 시트·링크 관리). */
    class LinkIssue(permanent: Boolean, hasPassword: Boolean) : AnalyticsEvent(
        "link_issue",
        mapOf("permanent" to permanent.toString(), "has_password" to hasPassword.toString()),
    )

    /** 연결 상대에게 직접 발송 성공. */
    class LetterSend(permanent: Boolean, reply: Boolean) : AnalyticsEvent(
        "letter_send",
        mapOf("permanent" to permanent.toString(), "reply" to reply.toString()),
    )

    /** 편지 본문 표시 성공. */
    class LetterOpen(source: LetterOpenSource) : AnalyticsEvent("letter_open", mapOf("source" to source.value))

    /** 받은 사람이 편지권으로 간직 성공. */
    data object LetterKeep : AnalyticsEvent("letter_keep")

    /** 보낸 편지를 나중에 영구로 만들기 성공. */
    data object LetterMakePermanent : AnalyticsEvent("letter_make_permanent")

    /** 편지권 구매 성공(서버 반영까지 끝난 뒤). */
    class TicketPurchase(productId: String) : AnalyticsEvent("ticket_purchase", mapOf("product_id" to productId))

    /** 연결 초대 링크 만들기 성공. */
    data object InviteCreate : AnalyticsEvent("invite_create")
}
