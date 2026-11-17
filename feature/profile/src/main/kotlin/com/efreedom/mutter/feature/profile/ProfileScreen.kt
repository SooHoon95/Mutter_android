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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
    /** 편지권 화면. 편지권 UI가 보이는 계정(paid_enabled || is_admin)에만 항목이 생긴다. */
    onOpenTickets: () -> Unit = {},
    /** 새 소식 안내 다시 보기. 편지권 항목과 같은 계정에만 줄이 생기고, 안내에 넣을 서버 일수(free_days·grace_days)를 넘긴다. */
    onOpenWhatsNew: (freeDays: Int, graceDays: Int) -> Unit = { _, _ -> },
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 로그아웃/계정 삭제 완료 시 탐색 — LaunchedEffect로 리컴포지션마다 재호출되는 것을 막는다.
    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignedOut()
    }

    // 저장 완료 토스트 자동 해제
    if (state.savedToast != null) {
        LaunchedEffect(state.savedToast) {
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
                        label = stringResource(R.string.profile_nickname),
                        value = state.nickname.ifEmpty { "—" },
                        onClick = { isEditingNickname = true },
                    )
                }

                // 로그인 비밀번호 — 이메일 가입 계정에만(소셜 계정은 이메일이 내부용이라 숨김). iOS처럼 닉네임 바로 아래.
                state.passwordStatus?.takeIf { state.showsLoginPassword }?.let { status ->
                    HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                    SettingsRow(
                        icon = { MutterIcon(id = DsR.drawable.lock, contentDescription = null, tint = InkSoft, size = 20.dp) },
                        label = stringResource(R.string.profile_login_password),
                        value = stringResource(
                            if (status.isSet) R.string.profile_login_password_set else R.string.profile_login_password_unset,
                        ),
                        onClick = viewModel::showPasswordDialog,
                    )
                }

                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))

                SettingsRow(
                    icon = { MutterIcon(id = DsR.drawable.mood_tag, contentDescription = null, tint = InkSoft, size = 20.dp) },
                    label = stringResource(R.string.profile_default_paper),
                    value = stringResource(R.string.profile_default_paper_value),
                    onClick = null,
                )

                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))

                // 편지 링크 암호 기본값(표시 전용) — iOS "암호 기본값 / 켜짐"과 같은 항목.
                SettingsRow(
                    icon = { MutterIcon(id = DsR.drawable.lock, contentDescription = null, tint = InkSoft, size = 20.dp) },
                    label = stringResource(R.string.profile_password_default),
                    value = stringResource(R.string.profile_on),
                    onClick = null,
                )

                state.entitlements?.takeIf { state.showsTickets }?.let { ent ->
                    HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                    SettingsRow(
                        icon = { MutterIcon(id = DsR.drawable.envelope, contentDescription = null, tint = InkSoft, size = 20.dp) },
                        label = stringResource(R.string.tickets_title),
                        value = stringResource(R.string.tickets_balance, ent.tickets),
                        onClick = onOpenTickets,
                    )
                    // 새 소식 — 편지권이 생기며 달라진 점을 다시 본다(봤는지 저장한 값은 건드리지 않는다).
                    HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                    SettingsRow(
                        icon = {
                            MutterIcon(id = DsR.drawable.envelope_open, contentDescription = null, tint = InkSoft, size = 20.dp)
                        },
                        label = stringResource(DsR.string.whatsnew_title),
                        value = "",
                        onClick = { onOpenWhatsNew(ent.freeDays, ent.graceDays) },
                    )
                }
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
                    label = stringResource(R.string.profile_report),
                    value = "",
                    onClick = onReport,
                )

                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))

                SettingsRow(
                    icon = { MutterIcon(id = DsR.drawable.paper, contentDescription = null, tint = InkSoft, size = 20.dp) },
                    label = stringResource(R.string.profile_terms),
                    value = "",
                    onClick = onTerms,
                )

                HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))

                SettingsRow(
                    icon = { MutterIcon(id = DsR.drawable.lock, contentDescription = null, tint = InkSoft, size = 20.dp) },
                    label = stringResource(R.string.profile_privacy),
                    value = "",
                    onClick = onPrivacy,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 문의하기 — iOS ProfileView 대응. 메일 앱을 열고, 없으면 이메일 복사 + 안내 토스트.
            MutterButton(
                text = stringResource(R.string.profile_contact),
                onClick = { openContactMail(context) },
                style = MutterButtonStyle.Ghost,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 로그아웃 버튼
            MutterButton(
                text = stringResource(R.string.profile_logout),
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
                    text = stringResource(R.string.profile_delete_account),
                    style = MutterText.bodyLargeBold,
                    color = Danger,
                )
            }
        }

        // 저장 완료 토스트
        state.savedToast?.let { toast ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
                containerColor = Ink,
                contentColor = Ivory,
            ) {
                val text = when (toast) {
                    ProfileSavedToast.NICKNAME -> stringResource(R.string.profile_nickname_saved)
                    ProfileSavedToast.PASSWORD -> stringResource(R.string.profile_login_password_saved)
                }
                Text(text = text, style = MutterText.bodyMediumBold)
            }
        }
    }

    if (state.isPasswordDialogVisible) {
        LoginPasswordDialog(
            isLoading = state.isSavingPassword,
            error = state.passwordError,
            onSave = viewModel::savePassword,
            onDismiss = viewModel::dismissPasswordDialog,
        )
    }

    // 계정 삭제 확인 다이얼로그
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.profile_delete_confirm)) },
            text = {
                // iOS ProfileModelData.deleteAccountDetail과 같은 조립 — 편지권이 남았으면 경고 줄을 앞에 붙인다.
                val tickets = state.entitlements?.tickets ?: 0
                val detail = stringResource(R.string.profile_delete_detail)
                Text(
                    if (tickets > 0) "${stringResource(R.string.profile_delete_tickets_warning, tickets)}\n$detail" else detail,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAccount()
                }) {
                    Text(stringResource(R.string.profile_delete), color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.profile_login_password_cancel))
                }
            },
        )
    }
}

private const val CONTACT_EMAIL = "musicletter2026@gmail.com"

/**
 * 문의 메일 열기. 제목·본문은 iOS처럼 `mailto:` 쿼리에 넣고 extra에도 함께 단다(쿼리만 읽는 앱·extra만 읽는 앱 모두 대응).
 * 메일 앱이 없으면 이메일을 클립보드에 복사하고 토스트로 안내 — iOS 폴백 대응.
 */
private fun openContactMail(context: Context) {
    val subject = context.getString(R.string.profile_contact_subject)
    val body = context.getString(R.string.profile_contact_body)
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$CONTACT_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    val opened = runCatching { context.startActivity(intent); true }.getOrDefault(false)
    if (!opened) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("contact_email", CONTACT_EMAIL))
        Toast.makeText(context, context.getString(R.string.profile_mail_fallback), Toast.LENGTH_LONG).show()
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
            text = nickname.ifEmpty { stringResource(R.string.profile_nickname) },
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
        label = { Text(stringResource(R.string.profile_nickname)) },
        singleLine = true,
        trailingIcon = {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onSave, enabled = nickname.isNotBlank()) {
                    MutterIcon(id = DsR.drawable.ic_check, contentDescription = stringResource(R.string.profile_save), tint = Gold)
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

// ─── 로그인 비밀번호 다이얼로그 ────────────────────────────────────────────────

/** 새 비밀번호 + 확인 입력. 길이·일치 검사와 서버 오류 문구는 ViewModel(AuthUseCase)이 [error]로 돌려준다. */
@Composable
private fun LoginPasswordDialog(
    isLoading: Boolean,
    error: String?,
    onSave: (password: String, confirm: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        // 저장 중에는 바깥 탭·뒤로가기로 닫히지 않는다(iOS LoginPasswordSheet 대응).
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isLoading, dismissOnClickOutside = !isLoading),
        title = { Text(stringResource(R.string.profile_login_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.profile_login_password_new)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.profile_login_password_confirm)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(text = it, style = MutterText.caption, color = Danger) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(password, confirm) },
                enabled = !isLoading && password.isNotEmpty() && confirm.isNotEmpty(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Gold, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.profile_login_password_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.profile_login_password_cancel))
            }
        },
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
