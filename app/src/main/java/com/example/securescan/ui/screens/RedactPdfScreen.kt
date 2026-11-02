
package com.example.securescan.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.securescan.utils.PdfUtils
import com.example.securescan.viewmodel.PdfToolsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedactPdfScreen(
    navController: NavController,
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Redacted_PDF") }
    var pageNumberText by remember { mutableStateOf("1") }

    var leftPercent by remember { mutableFloatStateOf(0.1f) }
    var topPercent by remember { mutableFloatStateOf(0.1f) }
    var widthPercent by remember { mutableFloatStateOf(0.3f) }
    var heightPercent by remember { mutableFloatStateOf(0.15f) }

    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // PDF picker: changed from OpenDocument to GetContent
    val picker = rememberLauncherForActivityResult(GetContent()) { uri ->
        selectedUri = uri
        if (selectedUri != null) {
            fileName = displayName(context, selectedUri!!)
                .substringBeforeLast(".")
                .ifBlank { "Redacted_PDF" }
        }
    }

    LaunchedEffect(selectedUri, pageNumberText) {
        pageBitmap = null
        previewBitmap = null

        val uri = selectedUri ?: return@LaunchedEffect
        val page = pageNumberText.toIntOrNull() ?: return@LaunchedEffect
        if (page <= 0) return@LaunchedEffect

        pageBitmap = withContext(Dispatchers.IO) {
            PdfUtils.renderPage(context, uri, page - 1, 1080, null)
        }
    }

    LaunchedEffect(
        pageBitmap,
        leftPercent,
        topPercent,
        widthPercent,
        heightPercent
    ) {
        val bitmap = pageBitmap ?: run {
            previewBitmap = null
            return@LaunchedEffect
        }

        previewBitmap = withContext(Dispatchers.Default) {
            val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutable)

            val paint = Paint().apply {
                color = android.graphics.Color.BLACK
                style = Paint.Style.FILL
            }

            val rectLeft = bitmap.width * leftPercent
            val rectTop = bitmap.height * topPercent
            val rectWidth = max(8f, bitmap.width * widthPercent)
            val rectHeight = max(8f, bitmap.height * heightPercent)

            canvas.drawRect(
                rectLeft,
                rectTop,
                (rectLeft + rectWidth).coerceAtMost(bitmap.width.toFloat()),
                (rectTop + rectHeight).coerceAtMost(bitmap.height.toFloat()),
                paint
            )

            mutable
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pageBitmap?.recycle()
            previewBitmap?.recycle()
        }
    }

    fun safeWidth(value: Float): Float =
        value.coerceIn(0.05f, 1f - leftPercent)

    fun safeHeight(value: Float): Float =
        value.coerceIn(0.05f, 1f - topPercent)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Redact PDF",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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

            ElevatedCard(
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    Button(
                        onClick = {
                            picker.launch("application/pdf")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose PDF")
                    }

                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("Output name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )

                    OutlinedTextField(
                        value = pageNumberText,
                        onValueChange = {
                            pageNumberText = it.filter(Char::isDigit)
                        },
                        label = { Text("Page number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            ElevatedCard(
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Preview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                "No page preview",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            ElevatedCard(
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Redaction area",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text("Left")
                    Slider(
                        value = leftPercent,
                        onValueChange = {
                            leftPercent = it.coerceIn(0f, 0.95f)
                            widthPercent = safeWidth(widthPercent)
                        }
                    )

                    Text("Top")
                    Slider(
                        value = topPercent,
                        onValueChange = {
                            topPercent = it.coerceIn(0f, 0.95f)
                            heightPercent = safeHeight(heightPercent)
                        }
                    )

                    Text("Width")
                    Slider(
                        value = widthPercent,
                        onValueChange = {
                            widthPercent = safeWidth(it)
                        },
                        valueRange = 0.05f..1f
                    )

                    Text("Height")
                    Slider(
                        value = heightPercent,
                        onValueChange = {
                            heightPercent = safeHeight(it)
                        },
                        valueRange = 0.05f..1f
                    )
                }
            }

            uiState.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            uiState.successMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val page = pageNumberText.toIntOrNull()
                        ?: return@Button

                    viewModel.redactPdf(
                        sourceUri = uri,
                        fileName = fileName.ifBlank { "Redacted_PDF" },
                        pageNumber = page,
                        leftPercent = leftPercent,
                        topPercent = topPercent,
                        widthPercent = widthPercent,
                        heightPercent = heightPercent
                    )
                },
                enabled = selectedUri != null &&
                        (pageNumberText.toIntOrNull() ?: 0) > 0 &&
                        !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save PDF")
                }
            }
        }
    }
}

// Updated displayName function: manually closes Cursor
private fun displayName(
    context: android.content.Context,
    uri: Uri
): String {

    val cursor = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    ) ?: return "PDF"

    return try {
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )

            if (index >= 0) {
                cursor.getString(index) ?: "PDF"
            } else {
                "PDF"
            }
        } else {
            "PDF"
        }
    } finally {
        cursor.close()
    }
}
