package com.eggplant.detector.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eggplant.detector.AppViewModel
import com.eggplant.detector.data.DiseaseData
import com.eggplant.detector.ui.camera.CameraPage
import com.eggplant.detector.ui.history.HistoryDetailPage
import com.eggplant.detector.ui.history.HistoryPage
import com.eggplant.detector.ui.home.HomePage
import com.eggplant.detector.ui.library.DiseaseDetailPage
import com.eggplant.detector.ui.library.LibraryPage
import com.eggplant.detector.ui.result.ResultPage
import com.eggplant.detector.ui.settings.SettingsPage

private val bottomRoutes = setOf(Routes.HOME, Routes.LIBRARY, Routes.HISTORY, Routes.SETTINGS)

@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val history by viewModel.history.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(currentRoute = currentRoute) { route ->
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
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp),
        ) {
            composable(Routes.HOME) {
                HomePage(
                    viewModel = viewModel,
                    onScan = { navController.navigate(Routes.CAMERA) },
                    onLibrary = { navController.navigate(Routes.LIBRARY) },
                    onHistory = { navController.navigate(Routes.HISTORY) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryPage(onDiseaseClick = { navController.navigate(Routes.diseaseDetail(it.id)) })
            }
            composable(Routes.DISEASE_DETAIL) { entry ->
                val disease = entry.arguments?.getString("diseaseId")?.let(DiseaseData::byId)
                DiseaseDetailPage(disease = disease, onBack = navController::popBackStack)
            }
            composable(Routes.CAMERA) {
                CameraPage(
                    onBack = navController::popBackStack,
                    onCapture = {
                        viewModel.detectCapture()
                        navController.navigate(Routes.RESULT)
                    },
                    onGallery = {
                        viewModel.detectGallery()
                        navController.navigate(Routes.RESULT)
                    },
                )
            }
            composable(Routes.RESULT) {
                ResultPage(
                    viewModel = viewModel,
                    title = "Scan Result",
                    onBack = navController::popBackStack,
                    onSave = {
                        viewModel.saveCurrentResult()
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
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
                val result = history.firstOrNull { it.id == id }
                HistoryDetailPage(result = result, onBack = navController::popBackStack)
            }
            composable(Routes.SETTINGS) {
                SettingsPage(viewModel = viewModel)
            }
        }
    }
}
