package com.example.securescan.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

enum class AddSigScreen { EDIT, PREVIEW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSignatureToPdfScreen(navController: NavController) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var pdfUri        by remember { mutableStateOf<Uri?>(null) }
    var pdfLabel      by remember { mutableStateOf("") }
    var sigBitmap     by remember { mutableStateOf<Bitmap?>(null) }
    var sigLabel      by remember { mutableStateOf("") }
    var allPages      by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedPage  by remember { mutableStateOf(0) }
    var isLoading     by remember { mutableStateOf(false) }
    var sigPosition   by remember { mutableStateOf(Offset(40f, 40f)) }
    var sigScalePct   by remember { mutableStateOf(30f) }
    var previewSize   by remember { mutableStateOf(IntSize.Zero) }
    var zoom          by remember { mutableStateOf(1f) }
    var panOffset     by remember { mutableStateOf(Offset.Zero) }
    var currentScreen by remember { mutableStateOf(AddSigScreen.EDIT) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pdfUri   = it
            pdfLabel = "PDF selected"
            scope.launch {
                isLoading = true
                allPages  = renderAllPdfPages(context, it)
                isLoading = false
            }
        }
    }

    val sigPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                sigBitmap = loadBitmapFromUri(context, it)
                sigLabel  = "Signature loaded"
                isLoading = false
            }
        }
    }

    val currentPageBitmap = allPages.getOrNull(selectedPage)

    // Generate preview bitmap by compositing signature onto page
    suspend fun generatePreview(): Bitmap? = withContext(Dispatchers.Default) {
        val page = currentPageBitmap ?: return@withContext null
        val sig  = sigBitmap         ?: return@withContext null
        if (previewSize.width == 0)  return@withContext null

        val result = page.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val sigWidthPx  = previewSize.width * sigScalePct / 100f
        val sigHeightPx = if (sig.width > 0)
            sigWidthPx * sig.height.toFloat() / sig.width else sigWidthPx

        // Scale sig position from preview space to bitmap space
        val bitmapScaleX = result.width.toFloat()  / previewSize.width
        val bitmapScaleY = result.height.toFloat() / previewSize.height

        val drawX = (sigPosition.x / zoom) * bitmapScaleX
        val drawY = (sigPosition.y / zoom) * bitmapScaleY
        val drawW = sigWidthPx  * bitmapScaleX / zoom
        val drawH = sigHeightPx * bitmapScaleY / zoom

        val scaledSig = Bitmap.createScaledBitmap(
            sig, drawW.toInt().coerceAtLeast(1),
            drawH.toInt().coerceAtLeast(1), true
        )
        canvas.drawBitmap(scaledSig, drawX, drawY, null)
        scaledSig.recycle()
        result
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (currentScreen == AddSigScreen.PREVIEW)
                            "Preview" else "Add Signature to PDF",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentScreen == AddSigScreen.PREVIEW) {
                            previewBitmap?.recycle()
                            previewBitmap = null
                            currentScreen = AddSigScreen.EDIT
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentScreen == AddSigScreen.PREVIEW) {
                        // Save from preview
                        IconButton(
                            onClick = {
                                if (pdfUri == null) return@IconButton
                                scope.launch {
                                    isLoading = true
                                    val result = addSignatureToPdfAllPages(
                                        context     = context,
                                        pdfUri      = pdfUri!!,
                                        signature   = sigBitmap!!,
                                        sigPosition = sigPosition,
                                        sigScalePct = sigScalePct,
                                        previewW    = previewSize.width,
                                        previewH    = previewSize.height,
                                        targetPage  = selectedPage,
                                        zoom        = zoom
                                    )
                                    isLoading = false
                                    result.onSuccess {
                                        snackbarHost.showSnackbar(
                                            "PDF saved to Documents/SecureScanner!"
                                        )
                                        currentScreen = AddSigScreen.EDIT
                                    }.onFailure { e ->
                                        snackbarHost.showSnackbar("Failed: ${e.message}")
                                    }
                                }
                            },
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color    = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = "Save",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
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

        // ── Preview screen ────────────────────────────────────────────────────
        if (currentScreen == AddSigScreen.PREVIEW) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(ComposeColor(0xFF2B2B2B))
            ) {
                if (isLoading || previewBitmap == null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = ComposeColor.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Generating preview...",
                            color = ComposeColor.White,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Show all pages with signature composited on selected page
                        itemsIndexed(allPages) { idx, pageBmp ->
                            Card(
                                modifier  = Modifier.fillMaxWidth(),
                                shape     = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(4.dp),
                                colors    = CardDefaults.cardColors(
                                    containerColor = ComposeColor.White
                                )
                            ) {
                                Column {
                                    Image(
                                        bitmap             = if (idx == selectedPage)
                                            previewBitmap!!.asImageBitmap()
                                        else pageBmp.asImageBitmap(),
                                        contentDescription = "Page ${idx + 1}",
                                        modifier           = Modifier.fillMaxWidth(),
                                        contentScale       = ContentScale.FillWidth
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ComposeColor(0xFFF5F5F5))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Page ${idx + 1} of ${allPages.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ComposeColor.Gray)
                                        if (idx == selectedPage) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text("Signature added",
                                                    modifier = Modifier.padding(
                                                        horizontal = 8.dp, vertical = 2.dp),
                                                    style      = MaterialTheme.typography.labelSmall,
                                                    color      = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }

                    // Save button at bottom
                    Surface(
                        modifier        = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        color           = MaterialTheme.colorScheme.surface,
                        shadowElevation = 12.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick  = {
                                    previewBitmap?.recycle()
                                    previewBitmap = null
                                    currentScreen = AddSigScreen.EDIT
                                },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Back to Edit")
                            }

                            Button(
                                onClick = {
                                    if (pdfUri == null) return@Button
                                    scope.launch {
                                        isLoading = true
                                        val result = addSignatureToPdfAllPages(
                                            context     = context,
                                            pdfUri      = pdfUri!!,
                                            signature   = sigBitmap!!,
                                            sigPosition = sigPosition,
                                            sigScalePct = sigScalePct,
                                            previewW    = previewSize.width,
                                            previewH    = previewSize.height,
                                            targetPage  = selectedPage,
                                            zoom        = zoom
                                        )
                                        isLoading = false
                                        result.onSuccess {
                                            snackbarHost.showSnackbar("✅ PDF saved!")
                                            currentScreen = AddSigScreen.EDIT
                                        }.onFailure { e ->
                                            snackbarHost.showSnackbar("Failed: ${e.message}")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape    = RoundedCornerShape(12.dp),
                                enabled  = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(18.dp),
                                        color       = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Saving...")
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save PDF")
                                }
                            }
                        }
                    }
                }
            }
            return@Scaffold
        }

        // ── Edit screen ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Select PDF and signature. Choose page, " +
                                "pinch to zoom, drag signature to position, " +
                                "then preview before saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Step 1: PDF
            SectionCard(number = "1", title = "Select PDF") {
                Button(onClick = { pdfPicker.launch("application/pdf") },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Choose PDF File")
                }
                if (pdfLabel.isNotEmpty()) {
                    Text("✓ $pdfLabel · ${allPages.size} page(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }

            // Step 2: Signature
            SectionCard(number = "2", title = "Select Signature Image") {
                Button(onClick = { sigPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Draw, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Choose Signature Image")
                }
                if (sigBitmap != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(
                        bitmap             = sigBitmap!!.asImageBitmap(),
                        contentDescription = "Signature preview",
                        modifier           = Modifier
                            .fillMaxWidth().height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ComposeColor.White),
                        contentScale = ContentScale.Fit
                    )
                    Text("✓ $sigLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }

            // Step 3: Page selection
            if (allPages.size > 1) {
                SectionCard(number = "3", title = "Select Page") {
                    Text("Tap a page to select where the signature goes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(allPages) { index, bitmap ->
                            val isSelected = index == selectedPage
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier            = Modifier.width(80.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 110.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .background(ComposeColor.White)
                                        .pointerInput(index) {
                                            detectTapGestures {
                                                selectedPage = index
                                                zoom         = 1f
                                                panOffset    = Offset.Zero
                                                sigPosition  = Offset(40f, 40f)
                                            }
                                        }
                                ) {
                                    Image(
                                        bitmap             = bitmap.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        modifier           = Modifier.fillMaxSize(),
                                        contentScale       = ContentScale.Fit
                                    )
                                    if (isSelected) {
                                        Box(modifier = Modifier
                                            .align(Alignment.TopEnd).padding(3.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Icon(Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(16.dp).padding(2.dp))
                                            }
                                        }
                                    }
                                }
                                Text("Page ${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold
                                    else FontWeight.Normal,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }

            // Step 4: Position
            val stepNum = if (allPages.size > 1) "4" else "3"
            if (currentPageBitmap != null && sigBitmap != null) {
                SectionCard(number = stepNum, title = "Position Signature") {

                    // Zoom controls
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Zoom:", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold)
                        FilledIconButton(
                            onClick  = { zoom = (zoom - 0.25f).coerceAtLeast(0.5f) },
                            modifier = Modifier.size(34.dp),
                            colors   = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out",
                                modifier = Modifier.size(16.dp))
                        }
                        Surface(
                            shape    = RoundedCornerShape(8.dp),
                            color    = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.width(64.dp)
                        ) {
                            Text("${(zoom * 100).roundToInt()}%",
                                modifier   = Modifier.padding(
                                    horizontal = 8.dp, vertical = 6.dp),
                                style      = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign  = TextAlign.Center)
                        }
                        FilledIconButton(
                            onClick  = { zoom = (zoom + 0.25f).coerceAtMost(4f) },
                            modifier = Modifier.size(34.dp),
                            colors   = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in",
                                modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick        = { zoom = 1f; panOffset = Offset.Zero },
                            modifier       = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) { Text("Reset", style = MaterialTheme.typography.labelSmall) }
                    }

                    // Size slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text("Sig size: ${sigScalePct.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(100.dp))
                        Slider(value = sigScalePct,
                            onValueChange = { sigScalePct = it },
                            valueRange = 5f..80f,
                            modifier = Modifier.weight(1f))
                    }

                    Text("• Pinch to zoom · Pan to scroll · Drag blue box to position signature",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp))

                    // PDF preview with signature overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp))
                            .background(ComposeColor(0xFF3A3A3A))
                            .onSizeChanged { previewSize = it }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, pinchZoom, _ ->
                                    zoom = (zoom * pinchZoom).coerceIn(0.5f, 4f)
                                    panOffset = Offset(
                                        panOffset.x + pan.x,
                                        panOffset.y + pan.y
                                    )
                                }
                            }
                    ) {
                        Image(
                            bitmap             = currentPageBitmap.asImageBitmap(),
                            contentDescription = "Page ${selectedPage + 1}",
                            modifier           = Modifier.fillMaxSize().graphicsLayer(
                                scaleX       = zoom,
                                scaleY       = zoom,
                                translationX = panOffset.x,
                                translationY = panOffset.y
                            ),
                            contentScale = ContentScale.Fit
                        )

                        if (previewSize.width > 0) {
                            val sigWidthPx  = previewSize.width * sigScalePct / 100f * zoom
                            val sigHeightPx = if (sigBitmap!!.width > 0)
                                sigWidthPx * sigBitmap!!.height.toFloat() / sigBitmap!!.width
                            else sigWidthPx

                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(sigPosition.x.roundToInt(),
                                            sigPosition.y.roundToInt())
                                    }
                                    .size(width = sigWidthPx.dp, height = sigHeightPx.dp)
                                    .border(2.dp, ComposeColor(0xFF1565C0),
                                        RoundedCornerShape(4.dp))
                                    .pointerInput(zoom) {
                                        detectDragGestures { _, drag ->
                                            sigPosition = Offset(
                                                (sigPosition.x + drag.x).coerceIn(
                                                    0f, previewSize.width - sigWidthPx),
                                                (sigPosition.y + drag.y).coerceIn(
                                                    0f, previewSize.height - sigHeightPx)
                                            )
                                        }
                                    }
                            ) {
                                Image(
                                    bitmap             = sigBitmap!!.asImageBitmap(),
                                    contentDescription = "Signature",
                                    modifier           = Modifier.fillMaxSize(),
                                    contentScale       = ContentScale.FillBounds
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                            shape    = RoundedCornerShape(6.dp),
                            color    = ComposeColor.Black.copy(alpha = 0.6f)
                        ) {
                            Text("Page ${selectedPage + 1} of ${allPages.size}",
                                modifier = Modifier.padding(
                                    horizontal = 8.dp, vertical = 4.dp),
                                color    = ComposeColor.White,
                                fontSize = 11.sp)
                        }
                    }
                }

                // Preview + Save buttons
                val saveStepNum = if (allPages.size > 1) "5" else "4"
                SectionCard(number = saveStepNum, title = "Preview & Save") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Preview button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading     = true
                                    previewBitmap = generatePreview()
                                    isLoading     = false
                                    currentScreen = AddSigScreen.PREVIEW
                                }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = RoundedCornerShape(12.dp),
                            enabled  = !isLoading
                        ) {
                            Icon(Icons.Default.Preview, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview")
                        }

                        // Direct save button
                        Button(
                            onClick = {
                                if (pdfUri == null) return@Button
                                scope.launch {
                                    isLoading = true
                                    val result = addSignatureToPdfAllPages(
                                        context     = context,
                                        pdfUri      = pdfUri!!,
                                        signature   = sigBitmap!!,
                                        sigPosition = sigPosition,
                                        sigScalePct = sigScalePct,
                                        previewW    = previewSize.width,
                                        previewH    = previewSize.height,
                                        targetPage  = selectedPage,
                                        zoom        = zoom
                                    )
                                    isLoading = false
                                    result.onSuccess {
                                        snackbarHost.showSnackbar("✅ PDF saved!")
                                    }.onFailure { e ->
                                        snackbarHost.showSnackbar("Failed: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = RoundedCornerShape(12.dp),
                            enabled  = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color    = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// ── Keep existing helpers ─────────────────────────────────────────────────────

private suspend fun renderAllPdfPages(
    context: Context, uri: Uri, renderWidth: Int = 1080
): List<Bitmap> = withContext(Dispatchers.IO) {
    val pages = mutableListOf<Bitmap>()
    try {
        val pfd      = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return@withContext pages
        val renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            val page   = renderer.openPage(i)
            val scale  = renderWidth.toFloat() / page.width
            val bitmap = Bitmap.createBitmap(
                renderWidth, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pages.add(bitmap); page.close()
        }
        renderer.close(); pfd.close()
    } catch (e: Exception) { e.printStackTrace() }
    pages
}

private suspend fun addSignatureToPdfAllPages(
    context: Context, pdfUri: Uri, signature: Bitmap,
    sigPosition: Offset, sigScalePct: Float,
    previewW: Int, previewH: Int,
    targetPage: Int, zoom: Float
): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
        PDFBoxResourceLoader.init(context)
        val stream = context.contentResolver.openInputStream(pdfUri)
            ?: error("Cannot read PDF")
        val doc    = PDDocument.load(stream); stream.close()

        val page         = doc.getPage(targetPage)
        val pageWidthPt  = page.mediaBox.width
        val pageHeightPt = page.mediaBox.height

        val sigWPt = pageWidthPt * sigScalePct / 100f
        val sigHPt = if (signature.width > 0)
            sigWPt * signature.height.toFloat() / signature.width else sigWPt

        val scaleX = pageWidthPt  / (previewW.toFloat() / zoom)
        val scaleY = pageHeightPt / (previewH.toFloat() / zoom)
        val pdfX   = (sigPosition.x / zoom) * scaleX
        val pdfY   = pageHeightPt - ((sigPosition.y / zoom) * scaleY) - sigHPt

        val argbSig = if (signature.config == Bitmap.Config.ARGB_8888) signature
        else signature.copy(Bitmap.Config.ARGB_8888, false)

        val pdImage = LosslessFactory.createFromImage(doc, argbSig)
        val cs      = PDPageContentStream(doc, page,
            PDPageContentStream.AppendMode.APPEND, true, true)
        cs.drawImage(pdImage, pdfX.coerceAtLeast(0f), pdfY.coerceAtLeast(0f),
            sigWPt, sigHPt)
        cs.close()

        val out = ByteArrayOutputStream()
        doc.save(out); doc.close()
        if (argbSig !== signature) argbSig.recycle()

        val fileName = "signed_${System.currentTimeMillis()}.pdf"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOCUMENTS + "/SecureScanner")
            }
            val u = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"), values
            ) ?: error("Insert failed")
            context.contentResolver.openOutputStream(u)?.use { it.write(out.toByteArray()) }
            u
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "SecureScanner")
            dir.mkdirs()
            val file = File(dir, fileName)
            file.writeBytes(out.toByteArray())
            Uri.fromFile(file)
        }
    }
}