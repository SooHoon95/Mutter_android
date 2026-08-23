@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.MutterIcon
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

/**
 * 프로필 탭 화면. 닉네임 수정, 법적 고지·신고, 로그아웃, 계정 삭제.
 *
 * [onReport]는 권리침해 신고 화면으로의 유일한 진입점이다. 편지 본문은 사용자 생성 콘텐츠라
 * 스토어 정책상 인앱 신고 수단이 도달 가능해야 하므로 이 행을 제거하면 안 된다.
 */
@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    onReport: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 로그아웃/계정 삭제 완료 시 탐색 — LaunchedEffect로 리컴포지션마다 재호출되는 것을 막는다.
    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignedOut()
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

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::load,
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
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
                        icon = { MutterIcon(id = DsR.drawable.person, contentDescription = null, tint = InkSoft, size = 20.dp) },
                        label = "닉네임",
                        value = state.nickname.ifEmpty { "—" },
                        onClick = { isEditingNickname = true },
                    )
                }

                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))

                SettingsRow(
                    icon = { MutterIcon(id = DsR.drawable.mood_tag, contentDescription = null, tint = InkSoft, size = 20.dp) },
                    label = "기본 편지지",
                    value = "봄날",
                    onClick = null,
                )

                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))

                SettingsRow(
                    icon = { MutterIcon(id = DsR.drawable.lock, contentDescription = null, tint = InkSoft, size = 20.dp) },
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

            Spacer(modifier = Modifier.height(20.dp))

            // 법적 고지·신고 카드 — 세 화면 모두 여기서만 진입한다.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(width = 0.5.dp, color = Hairline, shape = RoundedCornerShape(16.dp)),
            ) {
                SettingsRow(
                    icon = { MutterIcon(id = DsR.drawable.note, contentDescription = null, tint = InkSoft, size = 20.dp) },
                    label = "권리침해 신고",
                    value = "",
                    onClick = onReport,
                )

                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))

                SettingsRow(
                    icon = { MutterIcon(id = DsR.drawable.paper, contentDescription = null, tint = InkSoft, size = 20.dp) },
                    label = "이용약관",
                    value = "",
                    onClick = onTerms,
                )

                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))

                SettingsRow(
                    icon = { MutterIcon(id = DsR.drawable.lock, contentDescription = null, tint = InkSoft, size = 20.dp) },
                    label = "개인정보처리방침",
                    value = "",
                    onClick = onPrivacy,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 문의하기 — iOS ProfileView 대응. 메일 앱을 열고, 없으면 이메일 복사 + 안내 토스트.
            MutterButton(
                text = "문의하기",
                onClick = { openContactMail(context) },
                style = MutterButtonStyle.Ghost,
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

private const val CONTACT_EMAIL = "dkehskeh@gmail.com"

/** 문의 메일 열기. 메일 앱이 없으면 이메일을 클립보드에 복사하고 토스트로 안내 — iOS 폴백 대응. */
private fun openContactMail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$CONTACT_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, "Mutter 문의")
    }
    val opened = runCatching { context.startActivity(intent); true }.getOrDefault(false)
    if (!opened) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("contact_email", CONTACT_EMAIL))
        Toast.makeText(context, "메일 앱이 없어 이메일 주소를 복사했어요: $CONTACT_EMAIL", Toast.LENGTH_LONG).show()
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
                    MutterIcon(id = DsR.drawable.ic_check, contentDescription = "저장", tint = Gold)
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
            MutterIcon(id = DsR.drawable.ic_chevron_right, contentDescription = null, tint = InkFaint, size = 16.dp)
        }
    }
}
