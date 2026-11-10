---
name: mutter-android-conventions
description: Mutter Android의 프로젝트 메타·서비스 컨셉, 빌드 시스템(Gradle·JDK17·Android SDK·버전 카탈로그·secrets.properties·google-services.json), 에뮬레이터·adb·Maestro 실행, 파일·네이밍·주석 규칙, 커밋 메시지 컨벤션, 기술 스택, 빌드 실패 트러블슈팅을 다룬다. 새 Kotlin 파일 작성, 의존성 추가, ./gradlew 빌드·테스트, 앱 실행·스크린샷, 빌드 에러 분석, 커밋 작업 시 참조한다.
---

# Mutter Android Conventions

## 프로젝트 메타

- 앱: Mutter(뮤터) · `applicationId`/namespace `com.efreedom.mutter` · minSdk 26 · app compile/targetSdk 36(라이브러리 모듈 compileSdk 35)
- Kotlin 2.0.21 · AGP 8.9.3 · Gradle wrapper 8.11.1 · Compose BOM 2024.12.01 · Hilt 2.52 · supabase-kt 3.1.4 · Media3 1.5.1
- 버전 카탈로그 `gradle/libs.versions.toml`. 새 라이브러리는 카탈로그에 추가하고 `libs.x`로 참조한다(`multiplatform-settings` 하드코딩은 예외로 남아 있음).
- 원격 `git@github.com:SooHoon95/Mutter_android.git`, 단일 브랜치 `main`.

## 서비스 컨셉

"연출되는 편지" — 테마 본문 + 음악 1곡(자동재생) + 사진(최대 5장). 웹앱(`letter-app`)과 iOS 앱(`Mutter`)이 원본이고, 이 앱은 iOS 기능 패리티를 따라간다. 수신자는 무설치 웹으로 연다. 기능을 추가·수정하면 iOS 대응 동작과 맞는지 확인한다.

## 빌드 툴체인

- **JDK 17 필수, `./gradlew`는 항상 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`을 붙여 실행한다.** `gradle.properties`의 `org.gradle.java.home`은 Gradle 데몬에만 적용되고 wrapper 스크립트 자체는 `JAVA_HOME`/PATH의 java로 뜬다. 비대화형 셸(Claude Bash)엔 java가 없어 맨 `./gradlew`는 "Unable to locate a Java Runtime"으로 실패하고, 시스템 기본 JDK 26이 잡히면 Gradle 8.11이 거부한다(2026-10-08 확인).
- Android SDK: `/opt/homebrew/share/android-commandlinetools`(`local.properties`의 `sdk.dir`, gitignore). 에뮬레이터는 반드시 `ANDROID_SDK_ROOT`를 이 경로로 두고 실행한다(`~/Library/Android/sdk`엔 system-image가 없어 "Broken AVD system path").
- 비밀값: `secrets.properties`(gitignore, 키 목록은 `secrets.properties.template`) — SUPABASE_URL·SUPABASE_ANON_KEY·KAKAO_NATIVE_APP_KEY·GOOGLE_WEB_CLIENT_ID·RELEASE_STORE_*. `app/google-services.json`(gitignore)이 없으면 google-services 플러그인이 빌드를 실패시킨다. 키스토어는 `/keystore/`(gitignore).

| 목적 | 명령 |
|---|---|
| 디버그 빌드 | `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` |
| 컴파일만 빠르게 | `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin` |
| 단위 테스트 | `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :domain:test :data:testDebugUnitTest` |
| 설치 | `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:installDebug` |
| 릴리스 번들 | `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew clean :app:bundleRelease --no-daemon`(서명 필요, `mutter-android-ci-cd`) |
| 의존성 트리 | `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :feature:inbox:dependencies --configuration debugRuntimeClasspath` |

- **빌드 결과는 출력으로 판정한다.** `| tail` 파이프의 종료코드는 tail 것이다. `BUILD SUCCESSFUL` / `BUILD FAILED`를 확인한다.
- 릴리스는 R8(minify + shrinkResources). kotlinx.serialization·Kakao(Retrofit 시그니처) keep 규칙이 `app/proguard-rules.pro`에 있다 — 리플렉션·직렬화 타입을 추가하면 릴리스 빌드로 확인한다.

## 실행·런타임 확인

```bash
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
export PATH="$PATH:$ANDROID_SDK_ROOT/platform-tools"   # 비대화형 셸엔 adb가 PATH에 없다
nohup $ANDROID_SDK_ROOT/emulator/emulator -avd mutter -no-snapshot-load >emu.log 2>&1 &
adb shell getprop sys.boot_completed      # 1이면 부팅 완료
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:installDebug && adb shell am start -n com.efreedom.mutter/.MainActivity
adb exec-out screencap -p > shot.png      # 화면 확인
adb logcat -d | grep -i mutter            # 로그
```

- Maestro(`maestro/`): iOS와 공용 플로우(`smoke.yaml`·`auth.yaml`·`qa-tabs.yaml`). `JAVA_HOME=/opt/homebrew/opt/openjdk@17 maestro --device emulator-5554 test maestro/smoke.yaml`. 양 플랫폼에서 같은 문구만 assert한다.
- 온스크린 키보드가 접힌 막대로 뜨면 콜드 부팅(`-no-snapshot-load`).

## 팀 작업 규칙

1. 파일 하나에 공개 타입 하나가 기본. 예외: `Repositories.kt`·`UseCases.kt`(도메인 인터페이스·UseCase 모음), ViewModel 파일 상단의 `XUiState`, DTO 파일의 Row·Params.
2. 화면은 `XScreen.kt` + `XViewModel.kt`, 패키지 `com.efreedom.mutter.feature.<name>`.
3. feature 간 직접 의존 금지(`mutter-android-architecture`).
4. 코루틴(`suspend`·`viewModelScope`). 콜백 API를 새로 만들지 않는다.
5. 색·글꼴은 `:uicomponent` 토큰으로만.
6. 분류 enum은 `Type` 접미사, Domain에 같은 enum이 있으면 재활용.
7. 코드 스타일 `kotlin.code.style=official`. ktlint·detekt는 없다 — 주변 코드 스타일을 따른다.

## 주석

"왜"를 쓴다. iOS 대응 클래스는 KDoc에 `iOS X 대응`을 남긴다. 주석 처리된 dead code는 커밋하지 않는다. 처음 도입하는 API는 `mutter-android-learning-comments` 예외.

## 커밋 메시지

```
{feat|fix|refactor|style|test|docs|build|chore|ci}(선택 scope): {한국어 요약} — {부연}
```

예: `feat: 인앱 SoundCloud 곡 선택 + 편지 사진 — iOS 패리티`, `chore: 1.3.0(5) 버전업 + fastlane(…)`.

- 목적이 다른 변경은 커밋을 나눈다.
- 파일을 개별 지정해 stage하고 재확인한다. `secrets.properties`·`local.properties`·`*.jks`·`google-services.json`은 절대 포함하지 않는다.
- Co-Authored-By를 넣지 않는다.

## 빌드 실패 트러블슈팅

1. `Unable to locate a Java Runtime` / `Unsupported class file major version` — `JAVA_HOME=/opt/homebrew/opt/openjdk@17` 누락.
2. `SDK location not found` — `local.properties` `sdk.dir` 확인.
3. `File google-services.json is missing` — 로컬에 파일 배치(커밋 금지).
4. `Unresolved reference: BuildConfig.X` — `secrets.properties` 키 누락 또는 모듈 `buildFeatures { buildConfig = true }`.
5. Hilt `MissingBinding` — `RepositoryModule` `@Binds` 누락.
6. `Serializer for class … not found` — `@Serializable` 또는 serialization 플러그인 누락, 릴리스에서만이면 proguard keep.
7. 런타임 `PGRST202` — RPC 파라미터 DTO의 `= null` 기본값(`mutter-android-networking`).
