# Apple 로그인 (Android) — 설정 절차

작성 2026-07-26. 안드로이드는 Apple 네이티브 SDK가 없어 **브라우저 웹 OAuth**로 로그인한다.
iOS의 네이티브 Sign in with Apple과는 **완전히 별개의 콘솔 설정**이 필요하다.

## 0. iOS 설정으로는 왜 안 되는가

| | iOS (네이티브) | Android (웹 OAuth) |
|---|---|---|
| Apple 식별자 | **Bundle ID** (App ID) | **Services ID** (별도 생성) |
| client secret | 불필요 | **필수** (.p8로 서명한 ES256 JWT) |
| Supabase가 하는 일 | 앱이 받아온 id_token 검증만 | Apple과 직접 OAuth 왕복 |
| 앱 코드 경로 | `signInApple(idToken, nonce)` | `signInAppleWeb()` → Custom Tab |

Supabase의 Apple provider에 Bundle ID만 등록돼 있으면 iOS는 동작하지만 Android는 `invalid_client`로 실패한다.

## 1. 앱 코드 (완료됨 — 참고용)

| 파일 | 내용 |
|---|---|
| `core/network/.../SupabaseProvider.kt` | `OAUTH_REDIRECT_URL` 상수 + `handleOAuthCallback()` 자체 구현 |
| `data/.../AuthRepositoryImpl.kt` | `signInAppleWeb()` → `auth.signInWith(Apple, redirectUrl = OAUTH_REDIRECT_URL)` |
| `app/src/main/AndroidManifest.xml` | `<data android:scheme="mutter" />` intent-filter |
| `app/.../MainActivity.kt` | `handleOAuthCallback(intent)` → `oauthSessionBump` → `AppViewModel.refresh()` |

### supabase-kt의 `handleDeeplinks`를 쓰지 않는다

정상 토큰이 아닌 콜백에서 **앱이 죽기 때문이다.** 둘 다 에뮬레이터에서 재현 확인했다.

| 상황 | 무슨 일이 일어나는가 |
|---|---|
| 사용자가 Apple 화면에서 **취소** → `#error=access_denied` | `parseSessionFromFragment`가 "No access token found"로 **메인 스레드에서** throw → `FATAL EXCEPTION: main` |
| 토큰이 만료·위조됨 | `retrieveUser`가 supabase-kt 내부 `authScope`에서 throw. 이 스코프엔 `SupervisorJob`도 예외 핸들러도 없고, 그 시점엔 `handleDeeplinks`가 이미 리턴한 뒤라 **호출부 try/catch로도 못 잡는다** |

그래서 `handleOAuthCallback()`이 scheme/host 검사 → fragment 파싱 → `retrieveUser` → `importSession`을
직접 수행하고, 각 단계를 `runCatching`으로 감싼 뒤 **성공했을 때만** `onSession()`을 호출한다.
취소는 에러가 아니므로 조용히 삼키고 로그인 화면을 유지한다.

`AuthConfig`의 `scheme`/`host`는 **일부러 비워둔다.** 채우면 `defaultRedirectUrl()`이 딥링크로 바뀌어
`redirectUrl`을 생략한 호출(이메일 OTP 메일 링크 등)의 목적지까지 함께 바뀐다. 우리는 Apple 호출에만
`OAUTH_REDIRECT_URL`을 명시적으로 넘기므로 이 설정이 필요 없다.

## 2. Apple Developer 콘솔

### 2.1 Services ID 생성

[Certificates, Identifiers & Profiles → Identifiers](https://developer.apple.com/account/resources/identifiers/list) → `+` → **Services IDs**

| 항목 | 값 |
|---|---|
| Description | `Mutter Web Sign In` (자유) |
| Identifier | `com.efreedom.mutter.web` (Bundle ID와 **달라야** 함) |

생성 후 다시 열어 **Sign in with Apple** 체크 → `Configure`:

| 항목 | 값 |
|---|---|
| Primary App ID | 기존 iOS App ID (`com.efreedom.mutter` 등) |
| Domains and Subdomains | `aqotppfdrbxhthlqwwhp.supabase.co` |
| Return URLs | `https://aqotppfdrbxhthlqwwhp.supabase.co/auth/v1/callback` |

> Return URL은 **Supabase 콜백**이지 앱의 `mutter://` 주소가 아니다.
> Apple → Supabase → 앱 순서로 돌아온다.

### 2.2 키(.p8) 발급

[Keys](https://developer.apple.com/account/resources/authkeys/list) → `+` → **Sign in with Apple** 체크 → Configure에서 Primary App ID 지정 → 등록

다운로드되는 `AuthKey_XXXXXXXXXX.p8`은 **단 한 번만 받을 수 있다.** 안전한 곳에 보관(저장소에 커밋 금지).

기록해 둘 값 3가지:

| 값 | 위치 |
|---|---|
| **Team ID** | 우측 상단 계정 메뉴 / Membership 페이지 (10자) |
| **Key ID** | 방금 만든 키 상세 (10자) |
| **Services ID** | 2.1에서 정한 Identifier |

## 3. client secret JWT 생성

Supabase가 Apple에 보낼 `client_secret`은 .p8로 직접 서명한 JWT다. **최대 6개월 만료**라 갱신이 필요하다.

```bash
pip install "pyjwt[crypto]"
```

```python
# gen_apple_secret.py — 저장소에 커밋하지 말 것
import jwt, time

TEAM_ID     = "XXXXXXXXXX"                  # Apple Team ID
KEY_ID      = "YYYYYYYYYY"                  # .p8 키의 Key ID
SERVICES_ID = "com.efreedom.mutter.web"     # 2.1의 Identifier
P8_PATH     = "AuthKey_YYYYYYYYYY.p8"

now = int(time.time())
token = jwt.encode(
    {
        "iss": TEAM_ID,
        "iat": now,
        "exp": now + 15777000,              # Apple 허용 최대치(약 6개월)
        "aud": "https://appleid.apple.com",
        "sub": SERVICES_ID,                 # Bundle ID 아님 — Services ID
    },
    open(P8_PATH).read(),
    algorithm="ES256",
    headers={"kid": KEY_ID},
)
print(token)
```

`sub`에 Bundle ID를 넣는 실수가 가장 흔하다. **Services ID**여야 한다.

## 4. Supabase 대시보드

### 4.1 Authentication → Providers → Apple

| 항목 | 값 |
|---|---|
| Enable Sign in with Apple | 켜기 |
| Client IDs | `com.efreedom.mutter, com.efreedom.mutter.web` — **쉼표로 iOS Bundle ID와 Services ID 둘 다** |
| Secret Key (for OAuth) | 3에서 출력된 JWT |

Client IDs에서 기존 Bundle ID를 지우면 **iOS 로그인이 깨진다.** 반드시 추가만 한다.

### 4.2 Authentication → URL Configuration → Redirect URLs

`mutter://login-callback` 추가.

여기 없으면 Supabase가 `redirect_to`를 무시하고 Site URL로 보내버려서 앱으로 안 돌아온다.

## 5. 검증

### 5.1 콜백 처리만 따로 확인 (Apple 설정 없이 가능)

콘솔 설정 전에 앱 코드 경로만 검증할 수 있다. 유효한 토큰 쌍을 가진 딥링크를 직접 쏜다:

```bash
adb shell am start -a android.intent.action.VIEW \
  -d "mutter://login-callback#access_token=<JWT>&refresh_token=<RT>&expires_in=3600&token_type=bearer"
```

앱이 로그인 화면 → 메인(또는 닉네임)으로 넘어가면 `scheme`/`host` 수정이 먹은 것이다.

### 5.2 전체 플로우

1. 로그인 화면 → **Apple로 계속하기**
2. Custom Tab에서 Apple 로그인
3. 앱으로 복귀 → 신규면 닉네임 화면, 기존이면 메인

실패 시 브라우저에 뜬 에러 메시지를 먼저 본다:

| 증상 | 원인 |
|---|---|
| `invalid_client` | Services ID 미등록, 또는 secret JWT의 `sub`가 틀림 |
| `invalid_request` / redirect 불일치 | 2.1 Return URL 오타 |
| 브라우저는 성공했는데 앱이 로그인 화면 그대로 | 4.2 Redirect URLs 누락(Supabase가 Site URL로 보내버림) |
| Apple 화면 자체가 안 뜸 | Supabase Apple provider 비활성 |

## 6. 유지보수

- **secret JWT는 최대 6개월 만료.** 만료되면 Android Apple 로그인만 조용히 죽는다(iOS는 secret을 안 쓰므로 멀쩡). 3의 스크립트를 다시 돌려 4.1에 갱신한다. 캘린더 알림 권장.
- flowType은 `IMPLICIT`(supabase-kt 기본값)이다. 리다이렉트 fragment에 토큰이 평문으로 실려 오고 `mutter://` 스킴은 다른 앱도 선점할 수 있어, 이론상 토큰 탈취 벡터가 있다. PKCE로 바꾸면 해소되지만 비용이 셋이다 — (a) `flowType`은 전역 설정이라 이메일 OTP 메일 링크 형식도 같이 바뀌고, (b) 기본 `codeVerifierCache`가 no-arg Settings라 `SettingsSessionManager`처럼 SharedPreferences를 명시 주입해야 하며, (c) `handleOAuthCallback()`이 fragment 파싱 대신 `?code=` 쿼리를 읽어 `exchangeCodeForSession`을 호출하도록 바꿔야 한다. 별도 작업으로 다룬다.
- Apple 신규 가입자는 `auth.users` 행이 먼저 생기고 프로필이 없어 `NEEDS_NICKNAME`으로 간다. Google/Kakao의 Edge Function `checkOnly`(닉네임 전 가입 방지)와 경로가 다르지만 iOS와 동일한 의도된 설계다(`domain/.../Account.kt` 주석 참고).
