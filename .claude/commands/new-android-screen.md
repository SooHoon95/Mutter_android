# new-android-screen

feature 모듈에 새 화면(Screen + ViewModel)을 추가한다. 규칙 근거는 `mutter-android-compose`·`mutter-android-architecture`.

## 입력

- **모듈명**: `feature/<name>` (예: inbox, viewer)
- **화면명**: 예 `ThreadList`
- **UseCase**(선택): 예 `ThreadUseCase`
- **iOS 대응 화면**(있으면): 동작·문구를 맞출 원본

## 절차

1. **분석** — 같은 모듈의 기존 `*Screen.kt`·`*ViewModel.kt`를 먼저 읽고 import·상태·이동 방식을 그대로 따른다. iOS 대응 화면이 있으면 그 ModelData·View도 읽는다.
2. **계획 설명** — 생성 파일과 역할. 3개 이상이면 확인받는다.
3. **구현** — 아래 템플릿.
4. **배선** — 이동 대상이면 `core/navigation/.../Routes.kt`에 `FeatureRoute` 항목, `app/.../MutterNavHost.kt`에 `composable<FeatureRoute.X>`, 호출부 람다에서 `navController.navigate(...)`.
5. **빌드·확인** — `/build-android` 후 에뮬레이터에서 화면 진입·스크린샷. 이후 `/arch-check`.

## 템플릿

### {화면명}ViewModel.kt

```kotlin
package com.efreedom.mutter.feature.{name}

/** iOS {화면명}ModelData 대응. */
data class {화면명}UiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class {화면명}ViewModel @Inject constructor(
    private val {useCase}: {UseCase},
) : ViewModel() {
    private val _state = MutableStateFlow({화면명}UiState())
    val state: StateFlow<{화면명}UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // {useCase}.…()
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}
```

### {화면명}Screen.kt

```kotlin
@Composable
fun {화면명}Screen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: {화면명}ViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier.fillMaxSize().background(Ivory)) {
        Text(text = "…", style = MutterText.titleLarge, color = Ink)
        state.error?.let { Text(it, style = MutterText.caption, color = GoldDeep) }
    }
}
```

## 완료 체크리스트

- [ ] ViewModel `@HiltViewModel`, 상태는 private `MutableStateFlow` + public `StateFlow`
- [ ] 사용자 액션 실패가 `error`로 드러남, `MutterError.userMessage` 사용
- [ ] Screen이 NavController·Repository를 모르고 람다로 이동을 알림
- [ ] 색·글꼴 토큰(`Ivory`, `Ink`, `MutterText.*`), 드로어블 `DsR.drawable.*`
- [ ] 새 라우트가 `FeatureRoute` + `MutterNavHost`에 등록됨
- [ ] `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug` 통과 + 에뮬레이터에서 화면 확인
