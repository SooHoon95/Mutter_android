package com.efreedom.mutter.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 화면 전환 조정자(iOS NavigationCoordinator 최소판).
 * 지금은 push/pop 스택을 노출한다. (RC-4: MainTab 구현 시 Navigation Compose NavController에
 * 위임해 프로세스 데스에도 back stack이 복원되도록 전환한다.)
 */
class NavigationCoordinator {
    private val _stack = MutableStateFlow<List<FeatureRoute>>(emptyList())
    val stack: StateFlow<List<FeatureRoute>> = _stack.asStateFlow()

    fun push(route: FeatureRoute) {
        _stack.value = _stack.value + route
    }

    fun pop() {
        _stack.value = _stack.value.dropLast(1)
    }

    fun popToRoot() {
        _stack.value = emptyList()
    }
}
