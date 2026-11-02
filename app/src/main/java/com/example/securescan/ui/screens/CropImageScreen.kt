package com.example.securescan.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.securescan.viewmodel.ImageToolsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropImageScreen(
    navController: NavController,
    viewModel: ImageToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fileName by remember { mutableStateOf("Cropped_Image") }

    var leftPercent by remember { mutableFloatStateOf(0f) }
    var topPercent by remember { mutableFloatStateOf(0f) }
    var widthPercent by remember { mutableFloatStateOf(1f) }
    var heightPercent by remember { mutableFloatStateOf(1f) }

    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        selectedUri = uri
        leftPercent = 0f
        topPercent = 0f
        widthPercent = 1f
        heightPercent = 1f
    }

    LaunchedEffect(selectedUri) {
        originalBitmap = selectedUri?.let { uri ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
        }

        if (selectedUri != null) {
            fileName = displayName(context, selectedUri!!).substringBeforeLast(".").ifBlank { "Cropped_Image" }
        }
    }

    LaunchedEffect(originalBitmap, leftPercent, topPercent, widthPercent, heightPercent) {
        val bitmap = originalBitmap ?: run {
            previewBitmap = null
            return@LaunchedEffect
        }

        previewBitmap = withContext(Dispatchers.Default) {
            val cropLeft = (bitmap.width * leftPercent).toInt().coerceIn(0, bitmap.width - 1)
            val cropTop = (bitmap.height * topPercent).toInt().coerceIn(0, bitmap.height - 1)
            val cropWidth = max(1, (bitmap.width * widthPercent).toInt())
            val cropHeight = max(1, (bitmap.height * heightPercent).toInt())
            val safeWidth = cropWidth.coerceAtMost(bitmap.width - cropLeft)
            val safeHeight = cropHeight.coerceAtMost(bitmap.height - cropTop)
            Bitmap.createBitmap(bitmap, cropLeft, cropTop, safeWidth, safeHeight)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            originalBitmap?.recycle()
            previewBitmap?.recycle()
        }
    }

    fun safeWidth(value: Float): Float = value.coerceIn(0.1f, 1f - leftPercent)
    fun safeHeight(value: Float): Float = value.coerceIn(0.1f, 1f - topPercent)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop Image", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { picker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose image")
                    }

                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("Output name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Preview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("No image selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Crop area", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                    Text("Left")
                    Slider(
                        value = leftPercent,
                        onValueChange = {
                            leftPercent = it.coerceIn(0f, 0.9f)
                            widthPercent = safeWidth(widthPercent)
                        }
                    )

                    Text("Top")
                    Slider(
                        value = topPercent,
                        onValueChange = {
                            topPercent = it.coerceIn(0f, 0.9f)
                            heightPercent = safeHeight(heightPercent)
                        }
                    )

                    Text("Width")
                    Slider(
                        value = widthPercent,
                        onValueChange = { widthPercent = safeWidth(it) },
                        valueRange = 0.1f..1f
                    )

                    Text("Height")
                    Slider(
                        value = heightPercent,
                        onValueChange = { heightPercent = safeHeight(it) },
                        valueRange = 0.1f..1f
                    )
                }
            }

            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            uiState.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    viewModel.cropImage(
                        sourceUri = uri,
                        fileName = fileName.ifBlank { "Cropped_Image" },
                        leftPercent = leftPercent,
                        topPercent = topPercent,
                        widthPercent = widthPercent,
                        heightPercent = heightPercent
                    )
                },
                enabled = selectedUri != null && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Crop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save image")
                }
            }
        }
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) ?: "Image"
            else "Image"
        } ?: "Image"
}
