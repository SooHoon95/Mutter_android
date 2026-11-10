---
description: Android 릴리즈 배포 전 전수 검토 — 정적 분석 + 에뮬레이터/기기 런타임 검사 후 심각도 랭킹 HTML 리포트
argument-hint: "[diff|full] [static|runtime|both]  (기본: diff both)"
---

# Release Audit (Android) — 배포 전 전수 검토

Mutter_android(Kotlin/Compose, Hilt, Clean 멀티모듈, supabase-kt)의 **릴리즈 전 리스크를 전수 검토**한다.
정적 분석과 런타임 검사를 돌리고 결과를 하나의 심각도 랭킹 HTML 리포트로 합친다. iOS 저장소의 `/release-audit`와 같은 구조다.

## 인자
- 1번째 = 범위: `diff`(기본, 직전 버전업 커밋..HEAD — `git log --grep=버전업 -1 --format=%H`) | `full`
- 2번째 = 모드: `both`(기본) | `static` | `runtime`

## 실행 절차

### 0. 자기발견
```
grep -n "versionCode\|versionName" app/build.gradle.kts
ls fastlane/metadata/android/ko-KR/changelogs/
```
versionCode가 Play 최신보다 크고 해당 changelogs 파일이 있는지 한 줄로 보고한다.

### 1. 빌드 게이트 (실패해도 계속)
```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug            # 컴파일
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:lintDebug                # Android Lint
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :domain:test :data:testDebugUnitTest
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:bundleRelease            # R8 — 서명 값 없으면 이 단계만 건너뛰고 finding으로 남김
```
성패는 `BUILD SUCCESSFUL`/`BUILD FAILED`로 판정한다.

### 2. 정적 분석 — 독립 클러스터를 병렬 Agent로 (결론만 회수, `파일:라인/severity/근거/배포차단`)

**C1. 디버그·테스트 잔존** — `println(`, `Log.d(` 남발, 하드코딩 테스트 계정/토큰, `TODO|FIXME`, 심사·디버그 전용 화면이 릴리스에서 도달 가능한지(`BuildConfig.DEBUG` 게이팅).

**C2. 크래시 위험** — `!!`, `lateinit` 미초기화 경로, `error(`/`TODO()`, `first()`/`[i]` 빈 컬렉션, 메인 스레드 IO, `viewModelScope` 밖 코루틴 누수, WebView JS 브리지 스레드에서 UI 접근.

**C3. 직렬화·R8** — `@Serializable` 누락, RPC 파라미터 DTO의 `= null` 기본값(PGRST202), 응답 `@SerialName`이 마이그레이션 컬럼명과 다름, 릴리스 keep 규칙 누락(Kakao·serialization).

**C4. 오프라인·에러 표시** — `NetworkMonitor` 소비처, 편지 열기·음악 재생(SoundCloud WebView·ExoPlayer) 오프라인 동작, WebView `onReceivedError` 처리, 사용자 액션 실패를 삼키는 `catch`.

**C5. UI/UX** — 다이얼로그 중복 표시, `imePadding` 누락으로 입력 가림, 하드코딩 색·글꼴, 큰 글꼴·작은 화면 깨짐, 접근성(`contentDescription`).

**C6. Edge 케이스** — 세션 만료(401) 중 중복 요청, 딥링크(`mutter://`, `/l/:token`, `/connect/`) 재진입, 권한 거부(알림), 프로세스 재생성 후 복원, 뒤로가기.

**C7. 보안·위생** — 로그에 토큰·개인정보, `secrets.properties` 외 하드코딩 키, `usesCleartextTraffic`, 과한 권한, exported 컴포넌트.

### 3. 런타임 검사 (모드 both/runtime)
에뮬레이터 부팅·설치는 `mutter-android-conventions` "실행·런타임 확인". 대상 화면을 띄운 뒤:
```
scripts/release-audit/offline-probe.sh <label> online
scripts/release-audit/offline-probe.sh <label> offline      # adb로 비행기 모드 ON 후 캡처
scripts/release-audit/offline-probe.sh <label> recovered    # 비행기 모드 OFF 후 캡처
```
- `<label>_offline.png`: 에러페이지/빈 화면/다이얼로그 중복 판정.
- `<label>_*_crash.txt`·`_anr.txt`: FATAL·ANR.
- 가능하면 `maestro test maestro/smoke.yaml`·`qa-tabs.yaml`도 돌린다.

### 4. 리포트
`scripts/release-audit/report-template.html`의 `{{...}}`(`SCOPE/TARGET/DATE/VERSION/VERDICT/VERDICT_CLASS/N_*/ROWS/UNVERIFIED`)를 치환해 파일로 쓰고 **Artifact로 발행**(icon `audit`). 터미널엔 배포 가·부 + BLOCKER 개수.
- 심각도: `BLOCKER`(크래시/빌드 깨짐/R8 직렬화 실패/RPC 호출 실패/PII 유출/디버그 화면 릴리스 도달) > `HIGH` > `MEDIUM` > `LOW`.
- 각 항목에 재현 조건 또는 `file:line`. 추측은 `{{UNVERIFIED}}`로 분리.

## 원칙
- 정적 클러스터는 독립 → 병렬, 런타임은 순차.
- 서브에이전트는 결론만 회수(파일 덤프 금지).
- diff 모드는 변경 파일 + 직접 호출부, full은 전수.
