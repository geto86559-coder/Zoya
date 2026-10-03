package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.presentation.screens.LiveVoiceScreen
import com.example.presentation.screens.OnboardingScreen
import com.example.presentation.screens.PrivacySettingsScreen
import com.example.presentation.viewmodels.ZoyaViewModel
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.ZoyaTheme

class MainActivity : ComponentActivity() {

    private val zoyaViewModel: ZoyaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ZoyaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VoidBlack
                ) {
                    ZoyaAppNavigation(viewModel = zoyaViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        zoyaViewModel.refreshPermissions()
    }
}

object ZoyaRoutes {
    const val ROUTE_ONBOARDING = "onboarding"
    const val ROUTE_LIVE_VOICE = "live_voice"
    const val ROUTE_SETTINGS = "settings"
}

@Composable
fun ZoyaAppNavigation(viewModel: ZoyaViewModel) {
    val navController = rememberNavController()
    val isOnboardingDone = remember { viewModel.privacyManager.isOnboardingCompleted() }

    val startDestination = if (isOnboardingDone) {
        ZoyaRoutes.ROUTE_LIVE_VOICE
    } else {
        ZoyaRoutes.ROUTE_ONBOARDING
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(ZoyaRoutes.ROUTE_ONBOARDING) {
            OnboardingScreen(
                permissionManager = viewModel.permissionManager,
                privacyManager = viewModel.privacyManager,
                onCompleteOnboarding = {
                    navController.navigate(ZoyaRoutes.ROUTE_LIVE_VOICE) {
                        popUpTo(ZoyaRoutes.ROUTE_ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(ZoyaRoutes.ROUTE_LIVE_VOICE) {
            LiveVoiceScreen(
                viewModel = viewModel,
                onNavigateToSettings = {
                    navController.navigate(ZoyaRoutes.ROUTE_SETTINGS)
                }
            )
        }

        composable(ZoyaRoutes.ROUTE_SETTINGS) {
            val currentApiKey = viewModel.currentApiKey.collectAsState()
            PrivacySettingsScreen(
                privacyManager = viewModel.privacyManager,
                permissionManager = viewModel.permissionManager,
                currentApiKey = currentApiKey.value,
                onSaveApiKey = { key -> viewModel.saveApiKey(key) },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
