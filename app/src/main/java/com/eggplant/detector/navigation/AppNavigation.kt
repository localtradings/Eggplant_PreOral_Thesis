package com.eggplant.detector.navigation

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
import com.eggplant.detector.AppViewModel
import com.eggplant.detector.ui.camera.CameraPage
import com.eggplant.detector.ui.history.HistoryDetailPage
import com.eggplant.detector.ui.history.HistoryPage
import com.eggplant.detector.ui.home.HomePage
import com.eggplant.detector.ui.library.DiseaseDetailPage
import com.eggplant.detector.ui.library.LibraryPage
import com.eggplant.detector.ui.info.AboutPage
import com.eggplant.detector.ui.info.HelpPage
import com.eggplant.detector.ui.info.OfflineStatusPage
import com.eggplant.detector.ui.info.PrivacyPage
import com.eggplant.detector.ui.info.ScanTipsPage
import com.eggplant.detector.ui.notifications.NotificationsPage
import com.eggplant.detector.ui.result.ResultPage
import com.eggplant.detector.ui.settings.SettingsPage
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.R

private val bottomRoutes = setOf(Routes.HOME, Routes.LIBRARY, Routes.HISTORY, Routes.SETTINGS)

@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val history by viewModel.history.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val currentResult by viewModel.currentResult.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomRoutes
    val homeListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(currentRoute = currentRoute) { route ->
                    if (route == Routes.HOME) {
                        scope.launch { homeListState.scrollToItem(0) }
                    }
                    if (route == currentRoute) {
                        return@BottomNavigationBar
                    }
                    if (route == Routes.CAMERA) {
                        navController.navigate(route)
                    } else {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
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
                HomePage(
                    viewModel = viewModel,
                    onScan = { navController.navigate(Routes.CAMERA) },
                    onLibrary = { navController.navigate(Routes.LIBRARY) },
                    onHistory = { navController.navigate(Routes.HISTORY) },
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onCareGuide = { navController.navigate(Routes.SCAN_TIPS) },
                    onOfflineUse = { navController.navigate(Routes.OFFLINE_STATUS) },
                    onLastScan = { navController.navigate(Routes.historyDetail(it)) },
                    listState = homeListState,
                )
            }
            composable(Routes.LIBRARY) {
                LibraryPage(
                    diseases = catalog,
                    onDiseaseClick = { navController.navigate(Routes.diseaseDetail(it.id)) },
                    onHelp = { navController.navigate(Routes.HELP) },
                )
            }
            composable(Routes.DISEASE_DETAIL) { entry ->
                val disease = entry.arguments?.getString("diseaseId")?.let { id -> catalog.firstOrNull { it.id == id } }
                DiseaseDetailPage(disease = disease, onBack = navController::popBackStack)
            }
            composable(Routes.CAMERA) {
                CameraPage(
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
                ResultPage(
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
                HistoryPage(viewModel = viewModel) { result ->
                    viewModel.openHistoryResult(result)
                    navController.navigate(Routes.historyDetail(result.id))
                }
            }
            composable(Routes.HISTORY_DETAIL) { entry ->
                val id = entry.arguments?.getString("resultId")
                val result = history.firstOrNull { it.id == id } ?: currentResult?.takeIf { it.id == id }
                HistoryDetailPage(result = result, onBack = navController::popBackStack)
            }
            composable(Routes.SETTINGS) {
                SettingsPage(
                    viewModel = viewModel,
                    onModelStatus = { navController.navigate(Routes.OFFLINE_STATUS) },
                    onScanTips = { navController.navigate(Routes.SCAN_TIPS) },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onHelp = { navController.navigate(Routes.HELP) },
                    onAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsPage(viewModel = viewModel, onBack = navController::popBackStack)
            }
            composable(Routes.SCAN_TIPS) { ScanTipsPage(onBack = navController::popBackStack) }
            composable(Routes.PRIVACY) { PrivacyPage(onBack = navController::popBackStack) }
            composable(Routes.HELP) { HelpPage(onBack = navController::popBackStack) }
            composable(Routes.ABOUT) { AboutPage(onBack = navController::popBackStack) }
            composable(Routes.OFFLINE_STATUS) { OfflineStatusPage(onBack = navController::popBackStack) }
        }
    }
}
