package com.efreedom.mutter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.feature.auth.AuthScreen
import com.efreedom.mutter.ui.theme.MutterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MutterTheme {
                val appViewModel: AppViewModel = hiltViewModel()
                val loggedIn by appViewModel.loggedIn.collectAsStateWithLifecycle()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (loggedIn) {
                            null -> CircularProgressIndicator()
                            false -> AuthScreen(onAuthenticated = { appViewModel.refresh() })
                            true -> LoggedIn(onSignOut = { appViewModel.signOut() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoggedIn(onSignOut: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("로그인됨 — Mutter Android", style = MaterialTheme.typography.titleMedium)
        Text("다음 단계: 홈·작성·뷰어 이식", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onSignOut) { Text("로그아웃") }
    }
}
