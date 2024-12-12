package com.example.trackurpill.api

import com.example.trackurpill.api.data.GeminiRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApiService {

    @POST("v1beta/models/gemini-1.5-flash-latest:generateContent")
    suspend fun generateContent(
        @Body request: GeminiRequest,
        @Query("key") apiKey: String
    ): Response<GeminiResponse>
}