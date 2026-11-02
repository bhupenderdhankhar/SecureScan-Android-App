package com.example.securescan.viewmodel

import android.app.Application
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.securescan.network.OtpServiceClient
import com.example.securescan.network.SendOtpRequest
import com.example.securescan.repository.FirestoreRepository
import com.example.securescan.utils.AuthManager
import com.example.securescan.utils.OtpVerifyResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.example.securescan.network.EmailJsOtpSender


data class LoginUiState(
    val email            : String  = "",
    val otp              : String  = "",
    val isLoading        : Boolean = false,
    val otpSent          : Boolean = false,
    val isLoggedIn       : Boolean = false,
    val errorMessage     : String? = null,
    val successMessage   : String? = null,
    val cooldownSeconds  : Long    = 0L,
    val otpExpirySeconds : Long    = 0L,
    val emailError       : String? = null,
    val otpError         : String? = null,
    // For dev testing — shows OTP in UI when backend not configured
    val devOtpHint       : String? = null
)

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val TAG = "SecureScan_OTP"
    private val authManager = AuthManager(app.applicationContext)
    private val firestoreRepo = FirestoreRepository()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            val valid = authManager.isSessionValid()
            if (valid) {
                _uiState.update { it.copy(isLoggedIn = true) }
            }
        }
    }

    fun onEmailChanged(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = null,
                errorMessage = null,
                devOtpHint = null
            )
        }
    }

    fun onOtpChanged(value: String) {
        if (value.length <= 6 && value.all { c -> c.isDigit() }) {
            _uiState.update {
                it.copy(otp = value, otpError = null, errorMessage = null)
            }
        }
    }

    fun sendOtp() {
        val email = _uiState.value.email.trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(emailError = "Enter a valid email address") }
            return
        }

        viewModelScope.launch {
            if (!authManager.canRequestOtp()) {
                val remaining = authManager.remainingCooldownSeconds()
                _uiState.update {
                    it.copy(errorMessage = "Please wait ${remaining}s before requesting again")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    emailError = null
                )
            }

            try {
                val otp = generateOtp()

                // Save OTP locally for current verification flow
                authManager.saveOtp(otp, email)

                var emailSent = false

                // Method 1: EmailJS
                if (!emailSent && EmailJsOtpSender.isConfigured) {
                    try {
                        emailSent = EmailJsOtpSender.sendOtp(email, otp)
                        if (!emailSent) {
                            android.util.Log.w("LoginViewModel", "EmailJS returned failure")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LoginViewModel", "EmailJS error: ${e.message}", e)
                    }
                }

                // Method 2: Custom backend
                if (!emailSent && OtpServiceClient.isBackendConfigured) {
                    try {
                        val response = OtpServiceClient.service.sendOtp(
                            SendOtpRequest(email = email, otp = otp)
                        )
                        emailSent = response.success

                        if (!emailSent) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Backend failed: ${response.message}"
                                )
                            }
                            return@launch
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Timeout - check internet or backend URL"
                            )
                        }
                        return@launch
                    } catch (e: java.net.UnknownHostException) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Cannot reach server: ${e.message}"
                            )
                        }
                        return@launch
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Network error: ${e.message}"
                            )
                        }
                        return@launch
                    }
                }

                if (emailSent) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            otpSent = true,
                            otp = "",
                            successMessage = "OTP sent to $email"
                        )
                    }
                } else {
                    // Dev fallback
                    android.util.Log.d("LoginViewModel", "==========================================")
                    android.util.Log.d("LoginViewModel", "DEV MODE - OTP for $email : $otp")
                    android.util.Log.d("LoginViewModel", "==========================================")

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            otpSent = true,
                            otp = "",
                            successMessage = "Dev mode active"
                        )
                    }
                }

                startCountdowns()

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unexpected error: ${e.message}"
                    )
                }
            }
        }
    }


    fun verifyOtp() {
        val email = _uiState.value.email.trim().lowercase()
        val otp   = _uiState.value.otp.trim()

        if (otp.length < 6) {
            _uiState.update { it.copy(otpError = "Enter the complete 6-digit OTP") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = authManager.verifyOtp(otp, email)) {
                OtpVerifyResult.SUCCESS -> {
                    authManager.saveLogin(email)
                    firestoreRepo.recordLogin(email)
                        .onFailure {
                            Log.w(TAG, "Firestore error: ${it.message}")
                        }
                    countdownJob?.cancel()
                    _uiState.update {
                        it.copy(
                            isLoading      = false,
                            isLoggedIn     = true,
                            successMessage = "Welcome! Login successful."
                        )
                    }
                }

                OtpVerifyResult.EXPIRED -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            otpError  = "OTP has expired. Tap Resend OTP.",
                            otpSent   = false
                        )
                    }
                }

                OtpVerifyResult.INVALID -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            otpError  = "Incorrect OTP. Please try again."
                        )
                    }
                }
            }
        }
    }

    fun resendOtp() {
        _uiState.update {
            it.copy(
                otpSent      = false,
                otp          = "",
                otpError     = null,
                errorMessage = null,
                devOtpHint   = null
            )
        }
        sendOtp()
    }

    fun logout() {
        viewModelScope.launch {
            authManager.logout()
            countdownJob?.cancel()
            _uiState.update { LoginUiState() }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun startCountdowns() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val cooldown = authManager.remainingCooldownSeconds()
                val expiry   = authManager.otpRemainingSeconds()
                _uiState.update {
                    it.copy(
                        cooldownSeconds  = cooldown,
                        otpExpirySeconds = expiry
                    )
                }
                if (cooldown == 0L && expiry == 0L) break
                delay(1_000L)
            }
        }
    }

    private fun generateOtp(): String =
        Random.nextInt(100_000, 999_999).toString()
}