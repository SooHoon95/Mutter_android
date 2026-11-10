---
name: mutter-android-compose
description: Mutter Android에서 Jetpack Compose 화면을 작성·수정·검토할 때 참조한다. Screen + @HiltViewModel + UiState(StateFlow) 패턴, 일회성 이벤트 플래그, 에러 표시(UiState.error·MutterError.userMessage), type-safe 내비게이션(FeatureRoute·MutterNavHost·탭 MainScaffold), 디자인 시스템(MutterTheme·MutterText·색 토큰·MutterButton/MutterIcon·DsR.drawable), 문자열 리소스, WebView/ExoPlayer 같은 명령형 뷰 수명, 상태 보존(rememberSaveable)을 다룬다.
---

# Mutter Android Compose

## Screen + ViewModel

```kotlin
data class InboxUiState(val items: List<InboxItem> = emptyList(), val isLoading: Boolean = false)

@HiltViewModel
class InboxViewModel @Inject constructor(private val inboxUseCase: InboxUseCase) : ViewModel() {
    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    fun load() { viewModelScope.launch { _state.value = _state.value.copy(isLoading = true) /* … */ } }
}

@Composable
fun InboxScreen(
    onOpen: (token: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    …
}
```

- UiState는 ViewModel 파일 상단의 `data class XUiState`. 상태가 질적으로 다르면 sealed(`ViewerState`, `ConnectInviteUiState`).
- 별도 Event/Effect 클래스는 없다. Screen이 ViewModel 공개 메서드를 직접 호출한다.
- 일회성 효과(토스트·뒤로가기)는 상태의 boolean 플래그 + 처리 후 리셋 메서드(`savedToast` + `onSavedToastShown()`, `navigateUp`).
- 화면 이동은 람다 파라미터(`onOpen: (String) -> Unit`)로 밖에 알린다. Screen이 NavController를 모른다.
- ViewModel KDoc에 iOS 대응(`iOS InboxModelData 대응`)을 적는다.

## 에러 표시

Repository가 `MutterError`로 정규화해 던진다(`mutter-android-networking`).

```kotlin
catch (e: Throwable) {
    val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
    _state.value = _state.value.copy(error = message)
}
// Screen
state.error?.let { Text(it, style = MutterText.caption, color = GoldDeep) }
```

- 특정 케이스는 상태로 분기한다(Viewer: `WrongPassword → PasswordRequired`, `LinkNotYetRevealed → RevealPending`).
- 실패를 삼키는 `catch (_: Throwable) {}`는 "빈 목록이 정상 상태"인 조회에서만(Inbox 로드). 사용자 액션 실패는 error로 드러낸다.
- 확인은 `AlertDialog`, 토스트성 알림은 Snackbar.

## 내비게이션

- 라우트: `core/navigation/.../Routes.kt`의 `@Serializable sealed interface FeatureRoute` — data object(SignIn, OnboardNickname, ComposeNew, Takedown, Terms, Privacy)와 data class(ComposeEdit, ComposeReply, ViewerToken, ViewerMyLetter, Delivery, ConnectInvite, Thread).
- NavHost: `app/.../MutterNavHost.kt`, `startDestination = MainTabs`. 등록은 `composable<FeatureRoute.X> { entry -> val route = entry.toRoute<FeatureRoute.X>(); XScreen(…) }`.
- 탭: `MainScaffold`의 5탭 `NavigationBar`. 선택 탭은 `rememberSaveable`, 탭 화면은 `when`으로 고른다(탭은 nav destination이 아니다).
- Splash/Auth/Nickname은 NavHost 밖 — `MainActivity`가 `AppViewModel.state`(LOADING·SIGNED_OUT·NEEDS_NICKNAME·READY)로 `Crossfade` 전환.
- 딥링크 파싱은 `app/.../Deeplink.kt`.
- 새 화면: `FeatureRoute` 항목 → feature에 Screen+ViewModel → `MutterNavHost`에 `composable<>` → 호출부 람다에서 `navController.navigate(FeatureRoute.X(…))`.

## 디자인 시스템 (`:uicomponent`)

- `MutterTheme {}` — 라이트 전용, `LocalTextStyle` 폰트를 Pretendard로 고정(Roboto 폴백 방지).
- 색: `theme/Color.kt` 최상위 val — `Ivory`(배경), `Surface`, `Ink`/`InkSoft`/`InkMid`/`InkFaint`, `Gold`(로즈모브 액센트)/`GoldSoft`/`GoldDeep`/`GoldLight`/`OnGold`, `Hairline`, `Danger`/`Warning`/`Success`, 편지 테마 팔레트. 하드코딩 `Color(0x…)`를 화면에 쓰지 않는다.
- 타이포: `MutterText.display/titleLarge/title`(명조 Hahmlet) · `headline/bodyLarge(Bold)/bodyMedium(Bold)/bodySmall/caption(Bold)`(Pretendard). iOS `MutterFont`와 1:1.
- 편지 테마: `theme/LetterTheme.kt`, `id`는 웹·DB `templateId`와 같은 slug.
- 컴포넌트: `MutterButton`(그라데이션 Box), `MutterIcon`(틴트, 기본 Ink)·`MutterImage`(원색), `PullableFullSizeBox`(빈 상태에서도 당겨서 새로고침), `ShareSheet`, `SoundCloudAttribution`, `LetterPhotoImage`, `MutterSplashScreen`.
- 드로어블: `uicomponent/src/main/res/drawable/` VectorDrawable, snake_case(`tab_x`/`tab_x_fill`, `empty_*`, `mood_*`, `social_*`). 화면에서 `import com.efreedom.mutter.uicomponent.R as DsR` → `DsR.drawable.x`. iOS SVG를 옮길 땐 svgo 정규화 → svg2vectordrawable → 색 없는 path에 색 주입(s2v가 상속 stroke·circle/ellipse/line을 버린다).

## 문자열

현재 UI 문구 대부분은 Composable에 한국어 하드코딩이다. 새 문구는 해당 feature의 `src/main/res/values/strings.xml`(ko) + `values-en/strings.xml`에 feature 접두사 키(`compose_photo_add`)로 추가하는 방향을 따른다(compose·viewer가 이 방식). 기존 하드코딩을 일괄로 옮기지는 않는다. Maestro는 양 플랫폼 공통 문구만 assert하므로 문구를 바꾸면 `maestro/`도 확인한다.

## 명령형 뷰·상태

- 숨김 WebView(SoundCloud 위젯)·ExoPlayer는 `:core:audiosync`의 Source 인스턴스가 소유한다. `SoundCloudSource.Attachment()`가 그 WebView를 `remember { webView }`로 Compose 트리에 올린다(1dp, JS 실행에 필요). Composable 안에서 WebView를 새로 만들면 리컴포지션마다 재생성돼 JS 브리지 명령이 죽은 컨텍스트로 가고 조용히 무음이 된다.
- 비동기 준비가 필요한 재생 소스는 재생 의도를 준비 상태와 분리해 기억하고 READY 시 실행한다.
- 키보드가 있는 화면은 `imePadding()`.
- 구성 변경을 넘겨야 하는 UI 상태는 `rememberSaveable`, 화면 데이터는 ViewModel.

## 자가 점검

- Screen이 NavController·Repository를 직접 모른다(람다·ViewModel 경유).
- 사용자 액션 실패가 UiState.error로 드러난다.
- 색·글꼴이 토큰(`Ink`, `MutterText.*`)으로만 쓰였다.
- 새 화면이 `FeatureRoute` + `MutterNavHost`에 등록됐다.
