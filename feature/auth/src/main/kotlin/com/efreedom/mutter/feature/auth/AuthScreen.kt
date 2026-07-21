package com.efreedom.mutter.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.MutterImage
import com.efreedom.mutter.uicomponent.component.MutterWordmark
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.Hairline
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface

private val KakaoYellow = Color(0xFFFEE500)
private val KakaoLabel = Color(0xFF191600)
private val AppleBlack = Color(0xFF000000)

/**
 * 로그인/온보딩 화면 (iOS `AuthView` 1:1). 아이보리 배경 + 로고 + 카드 + 소셜 버튼.
 * 이메일 매직코드(가입 겸용) / 비밀번호 / 소셜(준비 중).
 */
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (state.authenticated) {
        onAuthenticated()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MutterWordmark(markSize = 88.dp, wordmarkSize = 40)

            Spacer(Modifier.height(24.dp))

            // 카드
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), clip = false)
                    .background(Surface, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.step) {
                    AuthStep.ENTER_EMAIL -> EmailStep(state, viewModel)
                    AuthStep.ENTER_CODE -> CodeStep(state, viewModel)
                    AuthStep.ENTER_PASSWORD -> PasswordStep(state, viewModel)
                    AuthStep.ENTER_SOCIAL_NICKNAME -> SocialNicknameStep(state, viewModel)
                }

                state.error?.let { message ->
                    Text(
                        text = message,
                        style = MutterText.caption,
                        color = GoldDeep,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 소셜 닉네임 입력 중엔 소셜 버튼을 숨긴다(이미 소셜 인증은 끝난 상태).
            if (state.step != AuthStep.ENTER_SOCIAL_NICKNAME) {
                Spacer(Modifier.height(20.dp))
                SocialSection(
                    enabled = !state.isLoading,
                    onGoogle = { viewModel.signInGoogle(context) },
                    onKakao = { viewModel.signInKakao(context) },
                    onApple = { viewModel.appleComingSoon() },
                )
            }
        }
    }
}

// ─── Steps ────────────────────────────────────────────────────────────────────

@Composable
private fun EmailStep(state: AuthUiState, vm: AuthViewModel) {
    MutterField(
        value = state.email,
        onValueChange = vm::onEmailChange,
        placeholder = "이메일",
        keyboardType = KeyboardType.Email,
    )
    MutterButton(
        text = "인증코드 받기",
        onClick = vm::requestCode,
        isLoading = state.isLoading,
        enabled = vm.isEmailValid,
    )
    MutterButton(
        text = "비밀번호로 로그인",
        onClick = vm::switchToPassword,
        style = MutterButtonStyle.Ghost,
    )
}

@Composable
private fun CodeStep(state: AuthUiState, vm: AuthViewModel) {
    Text(
        text = "${state.email} 로 보낸 코드를 입력하세요",
        style = MutterText.caption,
        color = InkSoft,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    MutterField(
        value = state.code,
        onValueChange = vm::onCodeChange,
        placeholder = "인증코드",
        keyboardType = KeyboardType.Number,
    )
    MutterButton(
        text = "확인",
        onClick = vm::verifyCode,
        isLoading = state.isLoading,
        enabled = state.code.isNotEmpty(),
    )
    MutterButton(
        text = "이메일 다시 입력",
        onClick = vm::backToEmail,
        style = MutterButtonStyle.Ghost,
    )
}

@Composable
private fun PasswordStep(state: AuthUiState, vm: AuthViewModel) {
    MutterField(
        value = state.email,
        onValueChange = vm::onEmailChange,
        placeholder = "이메일",
        keyboardType = KeyboardType.Email,
    )
    MutterField(
        value = state.password,
        onValueChange = vm::onPasswordChange,
        placeholder = "비밀번호",
        keyboardType = KeyboardType.Password,
        isPassword = true,
    )
    MutterButton(
        text = "로그인",
        onClick = vm::signInWithPassword,
        isLoading = state.isLoading,
        enabled = vm.isEmailValid && state.password.isNotEmpty(),
    )
    MutterButton(
        text = "인증코드로 로그인",
        onClick = vm::backToEmail,
        style = MutterButtonStyle.Ghost,
    )
}

/** 신규 소셜 회원 — 닉네임 입력(이때 가입). 소셜 인증은 이미 끝났고 계정은 아직 없음. */
@Composable
private fun SocialNicknameStep(state: AuthUiState, vm: AuthViewModel) {
    Text(
        text = "어떻게 불러드릴까요?",
        style = MutterText.title,
        color = Ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "받는 사람에게 보일 이름이에요",
        style = MutterText.caption,
        color = InkSoft,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    MutterField(
        value = state.nickname,
        onValueChange = vm::onNicknameChange,
        placeholder = "닉네임",
        keyboardType = KeyboardType.Text,
    )
    MutterButton(
        text = "시작하기",
        onClick = vm::submitSocialNickname,
        isLoading = state.isLoading,
        enabled = vm.isNicknameValid,
    )
    MutterButton(
        text = "다른 방법으로 로그인",
        onClick = vm::backToEmail,
        style = MutterButtonStyle.Ghost,
    )
}

// ─── Social ─────────────────────────────────────────────────────────────────

@Composable
private fun SocialSection(
    enabled: Boolean,
    onGoogle: () -> Unit,
    onKakao: () -> Unit,
    onApple: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("또는", style = MutterText.caption, color = InkFaint)
        SocialButton(
            text = "Google로 계속하기",
            iconRes = DsR.drawable.social_google,
            container = Surface,
            content = Ink,
            borderColor = Hairline,
            enabled = enabled,
            onClick = onGoogle,
        )
        SocialButton(
            text = "카카오로 계속하기",
            iconRes = DsR.drawable.social_kakao,
            container = KakaoYellow,
            content = KakaoLabel,
            enabled = enabled,
            onClick = onKakao,
        )
        SocialButton(
            text = "Apple로 계속하기",
            iconRes = DsR.drawable.social_apple,
            container = AppleBlack,
            content = Color.White,
            tintIcon = Color.White,
            enabled = enabled,
            onClick = onApple,
        )
    }
}

@Composable
private fun SocialButton(
    text: String,
    iconRes: Int,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    borderColor: Color? = null,
    tintIcon: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp), clip = false)
            .background(container, RoundedCornerShape(12.dp))
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (tintIcon != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tintIcon,
                modifier = Modifier.size(20.dp),
            )
        } else {
            MutterImage(id = iconRes, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MutterText.bodyMediumBold, color = content)
    }
}

// ─── Field ──────────────────────────────────────────────────────────────────

/** iOS AuthView의 입력 필드(아이보리 배경 + 라운드) 대응. */
@Composable
private fun MutterField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = MutterText.bodyMedium, color = InkFaint) },
        singleLine = true,
        textStyle = MutterText.bodyMedium,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
}
