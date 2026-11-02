package com.example.securescan.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// ── Request models ────────────────────────────────────────────────────────────

data class SafeBrowsingRequest(
    val client   : ClientInfo,
    val threatInfo: ThreatInfo
)

data class ClientInfo(
    val clientId      : String = "securescan-app",
    val clientVersion : String = "1.0.0"
)

data class ThreatInfo(
    val threatTypes      : List<String> = listOf(
        "MALWARE", "SOCIAL_ENGINEERING",
        "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"
    ),
    val platformTypes    : List<String> = listOf("ANY_PLATFORM"),
    val threatEntryTypes : List<String> = listOf("URL"),
    val threatEntries    : List<ThreatEntry>
)

data class ThreatEntry(val url: String)

// ── Response models ───────────────────────────────────────────────────────────

data class SafeBrowsingResponse(
    val matches: List<ThreatMatch>? = null
)

data class ThreatMatch(
    val threatType     : String,
    val platformType   : String,
    val threatEntryType: String,
    val threat         : ThreatEntry
)

// ── API interface ─────────────────────────────────────────────────────────────

interface SafeBrowsingService {
    @POST("v4/threatMatches:find")
    suspend fun checkUrl(
        @Query("key") apiKey: String,
        @Body request: SafeBrowsingRequest
    ): SafeBrowsingResponse
}

object SafeBrowsingClient {

    private const val BASE_URL = "https://safebrowsing.googleapis.com/"

    // Replace with your actual API key from Google Cloud Console
    // Get free key: https://console.cloud.google.com → Safe Browsing API
    const val API_KEY = "AIzaSyC-Gxvb8gVN4nO33nj1qQH0aSaXf6ASn4s"

    val service: SafeBrowsingService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SafeBrowsingService::class.java)
    }

    suspend fun checkUrl(url: String): SafeBrowsingResponse {
        return service.checkUrl(
            apiKey  = API_KEY,
            request = SafeBrowsingRequest(
                client    = ClientInfo(),
                threatInfo = ThreatInfo(
                    threatEntries = listOf(ThreatEntry(url))
                )
            )
        )
    }
}