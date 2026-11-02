package com.example.securescan.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.securescan.ui.navigation.Screen
import com.example.securescan.utils.QrContentType
import com.example.securescan.utils.SecurityLevel
import com.example.securescan.viewmodel.QrViewModel
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRResultScreen(
    navController : NavController,
    encodedData   : String,
    viewModel     : QrViewModel = viewModel()
) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uiState   by viewModel.uiState.collectAsState()

    var showDangerDialog    by remember { mutableStateOf(false) }
    var showHistoryDialog   by remember { mutableStateOf(false) }
    var pendingOpenUrl      by remember { mutableStateOf("") }

    val rawValue = remember(encodedData) {
        try { URLDecoder.decode(encodedData, StandardCharsets.UTF_8.toString()) }
        catch (e: Exception) { encodedData }
    }

    // Analyze on load if not already done
    LaunchedEffect(rawValue) {
        if (uiState.rawValue != rawValue) {
            viewModel.analyzeQrCode(rawValue)
        }
    }

    // Danger popup
    if (showDangerDialog) {
        AlertDialog(
            onDismissRequest = { showDangerDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(36.dp))
            },
            title = {
                Text("⚠️ Dangerous Link",
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This link has been flagged as potentially dangerous. " +
                                "Opening it may expose your device to phishing or malware.",
                        textAlign = TextAlign.Center
                    )
                    uiState.security.reasons.forEach { reason ->
                        Text("• $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDangerDialog = false
                        openUrl(context, pendingOpenUrl)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) { Text("Open Anyway (Risky)") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDangerDialog = false }) {
                    Text("Stay Safe")
                }
            }
        )
    }

    // History dialog
    if (showHistoryDialog) {
        Dialog(onDismissRequest = { showHistoryDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Scan History",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        if (uiState.history.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearHistory() }) {
                                Text("Clear")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (uiState.history.isEmpty()) {
                        Text("No scan history yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.history.take(10).forEach { entry ->
                                Card(colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val (icon, color) = when (entry.security.level) {
                                            SecurityLevel.SAFE      -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
                                            SecurityLevel.SUSPICIOUS -> Icons.Default.Warning to Color(0xFFF57C00)
                                            SecurityLevel.DANGEROUS  -> Icons.Default.Cancel to Color(0xFFD32F2F)
                                            else -> Icons.Default.Info to Color.Gray
                                        }
                                        Icon(icon, contentDescription = null,
                                            tint     = color,
                                            modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.rawValue,
                                                style    = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis)
                                            Text(entry.contentType.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick  = { showHistoryDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Close") }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Result", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // History button
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(Icons.Default.History, contentDescription = "History",
                            tint = MaterialTheme.colorScheme.onPrimary)
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

            // Security status card (big, prominent)
            SecurityStatusCard(
                level     = uiState.security.level,
                isChecking = uiState.isChecking
            )

            // Content card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Content type badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val (typeIcon, typeLabel) = contentTypeInfo(uiState.contentType)
                        Icon(typeIcon, contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                typeLabel,
                                modifier   = Modifier.padding(
                                    horizontal = 10.dp, vertical = 4.dp),
                                style      = MaterialTheme.typography.labelMedium,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider()

                    // Raw value
                    Text("Scanned Content",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold)

                    Text(
                        uiState.rawValue.ifEmpty { rawValue },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Copy button
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            clipboard.setText(
                                AnnotatedString(uiState.rawValue.ifEmpty { rawValue })
                            )
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null,
                                modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Security reasons card
            if (uiState.security.reasons.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (uiState.security.level) {
                            SecurityLevel.DANGEROUS  -> Color(0xFFFFF3F3)
                            SecurityLevel.SUSPICIOUS -> Color(0xFFFFF8E1)
                            else                     -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Security Analysis",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = when (uiState.security.level) {
                                SecurityLevel.DANGEROUS  -> Color(0xFFD32F2F)
                                SecurityLevel.SUSPICIOUS -> Color(0xFFF57C00)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        uiState.security.reasons.forEach { reason ->
                            Text(reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // Action buttons
            // Action buttons based on content type
            when (uiState.contentType) {
                QrContentType.URL -> {
                    if (!uiState.isChecking) {
                        val url = uiState.rawValue.ifEmpty { rawValue }
                        Button(
                            onClick = {
                                when (uiState.security.level) {
                                    SecurityLevel.SAFE -> openUrl(context, url)
                                    else -> {
                                        pendingOpenUrl = url
                                        showDangerDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = when (uiState.security.level) {
                                    SecurityLevel.SAFE       -> Color(0xFF388E3C)
                                    SecurityLevel.SUSPICIOUS -> Color(0xFFF57C00)
                                    else                     -> Color(0xFFD32F2F)
                                }
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when (uiState.security.level) {
                                    SecurityLevel.SAFE       -> "Open Link"
                                    SecurityLevel.SUSPICIOUS -> "Open (Use Caution)"
                                    else                     -> "Open Anyway (Risky)"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }

                QrContentType.EMAIL -> {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse(
                                    if (uiState.rawValue.startsWith("mailto:"))
                                        uiState.rawValue
                                    else "mailto:${uiState.rawValue}"
                                )
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Email")
                    }
                }

                QrContentType.PHONE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val phone = uiState.rawValue
                                    .removePrefix("tel:").trim()
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:$phone")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call")
                        }
                        OutlinedButton(
                            onClick = {
                                val phone = uiState.rawValue
                                    .removePrefix("tel:").trim()
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:$phone")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Sms, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SMS")
                        }
                    }
                }

                QrContentType.WIFI -> {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Wi-Fi Network Details",
                                    fontWeight = FontWeight.SemiBold,
                                    style      = MaterialTheme.typography.bodyMedium)
                                Text(uiState.rawValue,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }

                QrContentType.CONTACT -> {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    type = "text/vcard"
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) { }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContactPage, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Contacts")
                    }
                }

                else -> { /* TEXT — no special action */ }
            }

// Scan again — always shown
            OutlinedButton(
                onClick  = {
                    navController.navigate(Screen.QrScanner.route) {
                        popUpTo(Screen.QrResult.route) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Again", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

// ── Security status card ──────────────────────────────────────────────────────

@Composable
fun SecurityStatusCard(
    level      : SecurityLevel,
    isChecking : Boolean
) {
    val (bgGradient, icon, title, subtitle) = when {
        isChecking -> Quadruple(
            listOf(Color(0xFF1565C0), Color(0xFF1976D2)),
            Icons.Default.Search,
            "Analyzing...",
            "Checking URL safety"
        )
        level == SecurityLevel.SAFE -> Quadruple(
            listOf(Color(0xFF2E7D32), Color(0xFF388E3C)),
            Icons.Default.VerifiedUser,
            "✅ Safe",
            "No threats detected"
        )
        level == SecurityLevel.SUSPICIOUS -> Quadruple(
            listOf(Color(0xFFE65100), Color(0xFFF57C00)),
            Icons.Default.Warning,
            "⚠️ Suspicious",
            "Proceed with caution"
        )
        level == SecurityLevel.DANGEROUS -> Quadruple(
            listOf(Color(0xFFB71C1C), Color(0xFFD32F2F)),
            Icons.Default.GppBad,
            "❌ Dangerous",
            "Do not open this link"
        )
        else -> Quadruple(
            listOf(Color(0xFF424242), Color(0xFF616161)),
            Icons.Default.Info,
            "Unknown",
            "Content type not analyzed"
        )
    }

    // Pulse animation for checking state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (isChecking) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(bgGradient))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape  = CircleShape,
                    color  = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                color    = Color.White,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint     = Color.White
                            )
                        }
                    }
                }
                Text(
                    title,
                    color      = Color.White,
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    color    = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component4() = d

private fun contentTypeInfo(type: QrContentType): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
    return when (type) {
        QrContentType.URL     -> Icons.Default.Link to "URL"
        QrContentType.EMAIL   -> Icons.Default.Email to "Email"
        QrContentType.PHONE   -> Icons.Default.Phone to "Phone"
        QrContentType.CONTACT -> Icons.Default.ContactPage to "Contact"
        QrContentType.WIFI    -> Icons.Default.Wifi to "Wi-Fi"
        QrContentType.TEXT    -> Icons.Default.TextFields to "Text"
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        val fullUrl = if (url.startsWith("http://") || url.startsWith("https://"))
            url else "https://$url"
        val uri = Uri.parse(fullUrl)

        // Try to find a specific app that handles this URL (deep link)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Check if any non-browser app handles this
        val pm           = context.packageManager
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val nonBrowserApps = resolveInfos.filter { info ->
            val pkg = info.activityInfo.packageName
            // Exclude common browsers
            !pkg.contains("chrome") && !pkg.contains("firefox") &&
                    !pkg.contains("browser") && !pkg.contains("opera") &&
                    !pkg.contains("samsung.internet") && !pkg.contains("android.browser")
        }

        if (nonBrowserApps.isNotEmpty()) {
            // Has a specific app — let user choose
            val chooser = Intent.createChooser(intent, "Open with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } else {
            // Open in browser directly
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        // Fallback
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ex: Exception) {
            android.widget.Toast.makeText(
                context, "Cannot open link", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@Composable
fun rememberScrollState() = androidx.compose.foundation.rememberScrollState()