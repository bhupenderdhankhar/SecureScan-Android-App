package com.example.securescan.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "auth_prefs")

class AuthManager(private val context: Context) {

    companion object {
        private val KEY_EMAIL         = stringPreferencesKey("user_email")
        private val KEY_LOGIN_TIME    = longPreferencesKey("login_timestamp")
        private val KEY_OTP_HASH      = stringPreferencesKey("otp_hash")
        private val KEY_OTP_EXPIRY    = longPreferencesKey("otp_expiry")
        private val KEY_OTP_EMAIL     = stringPreferencesKey("otp_email")
        private val KEY_LAST_OTP_TIME = longPreferencesKey("last_otp_request")
        private val KEY_USER_LOGGED_OUT = booleanPreferencesKey("user_logged_out")

        private const val SESSION_DURATION_MS = Long.MAX_VALUE
        private const val OTP_EXPIRY_MS       = 5  * 60 * 1000L        // 5 minutes
        private const val OTP_COOLDOWN_MS     = 60 * 1000L             // 1 minute between requests
    }

    // ── Session ───────────────────────────────────────────────────────────────

    suspend fun saveLogin(email: String) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_EMAIL]      = email
            prefs[KEY_LOGIN_TIME] = System.currentTimeMillis()
            prefs[KEY_USER_LOGGED_OUT] = false
        }
    }

    suspend fun enableDefaultLoginIfAllowed(email: String): Boolean {
        val prefs = context.authDataStore.data.first()
        val userLoggedOut = prefs[KEY_USER_LOGGED_OUT] ?: false

        if (userLoggedOut) return false
        if (isSessionValid()) return true

        saveLogin(email)
        return true
    }

    suspend fun isSessionValid(): Boolean {
        val prefs     = context.authDataStore.data.first()
        val loginTime = prefs[KEY_LOGIN_TIME] ?: return false
        val email     = prefs[KEY_EMAIL]      ?: return false
        if (email.isBlank()) return false
        return System.currentTimeMillis() - loginTime < SESSION_DURATION_MS
    }

    suspend fun getLoggedInEmail(): String? {
        val prefs = context.authDataStore.data.first()
        if (!isSessionValid()) return null
        return prefs[KEY_EMAIL]
    }

    val userEmailFlow: Flow<String?> = context.authDataStore.data
        .map { prefs -> prefs[KEY_EMAIL] }

    suspend fun logout() {
        context.authDataStore.edit { prefs ->
            prefs.remove(KEY_EMAIL)
            prefs.remove(KEY_LOGIN_TIME)
            prefs.remove(KEY_OTP_HASH)
            prefs.remove(KEY_OTP_EXPIRY)
            prefs.remove(KEY_OTP_EMAIL)
            prefs[KEY_USER_LOGGED_OUT] = true
        }
    }

    // ── OTP ───────────────────────────────────────────────────────────────────

    /**
     * Saves a hashed OTP (SHA-256) with expiry.
     * NEVER stores plain OTP on device.
     */
    suspend fun saveOtp(otp: String, email: String) {
        val hashed = hashOtp(otp)
        val expiry = System.currentTimeMillis() + OTP_EXPIRY_MS
        context.authDataStore.edit { prefs ->
            prefs[KEY_OTP_HASH]      = hashed
            prefs[KEY_OTP_EXPIRY]    = expiry
            prefs[KEY_OTP_EMAIL]     = email
            prefs[KEY_LAST_OTP_TIME] = System.currentTimeMillis()
        }
    }

    suspend fun verifyOtp(inputOtp: String, email: String): OtpVerifyResult {
        val prefs     = context.authDataStore.data.first()
        val stored    = prefs[KEY_OTP_HASH]   ?: return OtpVerifyResult.INVALID
        val expiry    = prefs[KEY_OTP_EXPIRY] ?: return OtpVerifyResult.EXPIRED
        val otpEmail  = prefs[KEY_OTP_EMAIL]  ?: return OtpVerifyResult.INVALID

        if (email.lowercase() != otpEmail.lowercase()) return OtpVerifyResult.INVALID
        if (System.currentTimeMillis() > expiry)       return OtpVerifyResult.EXPIRED

        val inputHash = hashOtp(inputOtp)
        return if (inputHash == stored) {
            // Clear OTP after successful verification
            context.authDataStore.edit { it.remove(KEY_OTP_HASH) }
            OtpVerifyResult.SUCCESS
        } else {
            OtpVerifyResult.INVALID
        }
    }

    suspend fun canRequestOtp(): Boolean {
        val prefs    = context.authDataStore.data.first()
        val lastTime = prefs[KEY_LAST_OTP_TIME] ?: return true
        return System.currentTimeMillis() - lastTime > OTP_COOLDOWN_MS
    }

    suspend fun remainingCooldownSeconds(): Long {
        val prefs    = context.authDataStore.data.first()
        val lastTime = prefs[KEY_LAST_OTP_TIME] ?: return 0L
        val elapsed  = System.currentTimeMillis() - lastTime
        val remaining = OTP_COOLDOWN_MS - elapsed
        return if (remaining > 0) remaining / 1000 else 0L
    }

    suspend fun otpRemainingSeconds(): Long {
        val prefs  = context.authDataStore.data.first()
        val expiry = prefs[KEY_OTP_EXPIRY] ?: return 0L
        val remaining = expiry - System.currentTimeMillis()
        return if (remaining > 0) remaining / 1000 else 0L
    }

    private fun hashOtp(otp: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash   = digest.digest(otp.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

enum class OtpVerifyResult { SUCCESS, INVALID, EXPIRED }
