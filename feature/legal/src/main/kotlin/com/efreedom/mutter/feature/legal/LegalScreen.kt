package com.efreedom.mutter.feature.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkMid
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText

// ─── 권리침해 신고 화면 ───────────────────────────────────────────────────────

/** 권리침해 신고 폼 화면. 제출 완료 시 완료 상태 표시. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakedownScreen(
    modifier: Modifier = Modifier,
    viewModel: LegalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        // navigationIcon 없음 — 화면 이탈은 시스템 뒤로(제스처/버튼)로만 한다.
        // 제목은 페이지 이름이라 남긴다.
        TopAppBar(
            title = { Text(text = "권리침해 신고", style = MutterText.headline, color = Ink) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Ivory),
        )

        if (state.submitted) {
            TakedownSubmittedView()
        } else {
            TakedownForm(
                state = state,
                onClaimantChange = viewModel::onClaimantChange,
                onContactChange = viewModel::onContactChange,
                onTrackRefChange = viewModel::onTrackRefChange,
                onReasonChange = viewModel::onReasonChange,
                onSubmit = viewModel::submit,
            )
        }
    }
}

@Composable
private fun TakedownForm(
    state: TakedownUiState,
    onClaimantChange: (String) -> Unit,
    onContactChange: (String) -> Unit,
    onTrackRefChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "저작권 침해 신고", style = MutterText.titleLarge, color = Ink)
            Text(
                text = "권리침해가 확인된 콘텐츠를 신고해 주세요. 검토 후 조치하겠습니다.",
                style = MutterText.bodyMedium,
                color = InkSoft,
            )

            OutlinedTextField(
                value = state.claimant,
                onValueChange = onClaimantChange,
                label = { Text("신고인 (성명 또는 단체명)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.contact,
                onValueChange = onContactChange,
                label = { Text("연락처 (이메일)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.trackRef,
                onValueChange = onTrackRefChange,
                label = { Text("침해 트랙 URL (선택)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "침해 내용", style = MutterText.captionBold, color = InkSoft)
                OutlinedTextField(
                    value = state.reason,
                    onValueChange = onReasonChange,
                    label = { Text("침해 내용을 구체적으로 작성해 주세요") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let { message ->
                Text(text = message, style = MutterText.caption, color = Danger)
            }

            MutterButton(
                text = "신고 제출",
                onClick = onSubmit,
                enabled = state.isValid,
                isLoading = state.isLoading,
            )
        }
    }
}

@Composable
private fun TakedownSubmittedView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            MutterIcon(id = DsR.drawable.check_circle, contentDescription = null, tint = Gold, size = 44.dp)
            Text(text = "신고가 접수됐어요", style = MutterText.title, color = Ink)
            Text(
                text = "검토 후 조치 결과를 이메일로 알려드릴게요.",
                style = MutterText.bodyMedium,
                color = InkSoft,
            )
        }
    }
}

// ─── 법적 문서 화면 ──────────────────────────────────────────────────────────

private const val TERMS_PLACEHOLDER = """이용약관

본 약관은 Mutter(이하 "서비스")를 이용함에 있어 필요한 사항을 규정합니다.

제1조 (목적)
본 약관은 서비스 이용 조건 및 절차, 이용자와 회사의 권리·의무 및 책임 사항을 규정함을 목적으로 합니다.

제2조 (이용약관의 효력 및 변경)
본 약관은 서비스를 이용하고자 하는 모든 이용자에게 적용됩니다.

제3조 (서비스의 제공 및 변경)
회사는 안정적인 서비스 제공을 위해 최선을 다합니다.

(자세한 내용은 서비스 내 고지 사항을 참조하세요.)"""

/** 약관 정적 문서 화면. 개인정보 처리방침은 웹 정식본을 연다(AppLink.privacyPolicy). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocScreen(
    title: String,
    text: String = TERMS_PLACEHOLDER,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        // navigationIcon 없음 — 화면 이탈은 시스템 뒤로(제스처/버튼)로만 한다.
        TopAppBar(
            title = { Text(text = title, style = MutterText.headline, color = Ink) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Ivory),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = title, style = MutterText.titleLarge, color = Ink)
                Text(text = text, style = MutterText.bodyMedium, color = InkMid)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
