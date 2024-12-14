// RetrofitInstance.kt
package com.example.trackurpill.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // Initialize Logging Interceptor (Optional)
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Initialize OkHttpClient with increased timeouts
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS) // Increased from 10s to 30s
        .readTimeout(30, TimeUnit.SECONDS)    // Increased from 10s to 30s
        .writeTimeout(30, TimeUnit.SECONDS)   // Increased from 10s to 30s
        .build()

    // Initialize Retrofit
    val api: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
            .create(GeminiApiService::class.java)
    }
}
