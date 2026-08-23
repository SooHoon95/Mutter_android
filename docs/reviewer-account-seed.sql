-- ============================================================================
-- 심사용(Play Console / App Store) 리뷰어 계정 시드
--
-- 실행 위치: Supabase Dashboard → SQL Editor
--
-- ── 설계 원칙: 본인 계정을 절대 건드리지 않는다 ─────────────────────────────
-- iOS·Android·웹이 같은 DB를 쓴다. 발신자로 "본인 계정"을 쓰면 그 계정의 iOS 앱에
-- 다음이 그대로 나타난다(마이그레이션 0008 확인):
--   · get_my_letters_with_status  → 홈 목록에 시드 편지가 '발송됨'으로 섞인다
--   · get_counterparts            → 주고받은 상대 목록에 심사 계정이 뜬다
--   · get_my_sent_with_recipients → 받은이가 '무터 심사'로 표시된다
--   · 리뷰어가 편지를 열면 letter_opens INSERT → trg_notify_letter_read(0029)
--     → 본인 실기기로 "편지를 읽었어요" 푸시가 날아간다
-- 그래서 발신자도 **전용 계정**을 따로 만든다. 시드 데이터는 심사용 계정 2개 사이에서만
-- 생성되고, 본인 계정에는 어떤 행도 추가되지 않는다.
--
-- ── 사전 조건: 계정 2개를 대시보드에서 만든다 (SQL로 하지 않는다) ──────────
--   Dashboard → Authentication → Users → Add user  ×2
--     ① 심사용 계정  : v_reviewer_email  (Play Console '앱 액세스 권한'에 적을 계정)
--     ② 전용 발신자  : v_sender_email    (편지를 보내주는 가상의 상대)
--   두 계정 모두 [v] Auto Confirm User 체크.
--     → 확인 메일이 발송되지 않으므로 실제로 존재하지 않는 주소를 써도 된다.
--     → 미체크면 email_confirmed_at 이 NULL 이라 비밀번호 로그인이 막힌다.
--   auth.users 직접 INSERT 는 Supabase 버전에 따라 identities 누락으로 깨지므로 쓰지 않는다.
--
-- ── 이 스크립트가 하는 일 ───────────────────────────────────────────────────
--   1. 두 계정의 profiles.nickname 을 채운다
--      → 0021 이후 nickname 은 항상 NULL 로 생성되고, 앱은 NULL 이면 닉네임 입력
--        화면으로 보낸다(AppViewModel.kt:92 needsNickname). 채워두면 리뷰어가
--        로그인 직후 바로 홈으로 들어간다.
--   2. 발신자 소유 편지 2통 → issue_link → 리뷰어 inbox 에 save_to_inbox
--      → 리뷰어의 '받은 편지'가 비어 있지 않다
--   3. 리뷰어 소유 편지 1통 → '내 편지' 목록도 비어 있지 않다
--
-- 여러 번 실행해도 안전하다(고정 UUID·고정 토큰 + 존재 검사).
-- ============================================================================

do $$
declare
  -- ▼▼▼ 여기 두 줄만 실제 값으로 바꾼다 ▼▼▼
  v_reviewer_email text := 'review@mutter.app';        -- ① 심사용 계정
  v_sender_email   text := 'review-sender@mutter.app'; -- ② 전용 발신자(본인 계정 금지)
  -- ▲▲▲ ---------------------------------- ▲▲▲

  v_reviewer_nickname text := '무터 심사';
  v_sender_nickname   text := '연이';   -- 리뷰어에게 '보낸 사람'으로 보이는 이름

  v_reviewer_id uuid;
  v_sender_id   uuid;
  v_stray       int;

  -- 재실행 시 중복 생성을 막기 위한 고정 UUID
  c_letter_in_1  uuid := '00000000-0000-4000-8000-000000000a01';
  c_letter_in_2  uuid := '00000000-0000-4000-8000-000000000a02';
  c_letter_own_1 uuid := '00000000-0000-4000-8000-000000000a03';

  -- delivery_links.token — issue_link(0024)의 서버 검증을 통과해야 한다:
  --   length >= 22  AND  ^[A-Za-z0-9_-]+$   (아래 값은 32자)
  c_token_1 text := 'MutterReviewSeedTokenAAAAAAAAAA1';
  c_token_2 text := 'MutterReviewSeedTokenBBBBBBBBBB2';
begin
  -- ── 0. 계정 확인 ──────────────────────────────────────────────────────────
  select id into v_reviewer_id from auth.users where email = v_reviewer_email;
  if v_reviewer_id is null then
    raise exception
      '심사용 계정(%)이 없다. Dashboard → Authentication → Users → Add user 로 만들고 Auto Confirm User 를 체크하라.',
      v_reviewer_email;
  end if;

  select id into v_sender_id from auth.users where email = v_sender_email;
  if v_sender_id is null then
    raise exception
      '전용 발신자 계정(%)이 없다. 같은 방식으로 하나 더 만들라(본인 계정을 쓰지 말 것).',
      v_sender_email;
  end if;

  if v_sender_id = v_reviewer_id then
    raise exception '발신자와 리뷰어가 같은 계정이다. 자기 편지는 받은 편지함에 담기지 않으므로 서로 달라야 한다.';
  end if;

  -- ── 0-1. 실계정 보호 장치 ────────────────────────────────────────────────
  -- 두 계정 중 하나라도 시드 외 편지를 이미 갖고 있으면 실제 사용자 계정으로 보고 중단한다.
  -- (본인 계정 이메일을 잘못 적어 넣는 사고를 여기서 막는다)
  select count(*) into v_stray
  from public.letters
  where owner_id in (v_reviewer_id, v_sender_id)
    and id not in (c_letter_in_1, c_letter_in_2, c_letter_own_1);

  if v_stray > 0 then
    raise exception
      '지정한 계정에 시드가 아닌 편지가 %건 있다. 실제 사용자 계정일 수 있으므로 중단한다. 이메일을 다시 확인하라.',
      v_stray;
  end if;

  raise notice 'reviewer=% sender=%', v_reviewer_id, v_sender_id;

  -- ── 1. 발신자 권한으로 전환 ──────────────────────────────────────────────
  -- letters·profiles·delivery_links 는 FORCE ROW LEVEL SECURITY(0002·0006)이고,
  -- issue_link 는 내부에서 owner_id <> auth.uid() 를 FORBIDDEN 으로 막는다(0024).
  -- SQL Editor 에서는 auth.uid() 가 NULL 이므로 request.jwt.claims 로 사용자를 위장한다.
  -- is_local=true → 이 트랜잭션에서만 유효.
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_sender_id::text, 'role', 'authenticated')::text,
    true
  );

  -- 발신자 프로필 — 행은 가입 트리거가 이미 만들었고 nickname 만 NULL 이다(0021).
  -- 이미 이름이 있으면 덮어쓰지 않는다.
  -- ON CONFLICT DO UPDATE 안에서 기존 행은 스키마 없이 `profiles.` 로만 참조한다
  -- (`public.profiles.nickname` 은 missing FROM-clause 오류가 난다).
  insert into public.profiles (id, nickname)
  values (v_sender_id, v_sender_nickname)
  on conflict (id) do update
    set nickname = coalesce(nullif(profiles.nickname, ''), excluded.nickname);

  -- 발신자 소유 편지 2통.
  -- paragraphs 는 jsonb 배열이고 키는 서버와 camelCase 계약이다:
  --   { id, order, text, cue? }
  -- iOS ParagraphDTO(Swift)·Android ParagraphDto 모두 id/order/text 가 필수라
  -- 세 키를 반드시 채운다. cue 는 양쪽 다 optional 이라 생략한다.
  -- template_id 는 iOS LetterRow.templateId 가 non-optional String 이므로 NULL 을 넣지 않는다.
  -- 아래 슬러그는 iOS LetterTheme.swift·Android LetterTheme.kt 에 동일하게 존재한다.
  insert into public.letters (id, owner_id, title, paragraphs, template_id)
  values (
    c_letter_in_1,
    v_sender_id,
    '먼 길을 걷는 너에게',
    jsonb_build_array(
      jsonb_build_object(
        'id', gen_random_uuid(), 'order', 0,
        'text', '요즘 어떻게 지내는지 자주 생각했어. 연락은 못 했지만 마음은 늘 네 쪽에 있었다.'
      ),
      jsonb_build_object(
        'id', gen_random_uuid(), 'order', 1,
        'text', '지치는 날에는 굳이 씩씩할 필요 없어. 오늘 하루를 넘겼다는 것만으로 충분하다.'
      ),
      jsonb_build_object(
        'id', gen_random_uuid(), 'order', 2,
        'text', '다음에 만나면 그동안 못 한 이야기를 다 들려줘. 나는 언제든 듣고 있을게.'
      )
    ),
    'classic-serif'
  )
  on conflict (id) do update
    set title = excluded.title,
        paragraphs = excluded.paragraphs,
        template_id = excluded.template_id;

  insert into public.letters (id, owner_id, title, paragraphs, template_id)
  values (
    c_letter_in_2,
    v_sender_id,
    '봄이 오면 다시 만나자',
    jsonb_build_array(
      jsonb_build_object(
        'id', gen_random_uuid(), 'order', 0,
        'text', '겨울이 길었다. 그래도 창문 앞 화분에 새 잎이 하나 올라왔어.'
      ),
      jsonb_build_object(
        'id', gen_random_uuid(), 'order', 1,
        'text', '네가 좋아하던 그 길에 벚꽃이 피면 사진을 찍어 보낼 생각이야. 그때는 같이 걷자.'
      )
    ),
    'spring-day'
  )
  on conflict (id) do update
    set title = excluded.title,
        paragraphs = excluded.paragraphs,
        template_id = excluded.template_id;

  -- 열람 링크 발급. issue_link 에는 ON CONFLICT 가 없고 token 이 unique 이므로
  -- 재실행 시 중복 발급을 막기 위해 존재 검사를 먼저 한다.
  -- p_password=NULL → 암호 없음, p_expires_at=NULL → 만료 없음, p_reveal_at=NULL → 즉시 공개.
  if not exists (select 1 from public.delivery_links where token = c_token_1) then
    perform public.issue_link(c_letter_in_1, c_token_1, null, null, null);
    raise notice 'issued link 1';
  end if;

  if not exists (select 1 from public.delivery_links where token = c_token_2) then
    perform public.issue_link(c_letter_in_2, c_token_2, null, null, null);
    raise notice 'issued link 2';
  end if;

  -- ── 2. 리뷰어 권한으로 전환 ──────────────────────────────────────────────
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_reviewer_id::text, 'role', 'authenticated')::text,
    true
  );

  insert into public.profiles (id, nickname)
  values (v_reviewer_id, v_reviewer_nickname)
  on conflict (id) do update
    set nickname = coalesce(nullif(profiles.nickname, ''), excluded.nickname);

  -- 리뷰어 소유 편지 1통 — '내 편지' 목록이 비어 있지 않게.
  insert into public.letters (id, owner_id, title, paragraphs, template_id)
  values (
    c_letter_own_1,
    v_reviewer_id,
    '고마웠다고 말하고 싶어서',
    jsonb_build_array(
      jsonb_build_object(
        'id', gen_random_uuid(), 'order', 0,
        'text', '그때 아무 말 없이 곁에 있어 준 게 오래 남았다. 늦었지만 고맙다는 말을 남겨 둔다.'
      )
    ),
    'warm-craft'
  )
  on conflict (id) do update
    set title = excluded.title,
        paragraphs = excluded.paragraphs,
        template_id = excluded.template_id;

  -- 받은 편지함에 담기. save_to_inbox 는 unique(user_id, letter_id) 에 대해
  -- ON CONFLICT DO UPDATE 이므로 재실행이 안전하다(0006).
  -- 트리거 on_inbox_insert 는 AFTER INSERT 전용이라 재실행 시 푸시가 중복 발송되지 않는다.
  -- 최초 INSERT 시 푸시는 리뷰어 본인에게 가고, 리뷰어는 기기 토큰이 없어 무시된다(0029).
  perform public.save_to_inbox(c_token_1);
  perform public.save_to_inbox(c_token_2);

  -- 위장 해제
  perform set_config('request.jwt.claims', null, true);

  raise notice '완료. 아래 검증 쿼리를 실행하라.';
end $$;


-- ============================================================================
-- 검증 — 아래 이메일을 위와 같은 값으로 바꾼 뒤 실행. 세 결과 모두 행이 나와야 한다.
-- ============================================================================

-- (1) 계정 상태 — email_confirmed_at 이 NULL 이면 비밀번호 로그인이 막힌다.
--     NULL 이면 Dashboard 에서 해당 유저 → Confirm email.
select u.email, u.email_confirmed_at is not null as confirmed, p.nickname
from auth.users u
left join public.profiles p on p.id = u.id
where u.email in ('review@mutter.app', 'review-sender@mutter.app');

-- (2) 리뷰어로 위장
select set_config(
  'request.jwt.claims',
  json_build_object(
    'sub', (select id::text from auth.users where email = 'review@mutter.app'),
    'role', 'authenticated'
  )::text,
  false
);

-- 받은 편지 2행 + sender_nickname 이 채워져 있어야 한다
select letter_id, title, sender_nickname, saved_at from public.get_my_inbox();

-- 내 편지 1행
select id, title, is_sent from public.get_my_letters_with_status();

-- 편지가 실제로 열리는지(암호 없음 경로).
-- get_letter_by_token 은 2-arg·3-arg 오버로드가 공존한다(0014) → NULL 에 타입을 명시해야
-- 인자 해석이 모호해지지 않는다.
-- 이 쿼리는 부작용이 없다:
--   · 읽음 기록은 별도 RPC record_letter_open(0017)이 하고 get_letter_by_token 은 건드리지 않는다
--     → letter_opens 행이 생기지 않고 "편지를 읽었어요" 푸시(0029)도 발송되지 않는다
--   · 기기 잠금(claim-and-bind)은 0011에서 제거됐다 → 리뷰어 기기의 열람이 선점되지 않는다
select public.get_letter_by_token('MutterReviewSeedTokenAAAAAAAAAA1', null::text);

-- 위장 해제
select set_config('request.jwt.claims', null, false);

-- (3) 본인 계정이 오염되지 않았는지 — 0행이어야 한다.
--     시드 편지의 소유자가 심사용 계정 2개 밖으로 새지 않았음을 확인한다.
select l.id, l.title, u.email
from public.letters l
join auth.users u on u.id = l.owner_id
where l.id in ('00000000-0000-4000-8000-000000000a01',
               '00000000-0000-4000-8000-000000000a02',
               '00000000-0000-4000-8000-000000000a03')
  and u.email not in ('review@mutter.app', 'review-sender@mutter.app');


-- ============================================================================
-- 정리 — 심사가 끝나고 시드를 지울 때만 실행 (기본은 주석 유지)
-- ============================================================================
-- 편지를 지우면 delivery_links.letter_id·inbox.letter_id 가 모두
-- `on delete cascade`(0002·0006)라 링크와 받은 편지함 행이 함께 사라진다.
--
-- a04는 이 스크립트가 만들지 않는다. 스토어 스크린샷(편지 작성 화면에 내용이 보이는 컷)용으로
-- 심사용 계정에 REST API로 따로 넣은 초안 "봄에 다시 만나자"다. 함께 지우면 된다.
--
-- delete from public.letters
--  where id in ('00000000-0000-4000-8000-000000000a01',
--               '00000000-0000-4000-8000-000000000a02',
--               '00000000-0000-4000-8000-000000000a03',
--               '00000000-0000-4000-8000-000000000a04');
--
-- 계정까지 없앨 경우: Dashboard → Authentication → Users 에서 두 계정 삭제.
-- profiles·letters·inbox 모두 auth.users 에 cascade 되어 함께 정리된다.
-- 단, 심사 중에는 지우지 말 것 — 업데이트 제출마다 같은 계정으로 재심사한다.
