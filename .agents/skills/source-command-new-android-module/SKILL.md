---
name: "source-command-new-android-module"
description: "Migrated source command `new-android-module`"
---

# source-command-new-android-module

Use this skill when the user asks to run the migrated source command `new-android-module`.

## Command Template

# new-android-module

새 feature/core 모듈을 만든다. 규칙 근거는 `mutter-android-architecture`. 새 feature 모듈은 구현 전에 사용자 확인을 받는다.

## 입력

- 모듈 경로(예: `:feature:search`, `:core:analytics`)와 역할 한 줄
- 의존할 모듈

## 절차

1. **템플릿 고르기** — 가장 비슷한 기존 모듈의 `build.gradle.kts`를 복사한다(convention plugin 없음).
   - feature: `feature/inbox/build.gradle.kts`
   - Hilt 없는 core: `core/navigation/build.gradle.kts`
2. **파일 생성**
   ```
   feature/<name>/
   ├── build.gradle.kts                        # namespace = "com.efreedom.mutter.feature.<name>"
   └── src/main/kotlin/com/efreedom/mutter/feature/<name>/
   ```
   AndroidManifest는 기존 모듈에 있을 때만 같이 만든다.
3. **등록** — `settings.gradle.kts`에 `include(":feature:<name>")`, `:app`이 쓰면 `app/build.gradle.kts`에 `implementation(project(":feature:<name>"))`.
4. **의존 방향 확인** — `/gradle-dep-check`. feature는 `:domain`·`:uicomponent`·`:core:navigation`만.
5. **빌드** — `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :feature:<name>:compileDebugKotlin` → `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug`.

## 체크리스트

- [ ] `namespace`가 경로와 일치
- [ ] compileSdk 35 / minSdk 26 / JVM 17 — 기존 라이브러리 모듈과 동일
- [ ] 금지 의존(feature→feature, feature→:data) 없음
- [ ] settings·app 등록, 빌드 통과
