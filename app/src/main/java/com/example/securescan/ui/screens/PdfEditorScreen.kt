package com.example.securescan.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.securescan.utils.PdfEditorUtils
import com.example.securescan.utils.PdfTextBlock
import com.example.securescan.viewmodel.PdfEditScreen
import com.example.securescan.viewmodel.PdfEditorViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    navController: NavController,
    viewModel: PdfEditorViewModel = hiltViewModel()
) {
    val uiState      by viewModel.uiState.collectAsState()
    val snackbarHost  = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.onFileSelected(it) } }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Font size conflict dialog
    if (uiState.showFontSizeConflictDialog) {
        FontSizeConflictDialog(
            conflictCount = uiState.fontSizeConflicts.size,
            originalSize  = uiState.fontSizeConflicts
                .firstOrNull()?.originalSize ?: 0f,
            userSize      = uiState.fontSizeConflicts
                .firstOrNull()?.userSize ?: 0f,
            onUseOriginal = {
                viewModel.resolveConflictsAndPreview(useOriginalSize = true)
            },
            onKeepUser = {
                viewModel.resolveConflictsAndPreview(useOriginalSize = false)
            },
            onDismiss = { viewModel.dismissConflictDialog() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when {
                                uiState.selectedUri == null -> "PDF Editor"
                                uiState.currentScreen == PdfEditScreen.PREVIEW -> "Preview"
                                else -> uiState.selectedFileName
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        if (uiState.renderedPages.isNotEmpty()) {
                            Text("${uiState.renderedPages.size} pages · ${uiState.totalEdits} edit(s)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                                    .copy(alpha = 0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.currentScreen == PdfEditScreen.PREVIEW)
                            viewModel.goBackToEditing()
                        else navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    when (uiState.currentScreen) {
                        PdfEditScreen.EDIT ->
                            if (uiState.renderedPages.isNotEmpty()) {
                                IconButton(onClick = { viewModel.generatePreview() }) {
                                    Icon(
                                        Icons.Default.Preview,
                                        contentDescription = "Preview",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        PdfEditScreen.PREVIEW ->
                            IconButton(
                                onClick  = { viewModel.savePdf() },
                                enabled  = !uiState.isSaving
                            ) {
                                if (uiState.isSaving)
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(20.dp),
                                        color       = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                else
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = "Save",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                            }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = MaterialTheme.colorScheme.primary,
                    titleContentColor      = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.selectedUri == null ->
                    PdfEditorWelcomeScreen(
                        onPickFile = { filePicker.launch("application/pdf") }
                    )

                uiState.isLoadingPdf ->
                    FullScreenLoader("Opening PDF...")

                uiState.currentScreen == PdfEditScreen.PREVIEW ->
                    PdfPreviewScreen(
                        previewPages = uiState.previewPages,
                        isGenerating = uiState.isGeneratingPreview,
                        isSaving     = uiState.isSaving,
                        totalEdits   = uiState.totalEdits,
                        onSave       = { viewModel.savePdf() },
                        onGoBack     = { viewModel.goBackToEditing() }
                    )

                uiState.renderedPages.isNotEmpty() -> {
                    val allBlocks   = uiState.textBlocks.values.flatten()
                    val selBlock    = allBlocks.find {
                        it.id == uiState.selectedBlockId
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (selBlock != null) {
                            FontControlToolbar(
                                block            = selBlock,
                                onFontSizeChange = { newSize ->
                                    viewModel.setUserFontSize(selBlock.id, newSize)
                                },
                                onBoldToggle     = {
                                    viewModel.toggleUserBold(selBlock.id)
                                }
                            )
                        } else if (uiState.isExtractingText) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp, vertical = 10.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color       = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        "Detecting text positions — tap text to edit...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme
                                            .onSecondaryContainer
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color    = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp, vertical = 8.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint     = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        "Tap any text block to select · Double-tap to edit",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        PdfPagesList(
                            pages            = uiState.renderedPages,
                            textBlocks       = uiState.textBlocks,
                            selectedBlockId  = uiState.selectedBlockId,
                            hasEdits         = uiState.hasEdits,
                            isExtractingText = uiState.isExtractingText,
                            onBlockTapped    = { viewModel.selectBlock(it.id) },
                            onBlockMoved     = { id, dx, dy ->
                                viewModel.moveBlock(id, dx, dy)
                            },
                            onBlockTextUpdated = { id, text ->
                                viewModel.updateBlockText(id, text)
                            },
                            onClearSelection = { viewModel.clearSelection() },
                            onPreview        = { viewModel.generatePreview() }
                        )
                    }
                }
            }
        }
    }
}

// ── Font Size Conflict Dialog ─────────────────────────────────────────────────

@Composable
fun FontSizeConflictDialog(
    conflictCount : Int,
    originalSize  : Float,
    userSize      : Float,
    onUseOriginal : () -> Unit,
    onKeepUser    : () -> Unit,
    onDismiss     : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Change Found in Font Size",
                fontWeight  = FontWeight.Bold,
                textAlign   = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "$conflictCount block(s) have a different font size than original.",
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Original size:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "${originalSize.roundToInt()} px",
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.primary,
                                style      = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Your size:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "${userSize.roundToInt()} px",
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.error,
                                style      = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Text(
                    "Replace your font size with the original?",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = onUseOriginal) {
                Text("Yes, Use Original")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onKeepUser) {
                Text("Keep My Size")
            }
        }
    )
}

// ── Font Control Toolbar ──────────────────────────────────────────────────────

@Composable
fun FontControlToolbar(
    block            : PdfTextBlock,
    onFontSizeChange : (Float) -> Unit,
    onBoldToggle     : () -> Unit
) {
    val currentSize = block.effectiveFontSize
    val isBold      = block.effectiveIsBold
    val sizeChanged = block.hasFontSizeChanged

    Surface(
        color          = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier       = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Size",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                FilledIconButton(
                    onClick  = { onFontSizeChange((currentSize - 1f).coerceAtLeast(4f)) },
                    modifier = Modifier.size(34.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrease font",
                        modifier = Modifier.size(16.dp)
                    )
                }

                Surface(
                    shape  = RoundedCornerShape(8.dp),
                    color  = if (sizeChanged)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.defaultMinSize(minWidth = 60.dp)
                ) {
                    Text(
                        "${currentSize.roundToInt()} px",
                        modifier = Modifier.padding(
                            horizontal = 10.dp, vertical = 6.dp
                        ),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color      = if (sizeChanged)
                            MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign  = TextAlign.Center
                    )
                }

                FilledIconButton(
                    onClick  = { onFontSizeChange((currentSize + 1f).coerceAtMost(200f)) },
                    modifier = Modifier.size(34.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase font",
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onBoldToggle,
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = if (isBold)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = if (isBold)
                            MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier      = Modifier.height(36.dp),
                    shape         = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text(
                        "B",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 16.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (sizeChanged)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (sizeChanged) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    tint     = if (sizeChanged) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    buildString {
                        append("Original: ")
                        append("${block.fontRecord.originalFontSize.roundToInt()} px")
                        append(" · ")
                        append(if (block.fontRecord.originalIsBold) "Bold" else "Regular")
                        if (sizeChanged) {
                            append(" · ⚠ Size changed from original")
                        }
                    },
                    style  = MaterialTheme.typography.labelSmall,
                    color  = if (sizeChanged) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── PDF pages list ────────────────────────────────────────────────────────────

@Composable
fun PdfPagesList(
    pages            : List<PdfEditorUtils.PageRenderResult>,
    textBlocks       : Map<Int, List<PdfTextBlock>>,
    selectedBlockId  : String?,
    hasEdits         : Boolean,
    isExtractingText : Boolean,
    onBlockTapped    : (PdfTextBlock) -> Unit,
    onBlockMoved     : (String, Float, Float) -> Unit,
    onBlockTextUpdated : (String, String) -> Unit,
    onClearSelection : () -> Unit,
    onPreview        : () -> Unit
) {
    val listState     = rememberLazyListState()
    var currentPage   by remember { mutableIntStateOf(1) }
    var showEditDialog by remember { mutableStateOf(false) }
    var blockToEdit   by remember { mutableStateOf<PdfTextBlock?>(null) }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = listState.firstVisibleItemIndex + 1
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state           = listState,
            modifier        = Modifier
                .fillMaxSize()
                .background(Color(0xFF3A3A3A)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding  = PaddingValues(
                top = 8.dp, bottom = 100.dp,
                start = 6.dp, end = 6.dp
            )
        ) {
            itemsIndexed(pages) { index, pageResult ->
                EditablePdfPageCard(
                    pageResult       = pageResult,
                    textBlocks       = textBlocks[index] ?: emptyList(),
                    selectedBlockId  = selectedBlockId,
                    pageNumber       = index + 1,
                    totalPages       = pages.size,
                    onBlockTapped    = onBlockTapped,
                    onBlockDoubleTapped = { block ->
                        blockToEdit   = block
                        showEditDialog = true
                    },
                    onBlockDragged   = { id, dx, dy -> onBlockMoved(id, dx, dy) },
                    onCanvasTapped   = { onClearSelection() }
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pages.size > 1) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        "Page $currentPage of ${pages.size}",
                        modifier = Modifier.padding(
                            horizontal = 16.dp, vertical = 6.dp
                        ),
                        color    = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            if (!isExtractingText) {
                Button(
                    onClick = onPreview,
                    shape   = RoundedCornerShape(24.dp),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = if (hasEdits)
                            MaterialTheme.colorScheme.primary
                        else Color(0xFF555555)
                    ),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        Icons.Default.Preview,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (hasEdits) "Preview Changes" else "Preview Document",
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    if (showEditDialog && blockToEdit != null) {
        PdfTextEditDialog(
            block     = blockToEdit!!,
            onConfirm = { newText: String ->
                onBlockTextUpdated(blockToEdit!!.id, newText)
                showEditDialog = false
                blockToEdit    = null
            },
            onDismiss = {
                showEditDialog = false
                blockToEdit    = null
            }
        )
    }
}

// ── Editable page card ────────────────────────────────────────────────────────

@Composable
fun EditablePdfPageCard(
    pageResult      : PdfEditorUtils.PageRenderResult,
    textBlocks      : List<PdfTextBlock>,
    selectedBlockId : String?,
    pageNumber      : Int,
    totalPages      : Int,
    onBlockTapped       : (PdfTextBlock) -> Unit,
    onBlockDoubleTapped : (PdfTextBlock) -> Unit,
    onBlockDragged      : (String, Float, Float) -> Unit,
    onCanvasTapped      : () -> Unit
) {
    var imageSize   by remember { mutableStateOf(IntSize.Zero) }
    val editedCount  = textBlocks.count { it.isEdited }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { imageSize = it }
            ) {
                Image(
                    bitmap             = pageResult.bitmap.asImageBitmap(),
                    contentDescription = "Page $pageNumber",
                    modifier           = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures { onCanvasTapped() }
                        },
                    contentScale       = ContentScale.FillWidth
                )

                if (imageSize.width > 0) {
                    textBlocks.forEach { block ->
                        DraggableTextBlockOverlay(
                            block        = block,
                            imageSize    = imageSize,
                            bitmapWidth  = pageResult.bitmap.width,
                            bitmapHeight = pageResult.bitmap.height,
                            isSelected   = block.id == selectedBlockId,
                            onTapped     = { onBlockTapped(block) },
                            onDoubleTapped = { onBlockDoubleTapped(block) },
                            onDragged    = { dx, dy -> onBlockDragged(block.id, dx, dy) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (editedCount > 0) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Page $pageNumber of $totalPages",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                if (editedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF4CAF50)
                    ) {
                        Text(
                            "$editedCount edited",
                            modifier = Modifier.padding(
                                horizontal = 8.dp, vertical = 2.dp
                            ),
                            fontSize   = 10.sp,
                            color      = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ── Draggable text block overlay ──────────────────────────────────────────────

@Composable
fun DraggableTextBlockOverlay(
    block          : PdfTextBlock,
    imageSize      : IntSize,
    bitmapWidth    : Int,
    bitmapHeight   : Int,
    isSelected     : Boolean,
    onTapped       : () -> Unit,
    onDoubleTapped : () -> Unit,
    onDragged      : (Float, Float) -> Unit
) {
    val density = LocalDensity.current

    // Scale factors: bitmap pixels → screen display pixels
    val scaleX = imageSize.width.toFloat()  / bitmapWidth.toFloat()
    val scaleY = imageSize.height.toFloat() / bitmapHeight.toFloat()

    // Bounds to display (use expanded if edited)
    val displayBounds = if (block.isEdited)
        PdfEditorUtils.computeExpandedBounds(block, bitmapWidth, bitmapHeight)
    else
        block.finalBounds

    // Convert bitmap pixels → Dp
    val leftDp   = with(density) { (displayBounds.left   * scaleX).toDp() }
    val topDp    = with(density) { (displayBounds.top    * scaleY).toDp() }
    val widthDp  = with(density) {
        ((displayBounds.right - displayBounds.left) * scaleX).toDp()
    }.coerceAtLeast(30.dp)
    val heightDp = with(density) {
        ((displayBounds.bottom - displayBounds.top) * scaleY).toDp()
    }.coerceAtLeast(10.dp)

    Box(
        modifier = Modifier
            .absoluteOffset(x = leftDp, y = topDp)
            .width(widthDp)
            .height(heightDp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                when {
                    isSelected && block.isEdited -> Color(0x5500C853)
                    isSelected                  -> Color(0x451565C0)
                    block.isEdited              -> Color(0x2500C853)
                    else                        -> Color.Transparent
                }
            )
            .then(
                if (isSelected || block.isEdited)
                    Modifier.border(
                        width = 1.5.dp,
                        color = when {
                            isSelected && block.isEdited -> Color(0xFF00C853)
                            isSelected                  -> Color(0xFF1565C0)
                            else                        -> Color(0xFF4CAF50)
                        },
                        shape = RoundedCornerShape(2.dp)
                    )
                else Modifier
            )
            .pointerInput(block.id) {
                detectTapGestures(
                    onTap       = { onTapped() },
                    onDoubleTap = { onDoubleTapped() }
                )
            }
            .pointerInput(block.id, isSelected) {
                if (isSelected) {
                    detectDragGestures { _, dragAmount ->
                        onDragged(
                            dragAmount.x / scaleX,
                            dragAmount.y / scaleY
                        )
                    }
                }
            }
    ) {
        if (isSelected && block.isEdited) {
            Text(
                text       = block.editedText,
                modifier   = Modifier.fillMaxSize().padding(1.dp),
                fontSize   = with(density) {
                    val rawSp = (block.effectiveFontSize * scaleY).toSp().value
                        .coerceIn(5f, 60f)
                    rawSp.sp
                },
                fontWeight = if (block.effectiveIsBold) FontWeight.Bold
                else FontWeight.Normal,
                color      = Color(0xFF0D5016),
                maxLines   = 20,
                overflow   = androidx.compose.ui.text.style.TextOverflow.Clip
            )
        }
    }
}

// ── Text edit dialog ──────────────────────────────────────────────────────────

@Composable
fun PdfTextEditDialog(
    block     : PdfTextBlock,
    onConfirm : (String) -> Unit,
    onDismiss : () -> Unit
) {
    var editText     by remember(block.id) { mutableStateOf(block.editedText) }
    val focusManager  = LocalFocusManager.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape     = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape    = RoundedCornerShape(8.dp),
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Edit Text",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${block.fontRecord.originalFontSize.roundToInt()} px",
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp, vertical = 2.dp
                                    ),
                                    style  = MaterialTheme.typography.labelSmall,
                                    color  = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    if (block.fontRecord.originalIsBold) "BOLD"
                                    else "Regular",
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp, vertical = 2.dp
                                    ),
                                    style  = MaterialTheme.typography.labelSmall,
                                    color  = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "ORIGINAL",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            block.text,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value         = editText,
                    onValueChange = { editText = it },
                    label         = { Text("New text") },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    maxLines      = 5,
                    trailingIcon  = {
                        if (editText != block.text)
                            IconButton(onClick = { editText = block.text }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reset",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }

                    Button(
                        onClick  = {
                            if (editText.isNotBlank()) onConfirm(editText)
                        },
                        modifier = Modifier.weight(1f),
                        enabled  = editText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply")
                    }
                }
            }
        }
    }
}

// ── Preview screen ────────────────────────────────────────────────────────────

@Composable
fun PdfPreviewScreen(
    previewPages : List<Bitmap>,
    isGenerating : Boolean,
    isSaving     : Boolean,
    totalEdits   : Int,
    onSave       : () -> Unit,
    onGoBack     : () -> Unit
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isGenerating || previewPages.isEmpty() ->
                FullScreenLoader("Generating preview...")

            else -> {
                LazyColumn(
                    state   = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2B2B2B)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp, start = 6.dp, end = 6.dp, bottom = 150.dp
                    )
                ) {
                    itemsIndexed(previewPages) { index, bitmap ->
                        Card(
                            modifier  = Modifier.fillMaxWidth(),
                            shape     = RoundedCornerShape(6.dp),
                            elevation = CardDefaults.cardElevation(4.dp),
                            colors    = CardDefaults.cardColors(
                                containerColor = Color.White
                            )
                        ) {
                            Column {
                                Image(
                                    bitmap             = bitmap.asImageBitmap(),
                                    contentDescription = "Preview page ${index + 1}",
                                    modifier           = Modifier.fillMaxWidth(),
                                    contentScale       = ContentScale.FillWidth
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF5F5F5))
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical   = 6.dp
                                        ),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Page ${index + 1} of ${previewPages.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Gray
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            "PREVIEW",
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp, vertical = 2.dp
                                            ),
                                            style      = MaterialTheme.typography.labelSmall,
                                            color      = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier        = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color           = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (totalEdits > 0) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors   = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme
                                        .primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint     = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "$totalEdits text block(s) modified",
                                        style      = MaterialTheme.typography.bodySmall,
                                        color      = MaterialTheme.colorScheme
                                            .onPrimaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick  = onGoBack,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Back to Edit")
                            }

                            Button(
                                onClick  = onSave,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape   = RoundedCornerShape(12.dp),
                                enabled = !isSaving
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(18.dp),
                                        color       = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Saving...")
                                } else {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save PDF")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfEditorWelcomeScreen(onPickFile: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(32.dp)
        ) {
            Surface(
                shape    = RoundedCornerShape(24.dp),
                color    = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(110.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint     = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                "PDF Text Editor",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Tap on any text in the PDF to select it.\n" +
                        "Double-tap to edit. Drag to move.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Button(
                onClick  = onPickFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Select PDF to Edit",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun FullScreenLoader(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(52.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Please wait...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
