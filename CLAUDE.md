# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 따를 **큰 원칙**만 담는다.
구현 세부(아키텍처·Compose·네트워킹·컨벤션·배포)는 `.claude/skills/`의 프로젝트 스킬에서 필요한 시점에 로드해 참조한다.

프로젝트: **Mutter (뮤터) Android** — "연출되는 편지" 음악 편지 앱. 편지 = 테마 본문 + 음악 1곡(자동재생) + 사진. 웹앱(`~/Desktop/Code/letter-app`)·iOS 앱(`~/Desktop/Code/Mutter`)과 같은 Supabase 백엔드를 쓰고, **iOS 기능 패리티**를 따른다. 수신자는 무설치 웹으로 연다.
Kotlin + Jetpack Compose + Hilt + Clean 멀티모듈 · supabase-kt · applicationId `com.efreedom.mutter` · minSdk 26.

---

## Workflow Orchestration

### 1. Plan Node Default
- 사소하지 않은 작업(3단계 이상이거나 아키텍처 결정을 포함하는 작업)에는 **plan 모드로 진입**한다.
- 작업이 어긋나기 시작하면 **즉시 멈추고 재계획**한다 — 그냥 밀어붙이지 않는다.
- 빌드뿐 아니라 **검증 단계에도 plan 모드를 활용**한다.
- 모호함을 줄이기 위해 **상세 스펙을 먼저 글로 작성**한다.

### 2. Subagent Strategy
- 위임은 **크고 독립적이며 병렬 실행이 가능한 작업에만** 한다 — 여러 모듈을 넓게 훑는 탐색, 서로 겹치지 않는 리서치 갈래, 저장소가 다른 작업(Android·iOS·웹)이 그 예다. 몇 번의 tool call로 끝날 일은 직접 한다.
- **자기 결과 확인용 위임은 금지.** 방금 내가 쓴 것을 다른 에이전트에게 "봐 달라"고 넘기는 건 위임이 아니라 미루기다. 다시 봐야 하면 내가 다시 읽는다.
- **동시 스폰 수는 낮게 유지한다.** 갈래 수를 늘리는 대신 각 갈래를 좁게 정의한다. 서브에이전트 1개당 **하나의 방향**만.
- 백그라운드 위임 결과는 메인에 자동으로 오지 않을 수 있다 — 결론을 verbatim으로 보고하게 하거나 직접 읽는다.

### 3. Self-Improvement Loop
- 사용자에게 **교정받을 때마다** `tasks/lessons.md`에 해당 패턴을 기록한다.
- 같은 실수를 막을 **자기 규칙**을 직접 작성한다.
- 실수율이 떨어질 때까지 이 기록을 **집요하게 반복 개선**한다.
- 세션 시작 시 `tasks/lessons.md`를 **먼저 검토**한다.

### 4. Verification Before Done
- 동작을 **증명하지 않고는** 작업을 완료로 표시하지 않는다.
- **컴파일/빌드 통과는 검증이 아니다.** "검증"이라는 단어는 런타임 동작 확인(에뮬레이터 실행, `adb logcat`, 스크린샷, Maestro)에만 쓴다. 빌드만 돌렸으면 "컴파일 통과"라고만 말한다.
- 자동으로 확인할 수 없는 것(실기기 소리, 미세한 시각 차이 등)은 **"확인 불가 — 육안 필요"**로 정직하게 말한다.
- RPC 호출은 실제 호출로 확인한다 — 파라미터 직렬화 문제(`PGRST202`)는 컴파일로 안 잡힌다.
- **검증 강도는 변경 크기에 맞춘다.** 대부분은 내가 직접 실행·로그·시연으로 증명하면 끝난다. 별도 리뷰 lane(`code-reviewer`/`verifier`)은 **리스크가 크거나 스스로 판단이 갈릴 때만** 붙인다 — 모든 비사소 작업에 일괄로 강제하지 않는다. 이 항목이 `.claude/CLAUDE.md`(OMC)의 일괄 강제보다 우선한다.
- "**staff 엔지니어가 이 코드를 승인할까?**"를 스스로 묻고, 필요하면 main과 변경 동작을 diff로 비교한다.

### 5. Demand Elegance (Balanced)
- 사소하지 않은 변경에는 **"더 우아한 방법이 있는가?"**를 멈춰서 묻는다.
- 수정이 hacky하게 느껴지면 지금 알게 된 모든 것을 종합해 우아한 해법으로 다시 구현한다.
- 단순하고 명확한 수정에는 이 단계를 **건너뛴다** — 과한 엔지니어링 금지.

### 6. Autonomous Bug Fixing
- 버그 리포트를 받으면 **그냥 고친다**. 로그·에러·실패한 테스트를 가리키고 그걸 해결한다.
- 사용자에게 **컨텍스트 스위칭을 강요하지 않는다**.

### 7. Apply, Don't Suggest
- 해결 방법을 찾았으면 **그 자리에서 바로 적용**한다. "이렇게 바꾸면 됩니다"로 끝내지 않는다.
- 단순 수정은 직접 Edit으로 적용한다.
- 영향이 크거나 destructive한 변경(여러 모듈, 공용 컴포넌트 시그니처, 삭제, 서버 계약, 프로덕션 트랙 업로드)은 risk를 적시해 **한 번만 확인**한다.
- 변경 후 영향 받는 호출부까지 연쇄 적용한 뒤 보고한다.

---

## Task Management

1. **Plan First**: 작업 계획을 `tasks/todo.md`에 체크 가능한 항목으로 적는다.
2. **Verify Plan**: 새 feature 모듈 또는 3개 이상 모듈 변경이면 구현 전에 확인받는다.
3. **Track Progress**: 진행하며 항목을 완료 처리한다.
4. **Explain Changes**: 각 단계에서 변경 요약을 high-level로 설명한다.
5. **Document Results**: `tasks/todo.md`에 리뷰 섹션을 덧붙인다. 끝난 작업은 `tasks/archive/`로.
6. **Capture Lessons**: 교정받은 후에는 `tasks/lessons.md`를 갱신한다.

포맷·분량 규칙은 `mutter-android-harness-ops`.

---

## Core Principles

- **Concise**: 응답은 핵심 먼저, 부연은 뒤. 이모지·자화자찬 없이 결과·수치만. 사용자 응답은 한국어.
- **Simplicity First**: 모든 변경을 가능한 한 단순하게. 코드를 최소한으로만 건드린다.
- **No Laziness**: 근본 원인을 찾는다. 임시방편 금지. 시니어 개발자의 기준을 적용한다.
- **Minimal Impact**: 변경은 필요한 곳에만 닿는다. 새로운 버그를 만들지 않는다.
- **Evidence First**: 원인을 추측으로 단정하지 않는다. 관련 파일을 먼저 읽고, 확인된 사실과 가설을 구분해 보고한다. 동작이 애매하면 iOS 대응 코드를, 서버 계약은 `letter-app/supabase/migrations`를 근거로 한다.
- **No Unsolicited Tests**: 테스트 코드는 사용자가 명시적으로 요청할 때만 작성한다. 내 변경으로 기존 테스트 컴파일이 깨지면 최소 수정으로만 맞춘다.
- `build/`·`.gradle/`·`.kotlin/`(빌드 산출물·캐시)는 읽지 않는다.

---

## 프로젝트 스킬 (구현 세부 사항)

관련 작업이 시작되면 해당 스킬을 **참조한다(자동 로드 또는 명시 호출)**.

| 스킬 | 다룰 때 사용 |
|------|-------------|
| `mutter-android-architecture` | 모듈 구조·의존 방향, Domain 모델·Repository·UseCase, DTO 매핑, Screen→ViewModel→UseCase→Repository→SupabaseProvider 흐름, Hilt DI, 새 모듈 추가 |
| `mutter-android-compose` | Compose 화면 작성, @HiltViewModel·UiState, 에러 표시, type-safe 내비게이션(FeatureRoute·MutterNavHost·탭), 디자인 시스템 토큰·컴포넌트·드로어블, 문자열 리소스, WebView/ExoPlayer 수명 |
| `mutter-android-networking` | supabase-kt 호출(rpc/from/functions/auth), Repository `wrap{}`, RPC 파라미터 DTO(`= null` 금지), `SupabaseErrorMapper`·`MutterError`, 세션·OAuth. **서버 계약 변경 시 iOS·웹 동시 확인** |
| `mutter-android-conventions` | 프로젝트 메타, 빌드 툴체인(JDK 17·SDK·secrets·google-services), 에뮬레이터·adb·Maestro, 파일·네이밍·주석, 커밋, 빌드 트러블슈팅 |
| `mutter-android-ci-cd` | fastlane → Play(verify·bundle·internal·production), 서명·키, 버전업·changelogs, 트랙 반영 확인 |
| `mutter-android-learning-comments` | 처음 도입하는 SDK·Compose 고급 API·Flow 패턴, 사용자가 "주석/흐름/이해 안 가" 표현 시 그 블록에 풍부한 한국어 주석 |
| `mutter-android-harness-ops` | **하네스 자신을 손댈 때** — 규칙 판단형 작성, `tasks/` 운영(todo·lessons·archive), effort 기준, 리뷰 보고 형식, 문서 분량. 기능 코드 작업에는 트리거하지 않음 |

### 슬래시 커맨드

| 상황 | 명령 |
|------|------|
| feature 모듈에 새 화면(Screen + ViewModel) 추가 | `/new-android-screen` |
| 새 feature/core 모듈 생성 | `/new-android-module` |
| 모듈 의존성 방향 위반 점검 | `/gradle-dep-check` |
| 빌드 실행 및 에러 분석 | `/build-android` |
| 변경 사항 커밋 | `/commit` |
| 아키텍처 규칙 위반 전수 검사 | `/arch-check` |
| 릴리즈 배포 전 전수 검토(정적 + 런타임 → HTML 리포트) | `/release-audit` |

자동 실행 조건
- 코드 작성/수정 후 → `/arch-check`로 아키텍처 위반 자가 검증
- 모듈·의존성을 추가했으면 → `/gradle-dep-check`
- 빌드 에러 발생 시 → `/build-android`
- 서버 RPC·에러 코드가 바뀌면 → 코드보다 먼저 `letter-app/supabase/migrations`로 시그니처를 확인하고 `mutter-android-networking`의 매핑 표를 갱신한다. iOS 영향도 확인한다

---

## 보조 컨텍스트

- `.claude/CLAUDE.md` — OMC(oh-my-claudecode) 멀티에이전트 오케스트레이션 운영 원칙. 이 파일과 함께 로드된다.
- `.claude/skills/omc-reference/SKILL.md` — OMC 에이전트/스킬 카탈로그 참조.
- `maestro/README.md` — iOS와 공용 UI 플로우.
- `docs/` — 스토어 제출 자료(`play-store-submission.md`), Apple 로그인 설정, 심사 계정 시드 SQL.
- 마케팅·ASO는 iOS 저장소(`Mutter/marketing/`, `/marketing`)에서 다룬다.
