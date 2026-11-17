package com.efreedom.mutter.feature.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText

// ─── 권리침해 신고 화면 ───────────────────────────────────────────────────────

/** 권리침해 신고 폼 화면(iOS TakedownView). 제출 완료 시 완료 상태 표시. */
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
        // 상단 바 제목은 iOS 내비게이션 바와 같은 "문의하기"(legal.contact).
        TopAppBar(
            title = { Text(text = stringResource(R.string.legal_contact), style = MutterText.headline, color = Ink) },
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
            Text(text = stringResource(R.string.legal_takedown_title), style = MutterText.titleLarge, color = Ink)
            Text(
                text = stringResource(R.string.legal_takedown_subtitle),
                style = MutterText.bodyMedium,
                color = InkSoft,
            )

            OutlinedTextField(
                value = state.claimant,
                onValueChange = onClaimantChange,
                label = { Text(stringResource(R.string.legal_takedown_claimant)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.contact,
                onValueChange = onContactChange,
                label = { Text(stringResource(R.string.legal_takedown_contact)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.trackRef,
                onValueChange = onTrackRefChange,
                label = { Text(stringResource(R.string.legal_takedown_target)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // iOS처럼 머리글 "사유" + 라벨·플레이스홀더 없는 여러 줄 입력칸.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.legal_takedown_reason), style = MutterText.captionBold, color = InkSoft)
                OutlinedTextField(
                    value = state.reason,
                    onValueChange = onReasonChange,
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let { message ->
                Text(text = message, style = MutterText.caption, color = GoldDeep)
            }

            MutterButton(
                text = stringResource(R.string.legal_takedown_submit),
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
            Text(text = stringResource(R.string.legal_takedown_done_title), style = MutterText.title, color = Ink)
            Text(
                text = stringResource(R.string.legal_takedown_done_detail),
                style = MutterText.bodyMedium,
                color = InkSoft,
            )
        }
    }
}
