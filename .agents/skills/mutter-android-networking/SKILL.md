---
name: mutter-android-networking
description: Mutter Android에서 Supabase(supabase-kt)를 호출하는 코드를 다룰 때 반드시 참조한다. 트리거 — (1) postgrest rpc/from·functions.invoke·auth 호출 추가·수정, (2) Repository 구현(wrap{}), (3) RPC 파라미터 DTO·응답 DTO·toDomain() 작성, (4) 서버 에러 코드 → MutterError 정규화(SupabaseErrorMapper), (5) 세션·401·OAuth 콜백, (6) 사용자가 "RPC 붙여줘/Supabase 연동/PGRST202/디코드 실패" 라고 말하는 경우.
---

# Mutter Android Networking (supabase-kt)

백엔드는 iOS·웹(`~/Desktop/Code/letter-app`)과 같은 Supabase 프로젝트다. **RPC 시그니처의 정본은 `letter-app/supabase/migrations`** — 인자명·default 유무·반환 컬럼명을 거기서 확인하고 코드를 쓴다. 서버 계약을 바꾸면 iOS `Mutter`와 웹도 같이 맞춘다.

## SupabaseProvider (`:core:network`)

- `SupabaseProvider(context, url, anonKey)` — Auth·Postgrest·Functions·Storage 설치. Hilt `NetworkModule`이 `BuildConfig.SUPABASE_URL/ANON_KEY`(gitignore된 `secrets.properties` → `:data` BuildConfig)로 만든다.
- 세션: `SettingsSessionManager(SharedPreferencesSettings("mutter_supabase_auth"))`, autoRefresh/autoLoad/autoSave.
- OAuth 리다이렉트 `mutter://login-callback`. 콜백은 자체 `handleOAuthCallback(intent, onSession)`으로 처리한다 — supabase-kt `handleDeeplinks`는 크래시가 나서 쓰지 않는다.

## Repository 구현

```kotlin
class InboxRepositoryImpl @Inject constructor(private val provider: SupabaseProvider) : InboxRepository {
    private val client get() = provider.client

    override suspend fun myInbox(): List<InboxItem> = wrap {
        client.postgrest.rpc("get_my_inbox").decodeList<InboxRow>().map { it.toDomain() }
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
```

- 모든 호출을 `wrap {}`으로 감싼다.
- 단일 `json` 객체를 반환하는 RPC는 `.decodeAs<T>()`(`decodeSingle` 아님 — `get_letter_by_token`).
- Edge Function은 `client.functions.invoke(...)`(`AuthRepositoryImpl`·`LetterPhotoRepositoryImpl`).
- 테이블 직접 접근(`from`)은 RLS 보호 테이블(`inbox` 삭제 등)에만.

## RPC 파라미터 DTO — `= null` 기본값 금지 (하드 제약)

```kotlin
@Serializable
data class OpenLinkParams(
    @SerialName("p_token") val token: String,
    @SerialName("p_password") val password: String?,   // ✅ 기본값 없음 → 호출부가 null도 명시
)
```

supabase-kt 직렬화기는 `encodeDefaults=false`라 값이 기본값(null)과 같으면 키를 **생략**한다. PostgREST는 넘어온 인자명 집합으로 함수를 찾으므로, 서버에 그 인자의 default가 없으면 `PGRST202`로 호출 전체가 실패한다(2026-07-22: 링크 발급·편지 열기·신고가 전부 실패한 BLOCKER).
반대로 **응답** DTO의 nullable 필드는 `= null` 기본값을 둔다(누락 필드 관용).

응답 DTO의 `@SerialName`은 마이그레이션의 `returns table` 컬럼명과 정확히 맞춘다(`get_my_letters_with_status`는 `id`를 반환 — `letter_id`로 디코드해 홈이 통째로 실패한 사고).

## 에러 정규화 — `data/.../support/SupabaseErrorMapper.kt`

| 조건 | MutterError |
|---|---|
| cause 체인에 `IOException` | `Network` |
| 세션 만료 마커 | `Unauthorized` + `SessionInvalidation.notifyUnauthorized()` → `AppViewModel`이 로그아웃·SIGNED_OUT |
| `NOT_YET_REVEALED:<iso>` | `LinkNotYetRevealed(revealAt)` |
| `WRONG_PASSWORD`·`LINK_REVOKED`·`LINK_EXPIRED`·`NOT_CONNECTED`·`INVITE_ALREADY_USED` | 같은 이름 케이스 |
| `*NOT_FOUND` / `FORBIDDEN` | `NotFound` / `Unauthorized` |
| 그 외 | `Unknown` |

`MutterError`(`:domain`)는 `sealed class MutterError(open val userMessage: String) : Exception()`. 새 서버 코드는 케이스·한국어 문구·매퍼를 같이 추가하고 iOS `SupabaseErrorMapper.swift`도 맞춘다.

## 자가 점검

- 시그니처를 `letter-app/supabase/migrations`에서 확인했다(인자 default·반환 컬럼명).
- RPC 파라미터 DTO에 `= null` 기본값이 없다.
- 모든 호출이 `wrap {}`을 거친다.
- 단일 json 반환 RPC에 `decodeAs`를 썼다.
- 실제 호출(에뮬레이터·로그)로 동작을 확인했다 — 컴파일 통과만으로 RPC가 맞다고 하지 않는다.
