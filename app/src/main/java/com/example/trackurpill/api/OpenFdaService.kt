package com.example.trackurpill.api

import retrofit2.http.GET
import retrofit2.http.Query

// API Interfaces
interface OpenFdaService {
    @GET("drug/event.json")
    suspend fun getAdverseEvents(
        @Query("search") searchQuery: String,
        @Query("limit") limit: Int = 5
    ): OpenFdaResponse
}


