package com.eggplant.detector.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eggplant.detector.app.EggplantAppViewModel
import com.eggplant.detector.feature.camera.CameraScreen
import com.eggplant.detector.feature.history.ScanHistoryDetailsScreen
import com.eggplant.detector.feature.history.ScanHistoryScreen
import com.eggplant.detector.feature.home.HomeScreen
import com.eggplant.detector.feature.library.DiseaseDetailsScreen
import com.eggplant.detector.feature.library.DiseaseLibraryScreen
import com.eggplant.detector.feature.information.AboutScreen
import com.eggplant.detector.feature.information.HelpScreen
import com.eggplant.detector.feature.information.OfflineStatusScreen
import com.eggplant.detector.feature.information.PrivacyScreen
import com.eggplant.detector.feature.information.ScanTipsScreen
import com.eggplant.detector.feature.notifications.NotificationsScreen
import com.eggplant.detector.feature.result.DetectionResultScreen
import com.eggplant.detector.feature.settings.SettingsScreen
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.R

private val bottomRoutes = setOf(Routes.HOME, Routes.LIBRARY, Routes.HISTORY, Routes.SETTINGS)

@Composable
fun EggplantNavigation(viewModel: EggplantAppViewModel) {
    val navController = rememberNavController()
    val history by viewModel.history.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val currentResult by viewModel.currentResult.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomRoutes
    val homeListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun navigateTopLevel(route: String) {
        if (route == currentRoute) {
            if (route == Routes.HOME) scope.launch { homeListState.scrollToItem(0) }
            return
        }
        if (route == Routes.HOME) {
            val returnedToHome = navController.popBackStack(Routes.HOME, inclusive = false)
            if (!returnedToHome) {
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            }
            scope.launch { homeListState.scrollToItem(0) }
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(currentRoute = currentRoute) { route ->
                    if (route == Routes.CAMERA) {
                        navController.navigate(route)
                    } else {
                        navigateTopLevel(route)
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onScan = { navController.navigate(Routes.CAMERA) },
                    onLibrary = { navigateTopLevel(Routes.LIBRARY) },
                    onHistory = { navigateTopLevel(Routes.HISTORY) },
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onCareGuide = { navController.navigate(Routes.SCAN_TIPS) },
                    onOfflineUse = { navController.navigate(Routes.OFFLINE_STATUS) },
                    onLastScan = { navController.navigate(Routes.historyDetail(it)) },
                    listState = homeListState,
                )
            }
            composable(Routes.LIBRARY) {
                DiseaseLibraryScreen(
                    diseases = catalog,
                    onDiseaseClick = { navController.navigate(Routes.diseaseDetail(it.id)) },
                    onHelp = { navController.navigate(Routes.HELP) },
                )
            }
            composable(Routes.DISEASE_DETAIL) { entry ->
                val disease = entry.arguments?.getString("diseaseId")?.let { id -> catalog.firstOrNull { it.id == id } }
                DiseaseDetailsScreen(disease = disease, onBack = navController::popBackStack)
            }
            composable(Routes.CAMERA) {
                CameraScreen(
                    viewModel = viewModel,
                    onBack = navController::popBackStack,
                    onResult = {
                        navController.navigate(Routes.RESULT)
                    },
                    onSaved = {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.RESULT) {
                DetectionResultScreen(
                    viewModel = viewModel,
                    title = stringResource(R.string.scan_result),
                    onBack = navController::popBackStack,
                    onSave = {
                        viewModel.saveCurrentResult { saved ->
                            if (saved) {
                                navController.navigate(Routes.HISTORY) {
                                    popUpTo(Routes.HOME)
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    onScanAgain = {
                        navController.navigate(Routes.CAMERA) {
                            popUpTo(Routes.CAMERA) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HISTORY) {
                ScanHistoryScreen(viewModel = viewModel) { result ->
                    viewModel.openHistoryResult(result)
                    navController.navigate(Routes.historyDetail(result.id))
                }
            }
            composable(Routes.HISTORY_DETAIL) { entry ->
                val id = entry.arguments?.getString("resultId")
                val result = history.firstOrNull { it.id == id } ?: currentResult?.takeIf { it.id == id }
                ScanHistoryDetailsScreen(result = result, onBack = navController::popBackStack)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onModelStatus = { navController.navigate(Routes.OFFLINE_STATUS) },
                    onScanTips = { navController.navigate(Routes.SCAN_TIPS) },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onHelp = { navController.navigate(Routes.HELP) },
                    onAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(viewModel = viewModel, onBack = navController::popBackStack)
            }
            composable(Routes.SCAN_TIPS) { ScanTipsScreen(onBack = navController::popBackStack) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = navController::popBackStack) }
            composable(Routes.HELP) { HelpScreen(onBack = navController::popBackStack) }
            composable(Routes.ABOUT) { AboutScreen(onBack = navController::popBackStack) }
            composable(Routes.OFFLINE_STATUS) { OfflineStatusScreen(onBack = navController::popBackStack) }
        }
    }
}
