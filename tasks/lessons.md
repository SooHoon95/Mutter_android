# Lessons — 자기 교정 누적

사용자 교정이 발생할 때마다 최신 항목을 위로 추가한다. 포맷·아카이브 규칙은 `mutter-android-harness-ops`.

> 2026-10-08 하네스 신설. 이전 교훈은 iOS 저장소 세션의 자동메모리에 있었고, 오래 쓸 규칙은 스킬로 옮겼다 — JDK 17·SDK 경로·에뮬레이터(`mutter-android-conventions`), RPC 파라미터 `= null` 금지·반환 컬럼명(`mutter-android-networking`), SVG→VectorDrawable 파이프라인(`mutter-android-compose`).

### 2026-07-22 — RPC 파라미터 DTO의 `= null` 기본값이 링크 발급·편지 열기·신고를 전부 깨뜨렸다
- 상황: `issue_link`·`get_letter_by_token`·`report_takedown` 파라미터 DTO의 nullable 필드에 `= null`을 뒀다. supabase-kt는 `encodeDefaults=false`라 null 키를 생략했고, 서버 함수에 default가 없어 PGRST202로 호출이 실패했다. 컴파일·빌드는 전부 통과했다.
- 교정: 기본값을 제거하고 호출부에서 null을 명시로 넘김. 같은 감사에서 `get_my_letters_with_status` 반환 컬럼(`id`)을 `letter_id`로 디코드하던 것도 수정.
- 규칙: RPC 코드는 `letter-app/supabase/migrations`로 인자 default·반환 컬럼명을 확인하고 쓴다. RPC 변경은 실제 호출로 확인한 뒤에만 "된다"고 보고한다.
