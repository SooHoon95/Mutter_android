---
name: "source-command-commit"
description: "Migrated source command `commit`"
---

# source-command-commit

Use this skill when the user asks to run the migrated source command `commit`.

## Command Template

# commit

변경 사항을 검토하고 커밋한다. `mutter-android-conventions`의 커밋 규칙을 따른다.

## 작업 순서

### 1단계: 변경 확인
`git status`·`git diff`로 변경 내용을 본다. staging이 비어 있으면 변경 파일 목록을 보여주고 대상을 확인받는다.
일괄 커밋 요청이면 변경 파일을 **출처 구분**(이번 세션에서 작성 vs 세션 전부터 있던 워킹트리 변경)해 먼저 보여주고, 커밋 요약에도 그 구분을 적는다.

### 2단계: 커밋 전 검증
1. 비밀값·로컬 파일이 없는가 — `secrets.properties`, `local.properties`, `*.jks`/`keystore/`, `app/google-services.json`, Play 서비스 계정 JSON, `.omc/`, `.kotlin/`, `build/`
2. 커밋마다 목적이 하나인가 — 다르면 나눈다(묻지 않고 기본 분리)
3. 주석 처리된 dead code가 없는가
4. 메시지가 변경을 정확히 표현하는가

### 3단계: 메시지

```
{타입}(선택 scope): {한국어 요약} — {부연}
```

| 타입 | 사용 시점 |
|------|-----------|
| `feat` | 새 기능 (iOS 대응이면 `— iOS 패리티`) |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 구조 개선 |
| `style` | 포맷 등 의미 없는 수정 |
| `test` | 테스트 |
| `docs` | 문서 |
| `build` | Gradle·카탈로그·플러그인 |
| `chore` | 버전업 등 기타 |
| `ci` | fastlane·배포 스크립트 |

### 4단계: 사용자 확인 후 커밋

```bash
git add {파일들}        # 개별 지정
git commit -m "{메시지}"
```

## 금지

- Co-Authored-By 포함
- `--no-verify`
- `git add -A` / `git add .`
