# tasks/todo.md

> 끝난 작업은 `tasks/archive/`로 옮긴다(`mutter-android-harness-ops`).

## 현재 작업 — iOS 기준 패리티 (2026-11-15 ~ 11-16)

iOS 앱을 기준으로 동작·문구(한국어·영어)를 맞춘다. 계획·검토·증거: `.omc/plans/parity/`(gaps.md, T1~T4-plan.md, diffs/). 커밋 전.
- [x] T3 편지 열람·받은 편지함·받은 편지 삭제함·삭제함 공용 레이아웃·남은 날짜 계산(달력 기준) — 검토 승인
- [x] T4 홈·보낸 편지 삭제함·스레드·연결(초대 수락)·편지권 — 검토 승인
- [x] T2 편지 작성·사진 여러 장·보내기 시트·링크 화면(예약 시각) — 검토 승인
- [x] T1 로그인·닉네임·프로필·권리침해 신고·탭 이름·앱 시작(알림 탭·401·강제 업데이트) — 검토 승인
- [x] 커밋(2026-11-17 사용자 확인, GA4 분석과 함께)
- [ ] iOS 시뮬레이터 나란히 대조: 스레드 말풍선·연결 행 경과 시간 표기(가설), 화면 간격
- [ ] 실기기: 결제 거부(403 not_tester·account_mismatch) 문구, 포그라운드 알림 탭, 소셜 로그인 실패 문구, 실제 소리
- [ ] 앱 복귀 직후 요청이 로그인 없이 나가는 문제의 공통 수정(지금은 연결 목록만 대기 처리) — 사용자 결정 대기
- [ ] 오류 문구(`MutterError`·ViewModel 폴백) 영어화 — 후속

### 리뷰
- 검증: `assembleDebug :domain:test :data:testDebugUnitTest --rerun-tasks` exit 0, 테스트 27건 실패 0. iOS↔Android 문구 기계 대조 355쌍 불일치 0(의도된 차이 23). 스토리마다 에뮬레이터(로컬 Supabase)에서 한국어·영어 화면 확인, task마다 독립 검토(iOS 원본과 재대조) 승인.
- 함께 고친 기존 버그: 초대 "다시 시도" 무동작, 연결이 있는 사람의 새 초대 수락 불가(서버 N:N과 불일치), 로그아웃 뒤 로그인 화면 상태 잔류·재로그인 멈춤, 로그아웃 상태 받은 편지 알림 탭이 로그인 없이 열림, 강제 업데이트 화면 뒤 터치 통과, 예약 날짜 창이 전날로 열림(00~09시), 받은함 삭제 실패 시 목록 키 중복 가능, 앱 복귀 직후 연결 목록 요청 401.
- iOS와 일부러 다르게 둔 것: 초대 수락 조건(서버 규칙), 보내기 시트 무료 기간 안내 조건(iOS 쪽 이탈), "연결 탭" 문구, 기본 제공 음원의 "SoundCloud" 표시, 영어 단수형, 스토어 이름, 프로필의 약관·신고 진입, 콜드 스타트 프로필 조회 실패 시 메인 진입.
- iOS 수정 후보(미반영): `Connection.swift` canAccept와 안내 문구 2종, `ComposeModelData` 무료 기간 안내의 paidEnabled 조건, "사람들 탭" 문구, 영어 복수형("1 tickets"·"1 days"), 음악 바 "SoundCloud" 표시, 이용약관·권리침해 신고 진입 없음.
- 시각 차이(범위 밖, 후속): 목록의 접근 상태 배지 모양·위치, 삭제함 행 버튼, 음악 바, 일부 아이콘 크기·색, 스플래시 최소 시간 2.3초(iOS 2.0초).

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
