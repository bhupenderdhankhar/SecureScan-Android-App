package com.example.securescan.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.securescan.utils.PdfTextMatch
import com.example.securescan.utils.PdfUtils
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    navController: NavController,
    encodedUri: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val uri = remember {
        Uri.parse(URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString()))
    }

    var pages by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var pageCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var currentPage by remember { mutableIntStateOf(1) }

    var password by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var tempPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchingText by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PdfTextMatch>>(emptyList()) }
    var currentResultIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPage = listState.firstVisibleItemIndex + 1
    }

    LaunchedEffect(uri, password) {
        isLoading = true
        errorMessage = null
        passwordError = false
        searchResults = emptyList()
        currentResultIndex = 0

        scope.launch {
            try {
                PdfUtils.init(context)
                val count = PdfUtils.getPageCount(context, uri, password)

                if (count == 0) {
                    if (password == null && PdfUtils.isPasswordProtected(context, uri)) {
                        showPasswordDialog = true
                        isLoading = false
                        return@launch
                    } else if (password != null) {
                        passwordError = true
                        showPasswordDialog = true
                        isLoading = false
                        return@launch
                    }

                    errorMessage = "Unable to open this PDF."
                    isLoading = false
                    return@launch
                }

                pageCount = count
                val bitmaps = mutableListOf<Bitmap?>()

                for (i in 0 until count) {
                    val bmp = PdfUtils.renderPage(context, uri, i, 1080, password)
                    bitmaps.add(bmp)
                    pages = bitmaps.toList()
                }

                isLoading = false
            } catch (e: Exception) {
                Log.e("PdfViewer", "Error loading PDF", e)
                errorMessage = "Failed to open PDF."
                isLoading = false
            }
        }
    }

    suspend fun runSearch() {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            currentResultIndex = 0
            return
        }

        isSearchingText = true
        searchResults = PdfUtils.searchTextInBitmaps(pages, searchQuery)
        currentResultIndex = 0
        isSearchingText = false

        val firstMatch = searchResults.firstOrNull() ?: return
        listState.animateScrollToItem(firstMatch.pageIndex)
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { navController.popBackStack() },
            title = { Text("Locked PDF") },
            text = {
                Column {
                    Text("Enter the password to open this file.")
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = tempPassword,
                        onValueChange = {
                            tempPassword = it
                            passwordError = false
                        },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = passwordError,
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )

                    if (passwordError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Incorrect password",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    password = tempPassword
                    showPasswordDialog = false
                    isLoading = true
                }) {
                    Text("Open")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { navController.popBackStack() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Viewer",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = {
                        scale = (scale - 0.25f).coerceAtLeast(0.5f)
                    }) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom out")
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { scale = 1f }
                            }
                    ) {
                        Text(
                            text = "${(scale * 100).roundToInt()}%",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    IconButton(onClick = {
                        scale = (scale + 0.25f).coerceAtMost(4f)
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom in")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (searchVisible) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        currentResultIndex = 0
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { scope.launch { runSearch() } },
                                enabled = !isSearchingText
                            ) {
                                if (isSearchingText) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Search")
                                }
                            }

                            if (searchResults.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        currentResultIndex =
                                            if (currentResultIndex == 0) searchResults.lastIndex
                                            else currentResultIndex - 1

                                        scope.launch {
                                            listState.animateScrollToItem(
                                                searchResults[currentResultIndex].pageIndex
                                            )
                                        }
                                    }
                                ) {
                                    Text("Prev")
                                }

                                OutlinedButton(
                                    onClick = {
                                        currentResultIndex =
                                            (currentResultIndex + 1) % searchResults.size

                                        scope.launch {
                                            listState.animateScrollToItem(
                                                searchResults[currentResultIndex].pageIndex
                                            )
                                        }
                                    }
                                ) {
                                    Text("Next")
                                }
                            }
                        }

                        when {
                            isSearchingText -> {
                                Text(
                                    "Searching pages...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            searchQuery.isNotBlank() && searchResults.isEmpty() -> {
                                Text(
                                    "No matches found.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            searchResults.isNotEmpty() -> {
                                val selected = searchResults[currentResultIndex]
                                Text(
                                    text = "Match ${currentResultIndex + 1} of ${searchResults.size} • Page ${selected.pageIndex + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = selected.excerpt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        errorMessage ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (isLoading && pages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ViewerStatusCard(
                            currentPage = currentPage.coerceAtMost(pageCount.coerceAtLeast(1)),
                            totalPages = pageCount
                        )
                    }

                    itemsIndexed(pages) { index, bitmap ->
                        PdfPageCard(
                            bitmap = bitmap,
                            pageNumber = index + 1,
                            totalPages = pageCount,
                            scale = scale,
                            isMatch = searchResults.any { it.pageIndex == index }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ViewerStatusCard(
    currentPage: Int,
    totalPages: Int
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Document",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Page $currentPage / $totalPages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PdfPageCard(
    bitmap: Bitmap?,
    pageNumber: Int,
    totalPages: Int,
    scale: Float,
    isMatch: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (isMatch) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

    Column {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(scaleX = scale, scaleY = scale),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Page $pageNumber of $totalPages",
                    style = MaterialTheme.typography.bodySmall
                )

                if (isMatch) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Match") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}
