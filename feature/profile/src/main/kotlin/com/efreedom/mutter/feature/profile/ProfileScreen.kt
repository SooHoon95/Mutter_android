package com.efreedom.mutter.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Hairline
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface
import kotlinx.coroutines.delay

/** 프로필 탭 화면. 닉네임 수정, 로그아웃, 계정 삭제. */
@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 로그아웃/계정 삭제 완료 시 탐색
    if (state.signedOut) {
        onSignedOut()
    }

    // 저장 완료 토스트 자동 해제
    if (state.savedToast) {
        LaunchedEffect(Unit) {
            delay(1_500)
            viewModel.onSavedToastShown()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    var isEditingNickname by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 아바타 헤더 — 닉네임 첫 글자
            AvatarHeader(nickname = state.nickname)

            Spacer(modifier = Modifier.height(32.dp))

            // 설정 카드
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(width = 0.5.dp, color = Hairline, shape = RoundedCornerShape(16.dp)),
            ) {
                if (isEditingNickname) {
                    NicknameEditRow(
                        nickname = state.nickname,
                        isLoading = state.isLoading,
                        onNicknameChange = viewModel::onNicknameChange,
                        onSave = {
                            viewModel.saveNickname()
                            isEditingNickname = false
                        },
                    )
                } else {
                    SettingsRow(
                        icon = { Icon(Icons.Default.Person, contentDescription = null, tint = InkSoft, modifier = Modifier.size(20.dp)) },
                        label = "닉네임",
                        value = state.nickname.ifEmpty { "—" },
                        onClick = { isEditingNickname = true },
                    )
                }

                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))

                SettingsRow(
                    icon = { },
                    label = "기본 편지지",
                    value = "봄날",
                    onClick = null,
                )

                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))

                SettingsRow(
                    icon = { },
                    label = "비밀번호",
                    value = "사용 중",
                    onClick = null,
                )
            }

            // 에러 메시지
            state.error?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = message, style = MutterText.caption, color = Danger)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 문의 행 (mailto 생략 — 표시만)
            Text(
                text = "문의: dkehskeh@gmail.com",
                style = MutterText.bodyMedium,
                color = InkSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 로그아웃 버튼
            MutterButton(
                text = "로그아웃",
                onClick = { viewModel.signOut() },
                style = MutterButtonStyle.Ghost,
                isLoading = state.isLoading,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 계정 삭제 텍스트 버튼
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(
                    text = "계정 삭제",
                    style = MutterText.bodyLargeBold,
                    color = Danger,
                )
            }
        }

        // 저장 완료 토스트
        if (state.savedToast) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
                containerColor = Ink,
                contentColor = Ivory,
            ) {
                Text(text = "닉네임이 저장됐어요.", style = MutterText.bodyMediumBold)
            }
        }
    }

    // 계정 삭제 확인 다이얼로그
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("계정을 삭제할까요?") },
            text = { Text("삭제하면 모든 편지와 데이터가 영구히 사라져요.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAccount()
                }) {
                    Text("삭제", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            },
        )
    }
}

// ─── 아바타 헤더 ──────────────────────────────────────────────────────────────

@Composable
private fun AvatarHeader(nickname: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GoldSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = nickname.firstOrNull()?.uppercase() ?: "M",
                style = MutterText.titleLarge,
                color = GoldDeep,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = nickname.ifEmpty { "닉네임" },
            style = MutterText.display,
            color = Ink,
        )
    }
}

// ─── 닉네임 인라인 편집 행 ────────────────────────────────────────────────────

@Composable
private fun NicknameEditRow(
    nickname: String,
    isLoading: Boolean,
    onNicknameChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = nickname,
        onValueChange = onNicknameChange,
        label = { Text("닉네임") },
        singleLine = true,
        trailingIcon = {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onSave, enabled = nickname.isNotBlank()) {
                    Icon(Icons.Default.Check, contentDescription = "저장", tint = Gold)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (nickname.isNotBlank()) onSave() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ─── 설정 행 ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }

        Spacer(modifier = Modifier.size(12.dp))

        Text(text = label, style = MutterText.bodyLarge, color = Ink, modifier = Modifier.weight(1f))

        Text(text = value, style = MutterText.bodyMedium, color = InkSoft)

        if (onClick != null) {
            Spacer(modifier = Modifier.size(8.dp))
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = InkFaint, modifier = Modifier.size(16.dp))
        }
    }
}
