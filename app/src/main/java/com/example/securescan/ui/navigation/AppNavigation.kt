package com.example.securescan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.securescan.ui.screens.*
import com.example.securescan.viewmodel.ScannerViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object QrScanner : Screen("qr_scanner")
    object QrResult : Screen("qr_result/{encodedData}") {
        fun createRoute(encoded: String) = "qr_result/$encoded"
    }
    object SignatureExtractor : Screen("signature_extractor")
    object AddSignatureToPdf : Screen("add_signature_to_pdf")
    object PdfEditor : Screen("pdf_editor")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object FileManager : Screen("files")
    object Settings : Screen("settings")
    object CreatePdf : Screen("create_pdf")
    object PasswordPdf : Screen("password_pdf")
    object ReducePdf : Screen("reduce_pdf")
    object MergePdf : Screen("merge_pdf")
    object SplitPdf : Screen("split_pdf")
    object ExtractPages : Screen("extract_pages")
    object RotateFlipImage : Screen("rotate_flip_image")
    object ResizeImage : Screen("resize_image")
    object CropImage : Screen("crop_image")
    object RedactPdf : Screen("redact_pdf")
    object PdfViewer : Screen("pdf_viewer/{encodedUri}") {
        fun createRoute(encodedUri: String) = "pdf_viewer/$encodedUri"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) { LoginScreen(navController = navController) }
        composable(Screen.Home.route) { HomeScreen(navController = navController) }
        composable(Screen.QrScanner.route) { QRScannerScreen(navController = navController) }

        composable(
            route = Screen.QrResult.route,
            arguments = listOf(navArgument("encodedData") { type = NavType.StringType })
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("encodedData") ?: ""
            QRResultScreen(navController = navController, encodedData = encoded)
        }

        composable(Screen.SignatureExtractor.route) {
            SignatureExtractorScreen(navController = navController)
        }
        composable(Screen.AddSignatureToPdf.route) {
            AddSignatureToPdfScreen(navController = navController)
        }
        composable(Screen.PdfEditor.route) {
            PdfEditorScreen(navController = navController)
        }

        composable(Screen.Camera.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val viewModel: ScannerViewModel = hiltViewModel(parentEntry)
            CameraScreen(navController = navController, viewModel = viewModel)
        }

        composable(Screen.FileManager.route) {
            FileManagerScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }

        composable(Screen.CreatePdf.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val viewModel: ScannerViewModel = hiltViewModel(parentEntry)
            CreatePdfScreen(navController = navController, viewModel = viewModel)
        }

        composable(Screen.PasswordPdf.route) { PasswordPdfScreen(navController = navController) }
        composable(Screen.ReducePdf.route) { ReducePdfScreen(navController = navController) }
        composable(Screen.MergePdf.route) { MergePdfScreen(navController = navController) }
        composable(Screen.SplitPdf.route) { SplitPdfScreen(navController = navController) }
        composable(Screen.ExtractPages.route) { ExtractPagesScreen(navController = navController) }
        composable(Screen.RotateFlipImage.route) { RotateFlipImageScreen(navController = navController) }
        composable(Screen.ResizeImage.route) { ResizeImageScreen(navController = navController) }
        composable(Screen.CropImage.route) { CropImageScreen(navController = navController) }
        composable(Screen.RedactPdf.route) { RedactPdfScreen(navController = navController) }

        composable(
            route = Screen.PdfViewer.route,
            arguments = listOf(navArgument("encodedUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("encodedUri") ?: ""
            PdfViewerScreen(navController = navController, encodedUri = encodedUri)
        }
    }
}
