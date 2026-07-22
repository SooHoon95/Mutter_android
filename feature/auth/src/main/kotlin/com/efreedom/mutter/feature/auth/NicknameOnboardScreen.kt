package com.efreedom.mutter.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterWordmark
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.Hairline
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText

/**
 * 가입 직후 닉네임 온보딩 화면 (iOS `NicknameOnboardView` 1:1).
 *
 * 인증은 됐지만 프로필이 없는 상태에서 표시된다. "시작하기"를 눌러야 프로필(회원가입)이 생성되고
 * [onComplete]로 루트에 알려 메인으로 진입한다. 되돌아갈 곳이 없어 뒤로가기/네비바 없음.
 */
@Composable
fun NicknameOnboardScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NicknameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 완료 콜백은 LaunchedEffect에서 — 리컴포지션마다 재호출되는 것을 막는다.
    LaunchedEffect(state.done) {
        if (state.done) onComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MutterWordmark(markSize = 72.dp, wordmarkSize = 32, showTagline = false)

            Spacer(Modifier.height(4.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("어떻게 불러드릴까요?", style = MutterText.titleLarge, color = Ink)
                Text(
                    "받는 사람에게 보일 이름이에요",
                    style = MutterText.bodyMedium,
                    color = InkSoft,
                    textAlign = TextAlign.Center,
                )
            }

            OutlinedTextField(
                value = state.nickname,
                onValueChange = viewModel::onNicknameChange,
                placeholder = { Text("닉네임", style = MutterText.bodyMedium, color = InkFaint) },
                singleLine = true,
                textStyle = MutterText.bodyMedium,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Ivory,
                    unfocusedContainerColor = Ivory,
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Hairline,
                    cursorColor = Gold,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { message ->
                Text(
                    text = message,
                    style = MutterText.caption,
                    color = GoldDeep,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            MutterButton(
                text = "시작하기",
                onClick = viewModel::save,
                isLoading = state.isLoading,
                enabled = viewModel.isValid,
            )
        }
    }
}
