package com.efreedom.mutter.core.navigation

/**
 * 결제 안내 시트가 무엇을 위해 뜨는지 — 문구와 구매 뒤에 이어서 할 일이 달라진다(iOS TicketCheckoutRoute 대응).
 * 편지권이 없는 계정이 편지권이 필요한 동작을 눌렀을 때만 뜬다(있으면 그 자리에서 진행한다). 계약 "결제 안내 시트(앱)".
 *
 * 화면(push) 라우트가 아니라 보던 화면 위에 올라오는 시트라 [FeatureRoute]와 따로 둔다.
 * feature끼리는 의존할 수 없어, 호출하는 화면은 이 값만 넘기고 시트는 `:app`이 `:feature:ticket`에서 만들어 주입한다.
 */
sealed interface TicketCheckoutRoute {
    /**
     * 보내는 사람, 아직 안 보낸 편지 — 보내기 전 선택에서 영구 편지를 골랐다. **구매만 한다.**
     * 편지권은 여기서 쓰지 않고, 링크를 만들거나 보낼 때 서버가 같은 트랜잭션에서 쓴다(`p_permanent`).
     */
    data object SendPermanent : TicketCheckoutRoute

    /** 보내는 사람, 이미 보낸 편지 — "영구 편지로 만들기". 구매하고 바로 쓴다. */
    data class MakePermanent(val letterId: String) : TicketCheckoutRoute

    /** 받는 사람 — 받은 편지를 간직한다(편지 끝·닫힘 예고·잠김 화면). 구매하고 바로 쓴다. [password]는 링크 암호(없으면 null). */
    data class KeepReceived(val token: String, val password: String?) : TicketCheckoutRoute
}
