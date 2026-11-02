package com.example.securescan.ui.screens

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.securescan.ui.navigation.Screen
import com.example.securescan.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaFile(
    val id: Long,
    val name: String,
    val size: Long,
    val uri: Uri,
    val dateModified: Long,
    val mimeType: String
) {
    val formattedSize: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${"%.1f".format(size / 1024.0 / 1024.0)} MB"
        }

    val formattedDate: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            .format(Date(dateModified * 1000L))
}

enum class FileTab { PDFS, IMAGES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showImages by settingsViewModel.showImages.collectAsState()

    var allPdfs by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var allImages by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var activeTab by remember { mutableStateOf(FileTab.PDFS) }
    var fileToDelete by remember { mutableStateOf<MediaFile?>(null) }
    var fileToRename by remember { mutableStateOf<MediaFile?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun loadFiles() {
        scope.launch {
            isLoading = true
            allPdfs = loadMediaFiles(context, pdfsOnly = true)
            allImages = loadMediaFiles(context, pdfsOnly = false)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadFiles() }

    LaunchedEffect(showImages) {
        if (!showImages) activeTab = FileTab.PDFS
    }

    val displayedPdfs = remember(allPdfs, searchQuery) {
        if (searchQuery.isBlank()) allPdfs
        else allPdfs.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val displayedImages = remember(allImages, searchQuery) {
        if (searchQuery.isBlank()) allImages
        else allImages.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            icon = {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete file") },
            text = { Text("Delete \"${file.name}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteFile(context, file)
                        fileToDelete = null
                        loadFiles()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    fileToRename?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("Rename file") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("File name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            renameFile(context, file, trimmed)
                            fileToRename = null
                            loadFiles()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { fileToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search files") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f),
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                cursorColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    } else {
                        Text("Files", fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            searchQuery = ""
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(
                            if (isSearching) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearching = !isSearching
                        if (!isSearching) searchQuery = ""
                    }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { loadFiles() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showImages) {
                TabRow(
                    selectedTabIndex = activeTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    Tab(
                        selected = activeTab == FileTab.PDFS,
                        onClick = { activeTab = FileTab.PDFS },
                        text = { Text("PDFs") }
                    )
                    Tab(
                        selected = activeTab == FileTab.IMAGES,
                        onClick = { activeTab = FileTab.IMAGES },
                        text = { Text("Images") }
                    )
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading files")
                        }
                    }
                }

                activeTab == FileTab.IMAGES && showImages -> {
                    ImageFileGrid(
                        images = displayedImages,
                        onOpenFile = { openImage(context, it) },
                        onShareFile = { shareFile(context, it) },
                        onDeleteFile = { fileToDelete = it },
                        onRenameFile = {
                            fileToRename = it
                            renameText = it.name.substringBeforeLast(".")
                        }
                    )
                }

                else -> {
                    PdfFileList(
                        files = displayedPdfs,
                        onOpenPdf = { file ->
                            val encoded = URLEncoder.encode(
                                file.uri.toString(),
                                StandardCharsets.UTF_8.toString()
                            )
                            navController.navigate(Screen.PdfViewer.createRoute(encoded))
                        },
                        onShareFile = { shareFile(context, it) },
                        onDeleteFile = { fileToDelete = it },
                        onRenameFile = {
                            fileToRename = it
                            renameText = it.name.substringBeforeLast(".")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PdfFileList(
    files: List<MediaFile>,
    onOpenPdf: (MediaFile) -> Unit,
    onShareFile: (MediaFile) -> Unit,
    onDeleteFile: (MediaFile) -> Unit,
    onRenameFile: (MediaFile) -> Unit
) {
    if (files.isEmpty()) {
        EmptyState(
            icon = Icons.Default.FolderOpen,
            title = "No PDFs yet",
            message = "Your exported documents will appear here."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(files, key = { it.id }) { file ->
                PdfFileCard(
                    file = file,
                    onOpen = { onOpenPdf(file) },
                    onShare = { onShareFile(file) },
                    onDelete = { onDeleteFile(file) },
                    onRename = { onRenameFile(file) }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun PdfFileCard(
    file: MediaFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = file.name.removeSuffix(".pdf"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${file.formattedSize}  •  ${file.formattedDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.OpenInNew,
                        contentDescription = "Open",
                        onClick = { onOpen(); expanded = false }
                    )
                    IconActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Share,
                        contentDescription = "Share",
                        onClick = { onShare(); expanded = false }
                    )
                    IconActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Edit,
                        contentDescription = "Rename",
                        onClick = { onRename(); expanded = false }
                    )
                    IconActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { onDelete(); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ImageFileGrid(
    images: List<MediaFile>,
    onOpenFile: (MediaFile) -> Unit,
    onShareFile: (MediaFile) -> Unit,
    onDeleteFile: (MediaFile) -> Unit,
    onRenameFile: (MediaFile) -> Unit
) {
    val context = LocalContext.current

    if (images.isEmpty()) {
        EmptyState(
            icon = Icons.Default.ImageNotSupported,
            title = "No images yet",
            message = "Saved images will appear here."
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(images, key = { it.id }) { file ->
                ImageFileCard(
                    file = file,
                    context = context,
                    onOpen = { onOpenFile(file) },
                    onShare = { onShareFile(file) },
                    onDelete = { onDeleteFile(file) },
                    onRename = { onRenameFile(file) }
                )
            }
        }
    }
}

@Composable
fun ImageFileCard(
    file: MediaFile,
    context: Context,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file.uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(file.uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpen() }
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${file.formattedSize}  •  ${file.formattedDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.OpenInNew,
                    contentDescription = "Open",
                    onClick = { onOpen() }
                )
                IconActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Share,
                    contentDescription = "Share",
                    onClick = { onShare() }
                )
                IconActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Edit,
                    contentDescription = "Rename",
                    onClick = { onRename() }
                )
                IconActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { onDelete() }
                )
            }
        }
    }
}

@Composable
private fun IconActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private suspend fun loadMediaFiles(
    context: Context,
    pdfsOnly: Boolean
): List<MediaFile> = withContext(Dispatchers.IO) {
    val files = mutableListOf<MediaFile>()
    val collection = MediaStore.Files.getContentUri("external")

    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.MIME_TYPE
    )

    val selection = if (pdfsOnly) {
        "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
    } else {
        "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? AND (${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%')"
    }

    val selectionArgs = if (pdfsOnly) {
        arrayOf("%SecureScanner%", "application/pdf")
    } else {
        arrayOf("%SecureScanner%")
    }

    val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

    context.contentResolver.query(
        collection,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: continue
            val size = cursor.getLong(sizeCol)
            val date = cursor.getLong(dateCol)
            val mime = cursor.getString(mimeCol) ?: continue
            val uri = ContentUris.withAppendedId(collection, id)

            files += MediaFile(id, name, size, uri, date, mime)
        }
    }

    files
}

private fun deleteFile(context: Context, file: MediaFile) {
    try {
        context.contentResolver.delete(file.uri, null, null)
    } catch (e: Exception) {
        android.util.Log.w("FileManager", "Delete failed: ${e.message}")
    }
}

private fun renameFile(context: Context, file: MediaFile, newBaseName: String) {
    try {
        val extension = file.name.substringAfterLast(".", "")
        val newName = if (extension.isNotBlank()) {
            "$newBaseName.$extension"
        } else {
            newBaseName
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
        }

        context.contentResolver.update(file.uri, values, null, null)
    } catch (e: Exception) {
        android.util.Log.w("FileManager", "Rename failed: ${e.message}")
    }
}

private fun openImage(context: Context, file: MediaFile) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.w("FileManager", "Open image failed: ${e.message}")
    }
}

private fun shareFile(context: Context, file: MediaFile) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = file.mimeType
        putExtra(Intent.EXTRA_STREAM, file.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
}
