---
name: mutter-android-ci-cd
description: Mutter Android 배포 자동화(fastlane → Google Play)를 다룬다. 트리거 — (1) Play 내부 테스트·프로덕션 업로드, 서명된 AAB 빌드, 버전업(versionCode/versionName), 릴리스 노트(changelogs), (2) fastlane android verify/bundle/internal/production 레인 실행·수정, (3) 업로드 키스토어·서비스 계정 키·서명 실패 디버깅, (4) 사용자가 "플레이 올려줘/안드 배포/버전업/내부 테스트" 라고 말하는 경우. GitHub Actions CI는 없다 — 배포는 로컬 fastlane.
---

# Mutter Android CI/CD (fastlane → Play)

CI 서버는 없다. 배포는 로컬에서 `fastlane android <lane>`(`fastlane/Fastfile`).

## 레인

| 레인 | 하는 일 |
|---|---|
| `verify` | `PLAY_JSON_KEY_PATH` 서비스 계정 키로 Play API 접근만 확인 |
| `bundle` | `JAVA_HOME=openjdk@17 ./gradlew clean :app:bundleRelease --no-daemon` → `app/build/outputs/bundle/release/app-release.aab` 존재 확인 |
| `internal` | bundle → 내부 테스트 트랙 업로드 |
| `production` | bundle → 프로덕션 트랙 업로드. **업로드 즉시 Google 검토로 들어간다** — 실행 전 사용자 확인 |

업로드는 메타데이터·이미지·스크린샷을 건너뛰고 changelogs만 올린다(`release_status: completed`). 스토어 문구·스크린샷은 Play Console에서 관리한다.

## 서명·키

- 업로드 키 `keystore/mutter-upload.jks`(gitignore). `secrets.properties`의 `RELEASE_STORE_FILE`·`RELEASE_STORE_PASSWORD`·`RELEASE_KEY_ALIAS`·`RELEASE_KEY_PASSWORD`가 모두 있고 파일이 실제로 있을 때만 `signingConfigs.release`가 만들어진다 — 없으면 서명 안 된 AAB가 나온다.
- Play 서비스 계정 JSON은 `PLAY_JSON_KEY_PATH` env(커밋 금지).

## 버전업

`app/build.gradle.kts`의 `versionCode`(+1, 매 업로드 고유) · `versionName`. 릴리스 노트는 `fastlane/metadata/android/ko-KR/changelogs/<versionCode>.txt`(한국어, 500자 이내). 커밋 예: `chore: 1.3.0(5) 버전업 + …`. 같은 기능이 iOS에도 나가면 양쪽 노트를 맞춘다.

## 완료 판정

- 업로드 성공 로그로 끝내지 않는다. Play Console(또는 `fastlane run google_play_track_version_codes track:internal`)에서 해당 versionCode가 트랙에 올라갔는지 확인한 뒤 보고한다. 내부 테스터 목록이 비어 있으면 아무에게도 안 보인다.
- fastlane 성패는 종료코드가 아니라 로그의 `fastlane.tools finished successfully` / `finished with errors`로 판정한다.
- 릴리스 AAB는 R8이 켜져 있어 디버그에서 안 보이던 직렬화·리플렉션 문제가 날 수 있다 — 주요 기능(편지 열기·음악 재생·로그인)을 릴리스 빌드로 한 번 실행해 본다.
