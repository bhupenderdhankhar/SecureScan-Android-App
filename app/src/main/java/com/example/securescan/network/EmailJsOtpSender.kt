package com.example.securescan.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object EmailJsOtpSender {

    // ── Setup (takes 5 minutes, completely free) ──────────────────────────────
    // 1. Go to https://www.emailjs.com → Sign up free
    // 2. Add Email Service (Gmail/Outlook) → copy Service ID
    // 3. Create Email Template:
    //    Subject: "Your SecureScan OTP Code"
    //    Body:    "Your OTP is: {{otp_code}} — expires in 5 minutes."
    //    → copy Template ID
    // 4. Go to Account → copy Public Key
    // 5. Fill in below:
    // ─────────────────────────────────────────────────────────────────────────

    private const val SERVICE_ID  = "YOUR_SERVICE_ID"   // e.g. "service_abc123"
    private const val TEMPLATE_ID = "YOUR_TEMPLATE_ID"  // e.g. "template_xyz789"
    private const val PUBLIC_KEY  = "YOUR_PUBLIC_KEY"   // e.g. "user_AbCdEfGhIjKlMn"

    val isConfigured: Boolean
        get() = SERVICE_ID  != "YOUR_SERVICE_ID" &&
                TEMPLATE_ID != "YOUR_TEMPLATE_ID" &&
                PUBLIC_KEY  != "YOUR_PUBLIC_KEY"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val EMAILJS_URL =
        "https://api.emailjs.com/api/v1.0/email/send"

    suspend fun sendOtp(toEmail: String, otp: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("service_id",  SERVICE_ID)
                    put("template_id", TEMPLATE_ID)
                    put("user_id",     PUBLIC_KEY)
                    put("template_params", JSONObject().apply {
                        put("to_email",  toEmail)
                        put("otp_code",  otp)
                        put("app_name",  "SecureScan")
                    })
                }

                val body = json.toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(EMAILJS_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("origin", "http://localhost")
                    .build()

                val response = client.newCall(request).execute()
                val success  = response.isSuccessful
                Log.d("EmailJS", "Send OTP response: ${response.code} — ${response.body?.string()}")
                response.close()
                success

            } catch (e: Exception) {
                Log.e("EmailJS", "Failed to send OTP: ${e.message}", e)
                false
            }
        }
}