package com.efreedom.mutter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 편지 링크 오버레이(전체화면 뷰어)의 ViewModel 보관소 — 편지(토큰)마다 하나.
 *
 * 오버레이는 NavHost 백스택 밖이라 그대로 두면 화면의 ViewModel이 Activity에 붙는다. 그러면 앱이 떠 있는 동안
 * 다른 편지 링크를 열어도 처음 연 편지의 ViewModel이 다시 쓰여 그 편지가 보인다. NavHost 목적지가 백스택 항목마다
 * 보관소를 갖는 것처럼, 오버레이도 편지마다 보관소를 갖게 한다.
 *
 * 이 객체는 Activity에 붙어 있어 회전 같은 재생성에는 보관소가 그대로 남고(편지를 다시 불러오지 않는다),
 * 오버레이가 닫히거나 다른 편지로 바뀌면 [clear]로 그 편지의 ViewModel을 정리한다(진행 중이던 조회도 함께 취소된다).
 */
@HiltViewModel
internal class LetterOverlayViewModelStores @Inject constructor() : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun storeFor(token: String): ViewModelStore = stores.getOrPut(token) { ViewModelStore() }

    fun clear(token: String) {
        stores.remove(token)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}

/**
 * 보관소만 편지별로 바꾼 소유자. ViewModel을 만드는 방법(Hilt 팩토리·생성 인자)은 Activity 것을 그대로 쓴다 —
 * `hiltViewModel()`이 Activity에서와 똑같이 동작한다. SavedStateHandle은 Activity 것을 같이 쓰게 되므로,
 * 이 안의 화면이 SavedStateHandle에 편지별 값을 담게 되면 이 소유자가 저장 상태도 따로 가져야 한다(지금은 쓰는 곳이 없다).
 */
private class LetterOverlayOwner(
    override val viewModelStore: ViewModelStore,
    private val host: HasDefaultViewModelProviderFactory,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = host.defaultViewModelProviderFactory
    override val defaultViewModelCreationExtras: CreationExtras
        get() = host.defaultViewModelCreationExtras
}

/**
 * [content] 안의 ViewModel과 화면 상태를 [token] 편지에 묶는다.
 * 토큰이 바뀌면 이전 편지의 화면·ViewModel을 버리고 새로 시작하고, 같은 토큰으로 다시 그려질 때(회전·재구성)는 그대로 둔다.
 *
 * @param isChangingConfigurations Activity가 재생성(회전 등) 때문에 내려가는 중인지 — 그때는 보관소를 비우지 않는다.
 */
@Composable
internal fun LetterOverlayScope(
    token: String,
    isChangingConfigurations: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val activityOwner = checkNotNull(LocalViewModelStoreOwner.current) { "ViewModelStoreOwner가 없다" }
    val stores: LetterOverlayViewModelStores = hiltViewModel(activityOwner)
    // key — 편지가 바뀌면 화면 안의 remember 상태(암호 입력·간직 확인·스크롤 위치)도 새로 시작한다.
    key(token) {
        val owner = remember {
            LetterOverlayOwner(stores.storeFor(token), activityOwner as HasDefaultViewModelProviderFactory)
        }
        DisposableEffect(Unit) {
            onDispose {
                // 닫혔거나 다른 편지로 바뀌었다. 회전으로 화면이 다시 만들어지는 중이면 남겨 둔다.
                if (!isChangingConfigurations()) stores.clear(token)
            }
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
    }
}
