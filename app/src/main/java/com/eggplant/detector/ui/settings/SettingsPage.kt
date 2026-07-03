package com.eggplant.detector.ui.settings

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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
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
import androidx.compose.ui.unit.dp
import com.eggplant.detector.AppViewModel
import com.eggplant.detector.BuildConfig
import com.eggplant.detector.ThemePreference
import com.eggplant.detector.UnitPreference
import com.eggplant.detector.components.SettingsRow

private data class InfoDialog(val title: String, val body: String)

@Composable
fun SettingsPage(viewModel: AppViewModel) {
    val theme by viewModel.themePreference.collectAsState()
    val units by viewModel.unitPreference.collectAsState()
    var infoDialog by remember { mutableStateOf<InfoDialog?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showUnitsDialog by remember { mutableStateOf(false) }

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
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Personalize your local app experience",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            SettingsRow("Language", Icons.Outlined.Language, "English") {
                infoDialog = InfoDialog("Language", "English is the only language included in this UI milestone.")
            }
            HorizontalDivider()
            SettingsRow("Theme", Icons.Outlined.DarkMode, theme.displayName) { showThemeDialog = true }
            HorizontalDivider()
            SettingsRow("Units", Icons.Outlined.Straighten, units.displayName) { showUnitsDialog = true }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            SettingsRow("Offline Model Status", Icons.Outlined.Memory, "Mock mode") {
                infoDialog = InfoDialog("Offline Model Status", "YOLO 26 Medium is not installed. Deterministic mock detection is active.")
            }
            HorizontalDivider()
            SettingsRow("Scan Quality Tips", Icons.Outlined.PhotoCamera) {
                infoDialog = InfoDialog("Scan Quality Tips", "Use one clear leaf, even lighting, a steady angle, and a simple background.")
            }
            HorizontalDivider()
            SettingsRow("Data & Privacy", Icons.Outlined.PrivacyTip) {
                infoDialog = InfoDialog("Data & Privacy", "No photos or history leave this device. This build uses memory only and sends no network requests.")
            }
            HorizontalDivider()
            SettingsRow("Export History", Icons.Outlined.Download) {
                infoDialog = InfoDialog("Export History", "Export is unavailable in this mock build. No file, cloud, email, or share action was performed.")
            }
            HorizontalDivider()
            SettingsRow("Help & FAQ", Icons.AutoMirrored.Outlined.HelpOutline) {
                infoDialog = InfoDialog("Help & FAQ", "This app demonstrates the UI for eggplant problem detection. Capture and gallery actions return fixed mock results.")
            }
            HorizontalDivider()
            SettingsRow("About App", Icons.Outlined.Info) {
                infoDialog = InfoDialog("About App", "Eggplant Disease Detector is a Kotlin/Compose thesis UI prototype prepared for future on-device model integration.")
            }
        }
        Text(
            "App version ${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = "Theme",
            options = ThemePreference.entries.map { it.displayName },
            selected = theme.displayName,
            onSelect = { choice ->
                viewModel.setTheme(ThemePreference.entries.first { it.displayName == choice })
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showUnitsDialog) {
        ChoiceDialog(
            title = "Units",
            options = UnitPreference.entries.map { it.displayName },
            selected = units.displayName,
            onSelect = { choice ->
                viewModel.setUnits(UnitPreference.entries.first { it.displayName == choice })
                showUnitsDialog = false
            },
            onDismiss = { showUnitsDialog = false },
        )
    }
    infoDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text(dialog.title) },
            text = { Text(dialog.body) },
            confirmButton = { TextButton(onClick = { infoDialog = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (option == selected) "✓  $option" else option)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
