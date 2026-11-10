---
name: "source-command-arch-check"
description: "Migrated source command `arch-check`"
---

# source-command-arch-check

Use this skill when the user asks to run the migrated source command `arch-check`.

## Command Template

# arch-check

`mutter-android-architecture`·`mutter-android-compose`·`mutter-android-networking` 스킬 규칙으로 코드베이스 위반을 탐지한다.

검사 전 위 스킬과 대상 `build.gradle.kts`·소스를 먼저 읽고, 확인된 위반과 추정 위반(가설)을 구분해 보고한다. 아래 항목은 서로 독립이라 동시에 실행할 수 있다. `build/` 디렉터리는 제외한다.

## 검사 항목

### 1. 모듈 의존 방향
- `feature/*/build.gradle.kts`에 `project(":data")` 또는 다른 `project(":feature:…")` → 위반
- `domain/build.gradle.kts`에 android·compose·hilt 플러그인/의존 → 위반
- `uicomponent/build.gradle.kts`에 `:domain`·`:feature:*` → 위반
- 상세 그래프는 `/gradle-dep-check`

### 2. Domain 순수성
- `domain/src` 에서 `import android.`, `import androidx.`, `import dagger.` → 위반(`javax.inject`만 허용)

### 3. 레이어 우회
- `feature/` 에서 `SupabaseProvider`, `postgrest`, `.rpc(`, `io.github.jan.supabase` import → 위반(Repository 경유)
- `feature/` 에서 `*Dto`·`*Row`·`*Params` 타입 사용 → DTO 노출 위반
- Screen Composable이 `NavController`를 파라미터로 받음 → 경고(람다로 이동을 알린다)

### 4. Networking
- `data/src/main/**/model/*Dto.kt` 의 `*Param(s)` 클래스에서 `: String? = null` 같은 `= null` 기본값 → **CRITICAL**(PGRST202)
- `data/src/main/**/repository/*Impl.kt` 의 public 메서드가 `wrap {` 을 거치지 않음 → 위반
- 단일 json 반환 RPC에 `decodeSingle` → 경고(`decodeAs`)

### 5. ViewModel·UiState
- `@HiltViewModel` 없는 ViewModel, `MutableStateFlow`를 public으로 노출 → 위반
- `catch (_: Throwable) { }` 로 사용자 액션 실패를 삼킴 → 경고(조회의 빈 목록 정상 상태는 예외)

### 6. 디자인 시스템
- `feature/` 에서 `Color(0x` 하드코딩, `FontFamily.Default`/`Typeface` 직접 사용 → 경고(토큰 사용)

### 7. DI
- `data/.../repository/*RepositoryImpl.kt` 중 `RepositoryModule.kt`에 `@Binds`가 없는 것 → 위반

## 출력 형식

1단계 — 발견: 확신 낮은 항목까지 전부, 확인/추정 구분.
2단계 — 필터: `[CRITICAL]`/`[HIGH]`/`[MEDIUM]`/`[LOW]`로 우선순위. 각 항목에 `파일:라인`과 근거 스킬.

```
[CRITICAL][FAIL] RPC 파라미터 = null 기본값:
  - data/src/main/kotlin/.../model/DeliveryDto.kt:23
[PASS] feature → :data 의존 없음
```
