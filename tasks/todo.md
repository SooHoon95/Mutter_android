# tasks/todo.md

> 끝난 작업은 `tasks/archive/`로 옮긴다(`mutter-android-harness-ops`).

## 현재 작업 — 하네스 구축 (afin-ios 구조 정합) (2026-10-08)

- [x] CLAUDE.md(원칙 7절·Task Management·Core Principles·스킬/커맨드 표) + `.claude/CLAUDE.md`(OMC)
- [x] 스킬 8개: `mutter-android-{architecture,compose,networking,conventions,ci-cd,learning-comments,harness-ops}` + `omc-reference`
- [x] 커맨드 7개: `/arch-check` `/build-android` `/commit` `/gradle-dep-check` `/new-android-screen` `/new-android-module` `/release-audit`(+ `scripts/release-audit/`)
- [x] tasks/(todo·lessons·archive), .gitignore에 런타임 상태 추가
- [x] 검증 — 정적(frontmatter·dangling) + `claude -p` 스킬 노출·트리거 + 문서화한 빌드 명령 실제 실행

### 리뷰
- 정적: 스킬 8개 name=디렉터리, dangling 0(남은 경고는 OMC 플러그인 커맨드·iOS 저장소 `/marketing` 참조 — 의도된 것).
- `claude -p` init: 프로젝트 스킬 7개·커맨드 7개 로드(`omc-reference`는 user-invocable:false라 목록 미표시 — afin-ios와 동일).
- 트리거: 네트워킹 2회 → `mutter-android-networking` 로드, UI → `/new-android-screen` 경유, 빌드 → conventions 스킬 참조. afin-ios도 Skill 도구 대신 SKILL.md를 직접 읽는 동일 동작.
- 실행 중 결함 발견·수정: 비대화형 셸에서 맨 `./gradlew`가 "Unable to locate a Java Runtime"으로 실패(`org.gradle.java.home`은 데몬 전용) → 모든 문서 명령에 `JAVA_HOME=…openjdk@17` 접두.
- 수정 후: `assembleDebug`·`:domain:test :data:testDebugUnitTest` BUILD SUCCESSFUL, `installDebug` → `am start` → 에뮬레이터 로그인 화면 스크린샷 확인, crash 버퍼 FATAL 0. `offline-probe.sh --self-check` OK.
