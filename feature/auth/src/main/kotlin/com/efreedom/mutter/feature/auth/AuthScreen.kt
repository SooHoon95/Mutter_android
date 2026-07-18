package com.efreedom.mutter.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 이메일 OTP 로그인 화면. 인증 성공 시 onAuthenticated 콜백. */
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.authenticated) {
        onAuthenticated()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Mutter", style = MaterialTheme.typography.headlineMedium)
        Text("이메일로 로그인", style = MaterialTheme.typography.bodyMedium)

        when (state.step) {
            AuthStep.ENTER_EMAIL -> {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("이메일") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::requestCode,
                    enabled = !state.isLoading && state.email.contains("@"),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("인증코드 받기")
                }
            }

            AuthStep.ENTER_CODE -> {
                Text("${state.email} 로 보낸 6자리 코드를 입력하세요")
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCodeChange,
                    label = { Text("인증코드") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::verifyCode,
                    enabled = !state.isLoading && state.code.length >= 6,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("확인")
                }
                TextButton(onClick = viewModel::backToEmail, enabled = !state.isLoading) {
                    Text("이메일 다시 입력")
                }
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        }
        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
