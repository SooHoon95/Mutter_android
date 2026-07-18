package com.efreedom.mutter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.core.designsystem.theme.Gold
import com.efreedom.mutter.core.designsystem.theme.MutterTheme
import com.efreedom.mutter.feature.auth.AuthScreen
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

                when (loggedIn) {
                    null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Gold)
                    }
                    false -> AuthScreen(onAuthenticated = { appViewModel.refresh() })
                    true -> MutterNavHost(onSignedOut = { appViewModel.signOut() })
                }
            }
        }
    }
}
