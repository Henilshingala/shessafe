package com.womensafety.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.womensafety.app.ui.screens.home.HomeScreen
import com.womensafety.app.ui.screens.onboarding.OnboardingScreen
import com.womensafety.app.ui.screens.contacts.ManageContactsScreen
import com.womensafety.app.ui.screens.settings.SettingsScreen
import com.womensafety.app.viewmodel.AppViewModel

@Composable
fun AppNavigation(
    isOnboardingComplete: Boolean,
    viewModel: AppViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (isOnboardingComplete) "home" else "onboarding"
    ) {
        composable("onboarding") {
            OnboardingScreen(
                viewModel = viewModel,
                onOnboardingComplete = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToContacts = { navController.navigate("contacts") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("contacts") {
            ManageContactsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
