package com.example.trackurpill.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Repository
class HealthRepository {
    private val openFdaService = Retrofit.Builder()
        .baseUrl("https://api.fda.gov/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build())
        .build()
        .create(OpenFdaService::class.java)

    suspend fun getDetailedHealthInfo(drugName: String): ApiResult<List<DetailedHealthInfo>> =
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                val limit = 5 // Desired number of reports
                val maxReactions = 5 // Desired number of reactions

                val adverseEventResponse = openFdaService.getAdverseEvents(
                    searchQuery = "patient.drug.medicinalproduct:\"$drugName\"",
                    limit = limit
                )

                val reactions = adverseEventResponse.results
                    .flatMap { it.patient.reaction }
                    .mapNotNull { it.reactionmeddrapt }
                    .distinct()
                    .take(maxReactions) // Limit the number of reactions

                val events = reactions.map { event ->
                    DetailedHealthInfo(
                        adverseEvent = event,
                        recommendations = getDefaultRecommendations(),
                        summary = getSummaryForEvent(),
                        severity = calculateSeverity(event)
                    )
                }

                Log.d("getDetailedHealthInfo", "Number of reports: ${adverseEventResponse.results.size}")
                Log.d("getDetailedHealthInfo", "Number of unique reactions: ${reactions.size}")

                ApiResult.Success(events)
            } catch (e: Exception) {
                Log.e("getDetailedHealthInfo", "Error fetching detailed health info", e)
                ApiResult.Error(e)
            }
        }


    private fun getDefaultRecommendations() = listOf(
        "Contact healthcare provider if reaction occurs",
        "Monitor symptoms closely",
        "Keep track of reaction timing",
        "Follow medication instructions carefully"
    )

    private fun getSummaryForEvent() =
        "This is a known potential side effect. Contact your healthcare provider if experienced."

    private fun calculateSeverity(event: String): String {
        val severityKeywords = mapOf(
            "High" to setOf("death", "fatal", "severe", "critical", "emergency"),
            "Medium" to setOf("significant", "moderate", "serious"),
            "Low" to setOf("mild", "minor", "slight")
        )
        return severityKeywords.entries.firstOrNull { (_, keywords) ->
            keywords.any { it in event.lowercase() }
        }?.key ?: "Medium"
    }
}