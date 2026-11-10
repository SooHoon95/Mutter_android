---
name: "source-command-build-android"
description: "Migrated source command `build-android`"
---

# source-command-build-android

Use this skill when the user asks to run the migrated source command `build-android`.

## Command Template

# build-android

Android 앱을 빌드하고 에러/경고를 분석한다. 규칙 근거는 `mutter-android-conventions`.

에러 원인을 추측으로 단정하지 않는다. 에러 메시지를 먼저 읽고 관련 `build.gradle.kts`·소스를 확인한 뒤, 확인된 사실과 가설을 구분해 보고한다.

## 빌드

```bash
cd ~/Desktop/Code/Mutter_android
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug > /tmp/mutter-android-build.log 2>&1; echo "exit=$?"
grep -E "BUILD SUCCESSFUL|BUILD FAILED|^e: |error:" /tmp/mutter-android-build.log | head -40
```

- `JAVA_HOME`을 빼면 비대화형 셸에서 wrapper가 java를 못 찾는다(`gradle.properties`의 java.home은 데몬 전용).
- 성패는 파이프 종료코드가 아니라 `BUILD SUCCESSFUL`/`BUILD FAILED`로 판정한다.
- 특정 모듈만: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :feature:inbox:compileDebugKotlin`
- 단위 테스트: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :domain:test :data:testDebugUnitTest`
- 릴리스(R8) 확인: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:bundleRelease`(서명 값 필요)

## 에러 분석

```
[ERROR] {파일}:{라인}: {메시지}
```

| 에러 | 원인 | 대처 |
|------|------|------|
| `Unresolved reference` | import 또는 모듈 의존 누락 | 모듈 `build.gradle.kts`·의존 방향(`/gradle-dep-check`) 확인 |
| Hilt `MissingBinding` / `[Dagger/MissingBinding]` | `@Binds`·`@Provides` 누락 | `data/.../di/RepositoryModule.kt` |
| `Unable to locate a Java Runtime` / `Unsupported class file major version` | JAVA_HOME 누락·JDK 17 아님 | `JAVA_HOME=/opt/homebrew/opt/openjdk@17` 접두 |
| `File google-services.json is missing` | 로컬 파일 없음 | 파일 배치(커밋 금지) |
| `BuildConfig.X` 미해결 | `secrets.properties` 키 누락 | `secrets.properties.template` 대조 |
| KSP 에러 | Hilt/serialization 어노테이션 | 해당 클래스 어노테이션·플러그인 |

## 빌드 후

컴파일 통과는 검증이 아니다. 동작 확인이 필요하면 `mutter-android-conventions`의 "실행·런타임 확인"으로 에뮬레이터에 설치·실행해 로그·스크린샷을 본다.
