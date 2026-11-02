package com.example.securescan.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.securescan.ui.navigation.Screen
import com.example.securescan.viewmodel.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val viewModel : LoginViewModel = viewModel()
    val uiState   by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))

                // App icon + title
                Surface(
                    shape  = RoundedCornerShape(24.dp),
                    color  = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(100.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint     = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "SecureScan",
                    style      = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
                Text(
                    "Secure document management",
                    style  = MaterialTheme.typography.bodyMedium,
                    color  = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Login card
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Sign In",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Enter your email to receive a one-time password",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Email field
                        OutlinedTextField(
                            value         = uiState.email,
                            onValueChange = { viewModel.onEmailChanged(it) },
                            label         = { Text("Email Address") },
                            leadingIcon   = {
                                Icon(Icons.Default.Email, contentDescription = null)
                            },
                            trailingIcon  = {
                                if (uiState.email.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onEmailChanged("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            isError         = uiState.emailError != null,
                            supportingText  = uiState.emailError?.let { { Text(it) } },
                            modifier        = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction    = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            singleLine = true,
                            enabled    = !uiState.otpSent && !uiState.isLoading
                        )

                        // Error message
                        AnimatedVisibility(visible = uiState.errorMessage != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        uiState.errorMessage ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Send OTP button
                        if (!uiState.otpSent) {
                            Button(
                                onClick  = {
                                    focusManager.clearFocus()
                                    viewModel.sendOtp()
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape    = RoundedCornerShape(12.dp),
                                enabled  = !uiState.isLoading &&
                                        uiState.email.isNotBlank()
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(20.dp),
                                        color       = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sending OTP...")
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Send OTP",
                                        style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }

                        // OTP sent section
                        AnimatedVisibility(
                            visible = uiState.otpSent,
                            enter   = fadeIn() + expandVertically(),
                            exit    = fadeOut() + shrinkVertically()
                        ) {
                            OtpSection(
                                email            = uiState.email,
                                otp              = uiState.otp,
                                isLoading        = uiState.isLoading,
                                otpError         = uiState.otpError,
                                cooldownSeconds  = uiState.cooldownSeconds,
                                otpExpirySeconds = uiState.otpExpirySeconds,
                                devOtpHint       = uiState.devOtpHint,       // ADD THIS
                                onOtpChanged     = { viewModel.onOtpChanged(it) },
                                onVerify         = { viewModel.verifyOtp() },
                                onResend         = { viewModel.resendOtp() },
                                onChangeEmail    = {
                                    viewModel.onEmailChanged(uiState.email)
                                    viewModel.clearMessages()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "By signing in you agree to our Terms of Service",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun OtpSection(
    email            : String,
    otp              : String,
    isLoading        : Boolean,
    otpError         : String?,
    cooldownSeconds  : Long,
    otpExpirySeconds : Long,
    devOtpHint       : String? = null,   // ADD THIS
    onOtpChanged     : (String) -> Unit,
    onVerify         : () -> Unit,
    onResend         : () -> Unit,
    onChangeEmail    : () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        HorizontalDivider()
        // Dev mode OTP hint — remove in production
        if (devOtpHint != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF9C4)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧪", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Developer Mode",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color      = Color(0xFF5D4037)
                        )
                        Text(
                            devOtpHint,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color      = Color(0xFF1B5E20)
                        )
                        Text(
                            "Configure backend in OtpService.kt to hide this",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF795548)
                        )
                    }
                }
            }
        }

        // Email confirmation
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MarkEmailRead, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("OTP sent to",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(email,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                TextButton(onClick = onChangeEmail) {
                    Text("Change", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // OTP expiry timer
        if (otpExpirySeconds > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Timer, contentDescription = null,
                    tint = if (otpExpirySeconds < 60)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "OTP expires in ${otpExpirySeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (otpExpirySeconds < 60)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // OTP input — large boxes
        Text("Enter OTP",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value         = otp,
            onValueChange = onOtpChanged,
            label         = { Text("6-digit OTP") },
            leadingIcon   = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            isError         = otpError != null,
            supportingText  = otpError?.let { { Text(it) } },
            modifier        = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction    = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus(); onVerify() }
            ),
            singleLine  = true,
            placeholder = { Text("e.g. 482910") }
        )

        // Verify button
        Button(
            onClick  = { focusManager.clearFocus(); onVerify() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            enabled  = !isLoading && otp.length == 6
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    color       = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verifying...")
            } else {
                Icon(Icons.Default.VerifiedUser, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verify OTP", style = MaterialTheme.typography.titleSmall)
            }
        }

        // Resend OTP
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Didn't receive it?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick  = onResend,
                enabled  = cooldownSeconds == 0L && !isLoading
            ) {
                Text(
                    if (cooldownSeconds > 0) "Resend in ${cooldownSeconds}s"
                    else "Resend OTP",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}