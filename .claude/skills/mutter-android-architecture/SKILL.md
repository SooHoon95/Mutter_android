---
name: mutter-android-architecture
description: Mutter Android의 모듈 구조·레이어 경계·의존 방향·Hilt DI를 다룰 때 참조한다. 새 feature/core 모듈 추가, settings.gradle.kts·build.gradle.kts 의존성 수정, Domain 모델·Repository 인터페이스·UseCase 설계, Repository 구현·DTO 매핑 구조, Screen→ViewModel→UseCase→Repository→SupabaseProvider 데이터 흐름, @HiltViewModel·@Binds·@Provides 배선이 필요한 작업에서 활용한다.
---

# Mutter Android Architecture

Clean Architecture 멀티모듈. iOS `Mutter`(Tuist 모듈러)를 1:1로 대응해 옮긴 구조다 — 모듈·클래스 KDoc에 "iOS X 대응"이 붙어 있고, 기능은 iOS와 패리티를 유지한다. 구조가 애매하면 iOS 대응 코드를 먼저 본다.

## 모듈

| 모듈 | 역할 | iOS 대응 |
|---|---|---|
| `:app` | `MainActivity`(Splash/Auth/Nickname/Main 전환), `MutterNavHost`, `AppViewModel`, 딥링크, 푸시 | MutterApp |
| `:domain` | 순수 Kotlin JVM — 모델, Repository 인터페이스(`repository/Repositories.kt`), UseCase(`usecase/UseCases.kt`), `MutterError`, `SessionInvalidation` | Domain + AppFoundation(Error) |
| `:data` | Repository 구현(`*RepositoryImpl`), DTO(`model/*Dto.kt`), `SupabaseErrorMapper`, Hilt 모듈 | Infrastructure |
| `:core:network` | `SupabaseProvider` 단일 진입점(Hilt 없음) | Network |
| `:core:navigation` | `FeatureRoute`(type-safe), `AppTab` | Router |
| `:core:audiosync` | 편지 음악 재생 — Media3 ExoPlayer(호스티드) + 숨김 WebView SoundCloud 위젯 | AudioSync |
| `:core:common` | `NetworkMonitor` | AppFoundation |
| `:uicomponent` | 테마·폰트·공통 컴포넌트·드로어블 | UIComponent |
| `:feature:*` | auth, compose, viewer, delivery, inbox, connections, threads, profile, ticket, home, legal | Feature |

`:feature:maintab`은 build 파일만 있고 `src`가 없다 — 탭 호스트(`MainScaffold`)는 `:app`에 있다.

## 의존 방향

```
:app → 전부
:feature:* → :domain, :uicomponent, :core:navigation (compose·viewer는 + :core:audiosync)
:data → :domain, :core:network
:core:audiosync → :domain
```

금지: `feature → feature`, `feature → :data`, `:domain → Android/Compose/Hilt`(순수 Kotlin + `javax.inject`만), `:uicomponent → :domain|feature`.
Feature 간 이동은 `FeatureRoute` + 콜백 람다로 `:app`이 연결한다.
다른 feature의 화면을 **위에 띄워야** 할 때도 같다 — 결제 안내 시트(`:feature:ticket`의 `TicketCheckoutSheet`)는 `:app`이 만들어 보내기·링크 관리·뷰어 화면에 `@Composable` 람다로 주입하고, 화면은 `TicketCheckoutRoute`(무엇을 위해)만 넘긴다.

컨벤션 플러그인(build-logic)은 없다. 새 모듈은 비슷한 기존 모듈의 `build.gradle.kts`를 복사해 `namespace`만 바꾼다(`/new-android-module`).

## 레이어 규칙

- **Domain**: `data class` 모델, Repository `interface`, `class XUseCase @Inject constructor(private val repository: XRepository)`. 대부분 통과형이고 로직이 있는 건 Audio·Profile·PushToken UseCase. 새 인터페이스·UseCase는 기존 단일 파일(`Repositories.kt`·`UseCases.kt`)에 추가한다.
- **Data**: `class XRepositoryImpl @Inject constructor(private val provider: SupabaseProvider) : XRepository`. 모든 호출을 `wrap { }`으로 감싸 `SupabaseErrorMapper.map`을 거친다(`mutter-android-networking`).
- **DTO**: `@Serializable data class XRow/XDto` + 멤버 `fun toDomain()`. Domain·feature에 DTO를 노출하지 않는다.
- **Feature**: `XScreen.kt` + `XViewModel.kt`(`mutter-android-compose`).
- **영속 데이터에는 이식 가능한 값만** — 편지 cue의 상대 에셋 경로(`/audio/x.m4a`)는 그대로 저장하고 재생 시점에 해석한다(웹·iOS와 같은 데이터를 공유하므로).

## DI (Hilt)

- `MutterApplication`은 `@HiltAndroidApp`, `MainActivity`는 `@AndroidEntryPoint`.
- `data/.../di/RepositoryModule.kt` — `@Binds @Singleton abstract fun bindXRepository(impl: XRepositoryImpl): XRepository`. 새 Repository는 여기 한 줄 추가.
- `data/.../di/NetworkModule.kt` — `@Provides @Singleton`으로 `SupabaseProvider(context, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)`.
- UseCase는 `@Inject constructor`라 모듈 등록이 필요 없다. ViewModel은 `@HiltViewModel` + `@Inject constructor(useCase)`.

## 자가 점검

- feature가 `:data`·다른 feature를 의존하지 않는다(`/gradle-dep-check`).
- `:domain`에 `android.*`·`androidx.*`·`dagger.*` import 없음.
- DTO가 feature/domain에 노출되지 않는다.
- 새 Repository가 `RepositoryModule`에 바인딩됐다(누락 시 Hilt 컴파일 에러).
- iOS 대응 기능이 있으면 이름·동작이 맞는다.
