package com.eggplant.detector.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eggplant.detector.app.EggplantAppViewModel
import com.eggplant.detector.BuildConfig
import com.eggplant.detector.app.LanguagePreference
import com.eggplant.detector.R
import com.eggplant.detector.app.ThemePreference
import com.eggplant.detector.core.ui.components.SettingsRow
import com.eggplant.detector.core.ui.components.SettingsSwitchRow

@Composable
fun SettingsScreen(
    viewModel: EggplantAppViewModel,
    onModelStatus: () -> Unit,
    onScanTips: () -> Unit,
    onPrivacy: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
) {
    val theme by viewModel.themePreference.collectAsState()
    val language by viewModel.languagePreference.collectAsState()
    val autoSaveEnabled by viewModel.autoSaveEnabled.collectAsState()
    val detectHealthyLeafEnabled by viewModel.detectHealthyLeafEnabled.collectAsState()
    val detectHealthyPlantEnabled by viewModel.detectHealthyPlantEnabled.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val languageOptions = listOf(
        LanguagePreference.ENGLISH to stringResource(R.string.english),
        LanguagePreference.FILIPINO to stringResource(R.string.filipino),
    )
    val themeOptions = listOf(
        ThemePreference.LIGHT to stringResource(R.string.light),
        ThemePreference.DARK to stringResource(R.string.dark),
        ThemePreference.SYSTEM to stringResource(R.string.system_default),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            SettingsRow(stringResource(R.string.language), Icons.Outlined.Language, languageLabel(language)) { showLanguageDialog = true }
            HorizontalDivider()
            SettingsRow(stringResource(R.string.theme), Icons.Outlined.DarkMode, themeLabel(theme)) { showThemeDialog = true }
            HorizontalDivider()
            SettingsRow(
                stringResource(R.string.history_saving),
                Icons.Outlined.Save,
                stringResource(if (autoSaveEnabled) R.string.automatic_save else R.string.manual_save),
            ) { viewModel.setAutoSave(!autoSaveEnabled) }
            HorizontalDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.detect_healthy_leaf),
                description = stringResource(R.string.detect_healthy_leaf_description),
                icon = Icons.Outlined.Eco,
                checked = detectHealthyLeafEnabled,
                onCheckedChange = viewModel::setDetectHealthyLeaf,
                enabledLabel = stringResource(R.string.on),
                disabledLabel = stringResource(R.string.off),
            )
            HorizontalDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.detect_healthy_plant),
                description = stringResource(R.string.detect_healthy_plant_description),
                icon = Icons.Outlined.Yard,
                checked = detectHealthyPlantEnabled,
                onCheckedChange = viewModel::setDetectHealthyPlant,
                enabledLabel = stringResource(R.string.on),
                disabledLabel = stringResource(R.string.off),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            SettingsRow(stringResource(R.string.detection_status), Icons.Outlined.Memory, stringResource(R.string.model_pending), onModelStatus)
            HorizontalDivider()
            SettingsRow(stringResource(R.string.scan_quality_tips), Icons.Outlined.PhotoCamera, onClick = onScanTips)
            HorizontalDivider()
            SettingsRow(stringResource(R.string.data_privacy), Icons.Outlined.PrivacyTip, onClick = onPrivacy)
            HorizontalDivider()
            SettingsRow(stringResource(R.string.help_faq), Icons.AutoMirrored.Outlined.HelpOutline, onClick = onHelp)
            HorizontalDivider()
            SettingsRow(stringResource(R.string.about_app), Icons.Outlined.Info, onClick = onAbout)
        }
        Text(
            stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            options = languageOptions,
            selected = language,
            onSelect = { choice ->
                viewModel.setLanguage(choice)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.theme),
            options = themeOptions,
            selected = theme,
            onSelect = { choice ->
                viewModel.setTheme(choice)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    TextButton(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (value == selected) "✓  $label" else label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun languageLabel(preference: LanguagePreference): String = when (preference) {
    LanguagePreference.ENGLISH -> stringResource(R.string.english)
    LanguagePreference.FILIPINO -> stringResource(R.string.filipino)
}

@Composable
private fun themeLabel(preference: ThemePreference): String = when (preference) {
    ThemePreference.LIGHT -> stringResource(R.string.light)
    ThemePreference.DARK -> stringResource(R.string.dark)
    ThemePreference.SYSTEM -> stringResource(R.string.system_default)
}
