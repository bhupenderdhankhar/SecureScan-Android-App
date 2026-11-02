package com.example.securescan.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.securescan.network.SafeBrowsingClient
import com.example.securescan.utils.QrContentType
import com.example.securescan.utils.SecurityChecker
import com.example.securescan.utils.SecurityLevel
import com.example.securescan.utils.SecurityResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class QrScanHistory(
    val rawValue   : String,
    val contentType: QrContentType,
    val security   : SecurityResult,
    val timestamp  : Long = System.currentTimeMillis()
)

data class QrUiState(
    val rawValue     : String         = "",
    val contentType  : QrContentType  = QrContentType.TEXT,
    val security     : SecurityResult = SecurityResult(SecurityLevel.UNKNOWN),
    val isChecking   : Boolean        = false,
    val errorMessage : String?        = null,
    val history      : List<QrScanHistory> = emptyList()
)

class QrViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(QrUiState())
    val uiState = _uiState.asStateFlow()

    fun analyzeQrCode(raw: String) {
        val decoded = try {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) { raw }

        val type = SecurityChecker.detectContentType(decoded)

        _uiState.update {
            it.copy(
                rawValue    = decoded,
                contentType = type,
                isChecking  = type == QrContentType.URL,
                security    = SecurityResult(
                    if (type == QrContentType.URL) SecurityLevel.CHECKING
                    else SecurityLevel.SAFE
                ),
                errorMessage = null
            )
        }

        if (type == QrContentType.URL) {
            checkUrlSecurity(decoded)
        }
    }

    private fun checkUrlSecurity(url: String) {
        viewModelScope.launch {
            // Step 1: Local check (instant)
            val localResult = SecurityChecker.performLocalCheck(url)

            _uiState.update { it.copy(security = localResult) }

            // Step 2: Safe Browsing API check
            if (SafeBrowsingClient.API_KEY != "YOUR_SAFE_BROWSING_API_KEY") {
                try {
                    val response = SafeBrowsingClient.checkUrl(url)
                    val matches  = response.matches

                    val apiResult = if (matches.isNullOrEmpty()) {
                        // API says safe — keep local result if suspicious
                        if (localResult.level == SecurityLevel.SUSPICIOUS ||
                            localResult.level == SecurityLevel.DANGEROUS) {
                            localResult
                        } else {
                            SecurityResult(SecurityLevel.SAFE,
                                listOf("✅ Verified safe by Google Safe Browsing"))
                        }
                    } else {
                        val threats  = matches.map { m ->
                            when (m.threatType) {
                                "MALWARE"                       -> "🚨 Malware detected"
                                "SOCIAL_ENGINEERING"            -> "🚨 Phishing site"
                                "UNWANTED_SOFTWARE"             -> "⚠️ Unwanted software"
                                "POTENTIALLY_HARMFUL_APPLICATION" -> "🚨 Harmful application"
                                else -> "⚠️ ${m.threatType}"
                            }
                        }
                        SecurityResult(
                            level         = SecurityLevel.DANGEROUS,
                            reasons       = threats,
                            safeBrowsing  = "Flagged by Google Safe Browsing"
                        )
                    }

                    _uiState.update {
                        it.copy(security = apiResult, isChecking = false)
                    }
                } catch (e: Exception) {
                    // API failed — use local result
                    _uiState.update {
                        it.copy(
                            isChecking   = false,
                            security     = localResult.copy(
                                reasons = localResult.reasons +
                                        listOf("ℹ️ Online check unavailable")
                            )
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(isChecking = false) }
            }

            // Save to history
            val current = _uiState.value
            val entry   = QrScanHistory(
                rawValue    = current.rawValue,
                contentType = current.contentType,
                security    = current.security
            )
            _uiState.update {
                it.copy(history = listOf(entry) + it.history.take(19))
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
    }
}