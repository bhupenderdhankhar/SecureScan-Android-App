package com.example.securescan.ui.screens

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.securescan.ui.navigation.Screen
import com.example.securescan.viewmodel.QrViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(navController: NavController) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: QrViewModel = viewModel()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var scanning            by remember { mutableStateOf(true) }
    var lastScanned         by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        val result = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        hasCameraPermission = result
        if (!result) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("QR Scanner", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            when {
                !hasCameraPermission -> {
                    // Permission denied UI
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = Color.White)
                        Text(
                            "Camera permission is required\nto scan QR codes",
                            color     = Color.White,
                            textAlign = TextAlign.Center,
                            style     = MaterialTheme.typography.bodyLarge
                        )
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }) { Text("Grant Permission") }
                    }
                }

                else -> {
                    // Camera preview
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraExecutor = Executors.newSingleThreadExecutor()
                            val cameraProviderFuture =
                                ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val options = BarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(
                                        Barcode.FORMAT_QR_CODE,
                                        Barcode.FORMAT_DATA_MATRIX
                                    ).build()
                                val scanner = BarcodeScanning.getClient(options)

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(
                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                    ).build()

                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && scanning) {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                val barcode = barcodes.firstOrNull()
                                                val value   = barcode?.rawValue
                                                if (!value.isNullOrBlank() &&
                                                    value != lastScanned && scanning) {
                                                    scanning    = false
                                                    lastScanned = value
                                                    val encoded = URLEncoder.encode(
                                                        value,
                                                        StandardCharsets.UTF_8.toString()
                                                    )
                                                    viewModel.analyzeQrCode(value)
                                                    navController.navigate(
                                                        Screen.QrResult.createRoute(encoded)
                                                    )
                                                }
                                            }
                                            .addOnFailureListener {
                                                Log.w("QRScanner", "Scan error: ${it.message}")
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    Log.e("QRScanner", "Camera bind error", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Scanner overlay
                    ScannerOverlay()

                    // Bottom hint
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            "Point camera at a QR code",
                            modifier   = Modifier.padding(
                                horizontal = 20.dp, vertical = 10.dp),
                            color      = Color.White,
                            fontSize   = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Dark overlay with transparent center
        val overlayColor = Color.Black.copy(alpha = 0.55f)
        val boxSize      = 260.dp

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)
                .background(overlayColor))
            Row(modifier = Modifier.fillMaxWidth().height(boxSize)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(overlayColor))
                // Clear center
                Box(modifier = Modifier.size(boxSize).background(Color.Transparent))
                Box(modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(overlayColor))
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)
                .background(overlayColor))
        }

        // Corner brackets
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(boxSize)
        ) {
            val cornerColor  = Color(0xFF00C853)
            val cornerStroke = 4.dp
            val cornerSize   = 32.dp

            // Top-left
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                Box(modifier = Modifier.width(cornerSize).height(cornerStroke)
                    .background(cornerColor))
                Box(modifier = Modifier.width(cornerStroke).height(cornerSize)
                    .background(cornerColor))
            }
            // Top-right
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                Box(modifier = Modifier.width(cornerSize).height(cornerStroke)
                    .align(Alignment.TopEnd).background(cornerColor))
                Box(modifier = Modifier.width(cornerStroke).height(cornerSize)
                    .align(Alignment.TopEnd).background(cornerColor))
            }
            // Bottom-left
            Box(modifier = Modifier.align(Alignment.BottomStart)) {
                Box(modifier = Modifier.width(cornerSize).height(cornerStroke)
                    .align(Alignment.BottomStart).background(cornerColor))
                Box(modifier = Modifier.width(cornerStroke).height(cornerSize)
                    .align(Alignment.BottomStart).background(cornerColor))
            }
            // Bottom-right
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                Box(modifier = Modifier.width(cornerSize).height(cornerStroke)
                    .align(Alignment.BottomEnd).background(cornerColor))
                Box(modifier = Modifier.width(cornerStroke).height(cornerSize)
                    .align(Alignment.BottomEnd).background(cornerColor))
            }
        }
    }
}