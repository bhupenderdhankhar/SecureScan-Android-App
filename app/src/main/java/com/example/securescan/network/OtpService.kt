package com.example.securescan.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class SendOtpRequest(
    val email: String,
    val otp: String,
    val appName: String = "SecureScan"
)

data class OtpApiResponse(
    val success: Boolean,
    val message: String = ""
)

interface OtpApiService {
    @POST("send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): OtpApiResponse
}

object OtpServiceClient {

    const val BASE_URL = "https://securescan-otp-backend.onrender.com/api/"

    val isBackendConfigured: Boolean
        get() = true

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    val service: OtpApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OtpApiService::class.java)
    }
}
