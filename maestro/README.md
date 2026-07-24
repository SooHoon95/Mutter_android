# Mutter QA — Maestro (iOS + Android 공용)

iOS 시뮬레이터와 Android 에뮬레이터를 **같은 flow**로 QA한다. 두 앱의 `appId`가 동일
(`com.efreedom.mutter`)해서 flow YAML 하나가 양쪽을 구동한다.

## 사전 준비
- Maestro 설치됨(`~/.maestro/bin/maestro`, v2.6.1 확인). Maestro는 **Java(JDK17)** 가 필요하다.
- 대상 기기가 켜져 있어야 함:
  - iOS: `xcrun simctl list devices booted` 로 UDID 확인(예: iPhone 17 Pro).
  - Android: `adb devices` 로 확인(예: `emulator-5554`).
- 앱이 기기에 설치돼 있어야 함(iOS는 Xcode 빌드·설치, Android는 `./gradlew :app:installDebug`).

## 환경 변수(매 실행 앞에)
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

## 실행
스크린샷은 **실행한 작업 디렉터리**에 저장되므로, 플랫폼별로 폴더를 나눠 실행하면 나란히 비교하기 좋다.
```bash
# Android
maestro --device emulator-5554 test maestro/smoke.yaml

# iOS (UDID는 본인 것으로)
maestro --device <SIM_UDID> test maestro/smoke.yaml
```

## Flow
- `smoke.yaml` — 앱 실행 → 스플래시 종료 대기 → 스크린샷. 인증 상태 무관, 항상 통과(설정 검증용).
- `auth.yaml` — 로그아웃 상태의 로그인 화면 검증(`"비밀번호로 로그인"` 노출) + 스크린샷.

## 크로스플랫폼 주의점(실측으로 확인된 것)
1. **Maestro는 JDK17 필요** — Java 없으면 "Unable to locate a Java Runtime".
2. **한국어 문구가 플랫폼 간 다름**(Android 하드코딩 vs iOS L10n). 공용 flow에선 **완전히 동일한 문구만** 단언할 것.
   확인된 차이: `인증코드 받기`(A) ↔ `코드 받기`(iOS), `인증코드`(A) ↔ `인증 코드`(iOS),
   `…로 계속하기`(A) ↔ `…로 계속`(iOS). 동일한 것: `이메일`, `비밀번호로 로그인`, `비밀번호`, `로그인`.
   → 견고한 셀렉터가 필요하면 **testTag(Android)/accessibilityIdentifier(iOS)** 를 양쪽 코드에 붙이는 게 정답(언어 독립).
3. **iOS 시뮬레이터 시스템 팝업**(예: "Apple 계정 확인")이 화면을 가릴 수 있다 → `tapOn: { text: "지금 안 함", optional: true }` 로 닫는다.

## 로그인 이후(메인 탭·작성·수신) E2E 확장
로그인은 소셜 OAuth/이메일 OTP라 자동화가 어렵다. 두 가지 길:
- **비밀번호 로그인 자동화**: 앱이 이메일+비밀번호 로그인을 지원하므로, 테스트 계정을 만들어
  `maestro test --env EMAIL=... --env PASSWORD=... login.yaml` 로 로그인시킬 수 있다.
  (단, 로그인 단계 전환 버튼 문구가 플랫폼마다 다르니 testTag를 붙이거나 플랫폼별 flow로 분기.)
- **이미 로그인된 세션**으로 실행: 세션이 영구 저장되므로, 수동 로그인 1회 후엔 메인 플로우 flow가 바로 돈다.
