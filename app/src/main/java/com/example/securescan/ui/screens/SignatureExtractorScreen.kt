package com.example.securescan.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ExtractionFormat { JPG, PNG }

data class CropRect(
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX  : Float = 0f,
    val endY  : Float = 0f
) {
    val left  : Float get() = minOf(startX, endX)
    val top   : Float get() = minOf(startY, endY)
    val right : Float get() = maxOf(startX, endX)
    val bottom: Float get() = maxOf(startY, endY)
    val width : Float get() = right  - left
    val height: Float get() = bottom - top
    val isValid : Boolean get() = width > 10f && height > 10f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureExtractorScreen(navController: NavController) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var sourceBitmap   by remember { mutableStateOf<Bitmap?>(null) }
    var sourceLabel    by remember { mutableStateOf("") }
    var cropRect       by remember { mutableStateOf(CropRect()) }
    var imageSize      by remember { mutableStateOf(IntSize.Zero) }
    var isLoading      by remember { mutableStateOf(false) }
    var croppedBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                sourceBitmap  = loadBitmapFromUri(context, it)
                sourceLabel   = "Image loaded"
                cropRect      = CropRect()
                croppedBitmap = null
                isLoading     = false
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                sourceBitmap  = renderFirstPdfPage(context, it)
                sourceLabel   = "PDF page loaded"
                cropRect      = CropRect()
                croppedBitmap = null
                isLoading     = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text("Extract Signature / Stamp",
                        fontWeight = FontWeight.SemiBold)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Info card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Select an area containing a signature or stamp. " +
                                "The background will be removed — only the ink/stamp remains.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Step 1: Source
            SectionCard(number = "1", title = "Select Source") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick  = { imagePicker.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("From Image")
                    }
                    OutlinedButton(
                        onClick  = { pdfPicker.launch("application/pdf") },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("From PDF")
                    }
                }
                if (sourceLabel.isNotEmpty()) {
                    Text("✓ $sourceLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }

            // Step 2: Crop
            if (sourceBitmap != null) {
                SectionCard(number = "2", title = "Draw Selection Area") {
                    Text(
                        "Drag on the image to select the signature or stamp",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                sourceBitmap!!.width.toFloat() /
                                        sourceBitmap!!.height.toFloat()
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp))
                            .onSizeChanged { imageSize = it }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        cropRect = CropRect(
                                            startX = offset.x,
                                            startY = offset.y,
                                            endX   = offset.x,
                                            endY   = offset.y
                                        )
                                    },
                                    onDrag = { _, drag ->
                                        cropRect = cropRect.copy(
                                            endX = (cropRect.endX + drag.x)
                                                .coerceIn(0f, imageSize.width.toFloat()),
                                            endY = (cropRect.endY + drag.y)
                                                .coerceIn(0f, imageSize.height.toFloat())
                                        )
                                    },
                                    onDragEnd = {}
                                )
                            }
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap             = sourceBitmap!!.asImageBitmap(),
                            contentDescription = "Source",
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.FillBounds
                        )
                        if (cropRect.isValid) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Semi-transparent overlay
                                drawRect(color = ComposeColor.Black.copy(alpha = 0.4f))
                                // Clear selection
                                drawRect(
                                    color     = ComposeColor.Transparent,
                                    topLeft   = Offset(cropRect.left, cropRect.top),
                                    size      = Size(cropRect.width, cropRect.height),
                                    blendMode = BlendMode.Clear
                                )
                                // Green border
                                drawRect(
                                    color   = ComposeColor(0xFF00C853),
                                    topLeft = Offset(cropRect.left, cropRect.top),
                                    size    = Size(cropRect.width, cropRect.height),
                                    style   = Stroke(width = 3f)
                                )
                                // Corner handles
                                val h = 18f
                                listOf(
                                    Offset(cropRect.left, cropRect.top),
                                    Offset(cropRect.right - h, cropRect.top),
                                    Offset(cropRect.left, cropRect.bottom - h),
                                    Offset(cropRect.right - h, cropRect.bottom - h)
                                ).forEach { c ->
                                    drawRect(
                                        color   = ComposeColor(0xFF00C853),
                                        topLeft = c,
                                        size    = Size(h, h)
                                    )
                                }
                            }
                        }
                    }

                    if (cropRect.isValid) {
                        Text(
                            "Selection: ${cropRect.width.toInt()} × ${cropRect.height.toInt()} px",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (!cropRect.isValid || imageSize.width == 0) {
                                scope.launch {
                                    snackbarHost.showSnackbar(
                                        "Please drag to select an area first"
                                    )
                                }
                                return@Button
                            }
                            scope.launch {
                                isLoading = true
                                croppedBitmap = extractSignature(
                                    source = sourceBitmap!!,
                                    crop   = cropRect,
                                    viewW  = imageSize.width,
                                    viewH  = imageSize.height
                                )
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        enabled  = cropRect.isValid && !isLoading
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extract (Remove Background)")
                    }
                }
            }

            // Step 3: Preview + save
            if (croppedBitmap != null) {
                SectionCard(number = "3", title = "Extracted Result") {
                    Text(
                        "Background removed — only signature/stamp ink remains",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Checkerboard background to show transparency
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 280.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ComposeColor.White),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap             = croppedBitmap!!.asImageBitmap(),
                            contentDescription = "Extracted",
                            modifier           = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentScale       = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "${croppedBitmap!!.width} × ${croppedBitmap!!.height} px · White background",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick  = { showSaveDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Signature / Stamp")
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Removing background...",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showSaveDialog && croppedBitmap != null) {
        SaveSignatureDialog(
            bitmap    = croppedBitmap!!,
            onSave    = { format, width, height ->
                scope.launch {
                    isLoading      = true
                    showSaveDialog = false
                    val result     = saveSignatureBitmap(
                        context, croppedBitmap!!, format, width, height
                    )
                    isLoading      = false
                    result.onSuccess {
                        snackbarHost.showSnackbar("Saved as ${format.name}!")
                    }.onFailure { e ->
                        snackbarHost.showSnackbar("Save failed: ${e.message}")
                    }
                }
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

// ── Save dialog ───────────────────────────────────────────────────────────────

@Composable
fun SaveSignatureDialog(
    bitmap    : Bitmap,
    onSave    : (ExtractionFormat, Int, Int) -> Unit,
    onDismiss : () -> Unit
) {
    var format    by remember { mutableStateOf(ExtractionFormat.PNG) }
    var widthStr  by remember { mutableStateOf(bitmap.width.toString()) }
    var heightStr by remember { mutableStateOf(bitmap.height.toString()) }
    var keepRatio by remember { mutableStateOf(true) }
    val ratio     = bitmap.width.toFloat() / bitmap.height.toFloat()

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Save Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)

                Text("Format", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExtractionFormat.values().forEach { fmt ->
                        FilterChip(
                            selected = format == fmt,
                            onClick  = { format = fmt },
                            label    = {
                                Text(if (fmt == ExtractionFormat.PNG)
                                    "PNG (transparent)" else "JPG")
                            }
                        )
                    }
                }

                if (format == ExtractionFormat.JPG) {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            "Note: JPG does not support transparency. " +
                                    "Background will be white. PNG recommended.",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Text("Output Size (pixels)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value         = widthStr,
                        onValueChange = { v ->
                            widthStr = v
                            if (keepRatio) {
                                v.toIntOrNull()?.let { w ->
                                    heightStr = (w / ratio).toInt().toString()
                                }
                            }
                        },
                        label      = { Text("Width") },
                        modifier   = Modifier.weight(1f),
                        singleLine = true
                    )
                    Text("×", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value         = heightStr,
                        onValueChange = { v ->
                            heightStr = v
                            if (keepRatio) {
                                v.toIntOrNull()?.let { h ->
                                    widthStr = (h * ratio).toInt().toString()
                                }
                            }
                        },
                        label      = { Text("Height") },
                        modifier   = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked         = keepRatio,
                        onCheckedChange = { keepRatio = it }
                    )
                    Text("Keep aspect ratio",
                        style = MaterialTheme.typography.bodySmall)
                }

                Text("Original: ${bitmap.width} × ${bitmap.height} px",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss,
                        modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val w = widthStr.toIntOrNull()?.coerceIn(10, 8000)
                                ?: bitmap.width
                            val h = heightStr.toIntOrNull()?.coerceIn(10, 8000)
                                ?: bitmap.height
                            onSave(format, w, h)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ── Section card ──────────────────────────────────────────────────────────────

@Composable
fun SectionCard(
    number  : String,
    title   : String,
    content : @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(bottom = 12.dp)
            ) {
                Surface(
                    shape    = RoundedCornerShape(50),
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(number,
                            color      = MaterialTheme.colorScheme.onPrimary,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

// ── Background removal ────────────────────────────────────────────────────────

/**
 * Crops the selected region from the source bitmap, then removes the
 * background using flood-fill from all four corners (assuming corners
 * are background) and makes those pixels transparent.
 * Returns a ARGB_8888 bitmap with transparent background.
 */
private suspend fun extractSignature(
    source : Bitmap,
    crop   : CropRect,
    viewW  : Int,
    viewH  : Int
): Bitmap = withContext(Dispatchers.Default) {
    val scaleX = source.width.toFloat()  / viewW
    val scaleY = source.height.toFloat() / viewH

    val srcL = (crop.left   * scaleX).toInt().coerceIn(0, source.width  - 1)
    val srcT = (crop.top    * scaleY).toInt().coerceIn(0, source.height - 1)
    val srcR = (crop.right  * scaleX).toInt().coerceIn(0, source.width)
    val srcB = (crop.bottom * scaleY).toInt().coerceIn(0, source.height)
    val srcW = (srcR - srcL).coerceAtLeast(1)
    val srcH = (srcB - srcT).coerceAtLeast(1)

    // Crop region
    val cropped = Bitmap.createBitmap(source, srcL, srcT, srcW, srcH)
        .copy(Bitmap.Config.ARGB_8888, true)

    val w = cropped.width
    val h = cropped.height
    val pixels = IntArray(w * h)
    cropped.getPixels(pixels, 0, w, 0, 0, w, h)

    // Sample background from corners
    val corners = listOf(
        pixels[0],
        pixels[w - 1],
        pixels[(h - 1) * w],
        pixels[(h - 1) * w + w - 1]
    )
    val bgR = corners.map { Color.red(it) }.average().toInt()
    val bgG = corners.map { Color.green(it) }.average().toInt()
    val bgB = corners.map { Color.blue(it) }.average().toInt()
    val tolerance = 40

    fun isBackground(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val matchesBg = kotlin.math.abs(r - bgR) < tolerance &&
                kotlin.math.abs(g - bgG) < tolerance &&
                kotlin.math.abs(b - bgB) < tolerance
        val isLight = r > 210 && g > 210 && b > 210
        return matchesBg || isLight
    }

    // BFS flood fill from all border pixels
    val visited = BooleanArray(w * h)
    val queue   = ArrayDeque<Int>()

    for (x in 0 until w) {
        for (y in listOf(0, h - 1)) {
            val idx = y * w + x
            if (!visited[idx] && isBackground(pixels[idx])) {
                visited[idx] = true; queue.add(idx)
            }
        }
    }
    for (y in 0 until h) {
        for (x in listOf(0, w - 1)) {
            val idx = y * w + x
            if (!visited[idx] && isBackground(pixels[idx])) {
                visited[idx] = true; queue.add(idx)
            }
        }
    }

    while (queue.isNotEmpty()) {
        val idx = queue.removeFirst()
        // Replace background with WHITE (not transparent)
        pixels[idx] = Color.WHITE
        val x = idx % w; val y = idx / w
        listOf(x-1 to y, x+1 to y, x to y-1, x to y+1).forEach { (nx, ny) ->
            if (nx in 0 until w && ny in 0 until h) {
                val nIdx = ny * w + nx
                if (!visited[nIdx] && isBackground(pixels[nIdx])) {
                    visited[nIdx] = true; queue.add(nIdx)
                }
            }
        }
    }

    // Make remaining light isolated pixels white too
    for (i in pixels.indices) {
        if (isBackground(pixels[i])) pixels[i] = Color.WHITE
    }

    // Create WHITE background result bitmap
    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(Color.WHITE)
    cropped.setPixels(pixels, 0, w, 0, 0, w, h)
    canvas.drawBitmap(cropped, 0f, 0f, null)
    cropped.recycle()
    result
}
internal suspend fun loadBitmapFromUri(
    context: Context, uri: Uri
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { s ->
            BitmapFactory.decodeStream(s)
        }
    } catch (e: Exception) { null }
}

internal suspend fun renderFirstPdfPage(
    context: Context, uri: Uri, renderWidth: Int = 1080
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val pfd      = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return@withContext null
        val renderer = PdfRenderer(pfd)
        val page     = renderer.openPage(0)
        val scale    = renderWidth.toFloat() / page.width
        val bitmap   = Bitmap.createBitmap(
            renderWidth, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888
        )
        Canvas(bitmap).drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close(); renderer.close(); pfd.close()
        bitmap
    } catch (e: Exception) { null }
}

internal suspend fun saveSignatureBitmap(
    context : Context,
    bitmap  : Bitmap,
    format  : ExtractionFormat,
    width   : Int,
    height  : Int
): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
        val scaled    = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val mime      = if (format == ExtractionFormat.PNG) "image/png" else "image/jpeg"
        val ext       = format.name.lowercase()
        val fileName  = "signature_${System.currentTimeMillis()}.$ext"
        val compFmt   = if (format == ExtractionFormat.PNG)
            Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/SecureScanner/Signatures")
            }
            val u = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: error("Insert failed")
            context.contentResolver.openOutputStream(u)?.use { o ->
                scaled.compress(compFmt, 100, o)
            }
            u
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES),
                "SecureScanner/Signatures"
            )
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { o -> scaled.compress(compFmt, 100, o) }
            Uri.fromFile(file)
        }
        scaled.recycle()
        uri
    }
}