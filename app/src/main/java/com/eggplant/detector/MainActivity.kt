package com.eggplant.detector

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eggplant.detector.navigation.AppNavigation
import com.eggplant.detector.theme.EggplantDetectorTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EggplantDetectorApp() }
    }
}

@Composable
fun EggplantDetectorApp(
    appViewModel: AppViewModel = viewModel(
        factory = AppViewModel.factory(
            (androidx.compose.ui.platform.LocalContext.current.applicationContext as EggplantApplication).repository,
        ),
    ),
) {
    val theme by appViewModel.themePreference.collectAsStateWithLifecycle()
    val language by appViewModel.languagePreference.collectAsStateWithLifecycle()
    LaunchedEffect(language) {
        val locales = LocaleListCompat.forLanguageTags(language.languageTag)
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
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
