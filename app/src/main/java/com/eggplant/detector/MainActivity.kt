package com.eggplant.detector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eggplant.detector.navigation.AppNavigation
import com.eggplant.detector.theme.EggplantDetectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EggplantDetectorApp() }
    }
}

@Composable
fun EggplantDetectorApp(appViewModel: AppViewModel = viewModel()) {
    val theme by appViewModel.themePreference.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (theme) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> systemDark
    }
    EggplantDetectorTheme(darkTheme = darkTheme) {
        AppNavigation(viewModel = appViewModel)
    }
}
