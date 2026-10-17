package com.womensafety.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.womensafety.app.ui.navigation.AppNavigation
import com.womensafety.app.ui.theme.WomenSafetyAppTheme
import com.womensafety.app.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WomenSafetyAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: AppViewModel = viewModel()
                    val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsState()

                    LaunchedEffect(Unit) {
                        viewModel.checkOnboardingStatus()
                    }

                    AppNavigation(
                        isOnboardingComplete = isOnboardingComplete,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
