package com.example.trackurpill.api

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
                val events = openFdaService.getAdverseEvents(
                    searchQuery = "patient.drug.medicinalproduct:\"$drugName\"",
                    limit = 5
                ).results.flatMap { it.patient.reaction }
                    .mapNotNull { it.reactionmeddrapt }
                    .distinct()
                    .take(5)
                    .map { event ->
                        DetailedHealthInfo(
                            adverseEvent = event,
                            recommendations = getRecommendationsForEvent(event),
                            summary = getSummaryForEvent(event),
                            severity = calculateSeverity(event)
                        )
                    }
                    .sortedByDescending { it.severity == "High" }

                ApiResult.Success(events)
            } catch (e: Exception) {
                ApiResult.Error(e)
            }
        }

    private fun getSummaryForEvent(event: String): String {
        val eventLower = event.lowercase()
        return when {
            eventLower.contains("severe") || eventLower.contains("critical") ->
                "This is a serious side effect that requires immediate medical attention."
            eventLower.contains("allerg") || eventLower.contains("hypersensitivity") ->
                "This indicates a potential allergic reaction. Stop medication and seek medical care."
            else ->
                "This is a known side effect. Contact your healthcare provider if experienced."
        }
    }

    private fun getRecommendationsForEvent(event: String): List<String> {
        val eventLower = event.lowercase()

        return when {
            // Allergic Reactions
            eventLower.contains("allerg") || eventLower.contains("hypersensitivity") ||
                    eventLower.contains("anaphyla") -> listOf(
                "Stop taking medication and seek immediate medical attention",
                "Use prescribed emergency medications if available",
                "Document time and nature of allergic symptoms",
                "Inform all healthcare providers about this allergy"
            )

            // Gastrointestinal Issues
            eventLower.contains("nausea") || eventLower.contains("vomit") ||
                    eventLower.contains("diarrhea") || eventLower.contains("stomach") -> listOf(
                "Take medication with food unless otherwise directed",
                "Stay hydrated with clear fluids",
                "Eat small, frequent meals",
                "Contact doctor if symptoms persist over 24 hours"
            )

            // Cardiovascular
            eventLower.contains("heart") || eventLower.contains("chest") ||
                    eventLower.contains("blood pressure") -> listOf(
                "Seek immediate medical attention for severe symptoms",
                "Monitor blood pressure if equipment is available",
                "Record frequency and duration of symptoms",
                "Avoid strenuous activity until consulting doctor"
            )

            // Neurological
            eventLower.contains("dizz") || eventLower.contains("headache") ||
                    eventLower.contains("somnolence") || eventLower.contains("drowsy") -> listOf(
                "Avoid driving or operating machinery",
                "Change positions slowly to prevent falls",
                "Rest in a quiet, dark environment if needed",
                "Note if symptoms are worse at certain times"
            )

            // Skin Reactions
            eventLower.contains("rash") || eventLower.contains("itch") ||
                    eventLower.contains("skin") -> listOf(
                "Avoid scratching affected areas",
                "Use recommended topical treatments if prescribed",
                "Document appearance and spread of reaction",
                "Take photos to show healthcare provider"
            )

            // Pain Related
            eventLower.contains("pain") || eventLower.contains("ache") ||
                    eventLower.contains("sore") -> listOf(
                "Track pain intensity on a scale of 1-10",
                "Note activities that worsen or improve pain",
                "Use prescribed pain management techniques",
                "Apply hot/cold therapy if recommended"
            )

            // Bleeding/Bruising
            eventLower.contains("bleed") || eventLower.contains("bruis") ||
                    eventLower.contains("hemorrhage") -> listOf(
                "Seek immediate care for severe bleeding",
                "Apply direct pressure to bleeding sites",
                "Avoid activities with injury risk",
                "Report any unusual bleeding to doctor"
            )

            // Sleep/Fatigue
            eventLower.contains("sleep") || eventLower.contains("fatigue") ||
                    eventLower.contains("tired") -> listOf(
                "Maintain regular sleep schedule",
                "Avoid caffeine and screens before bed",
                "Take medication at optimal timing",
                "Report excessive drowsiness to doctor"
            )

            // Mood Changes
            eventLower.contains("mood") || eventLower.contains("depress") ||
                    eventLower.contains("anxiety") -> listOf(
                "Keep a mood diary to track changes",
                "Maintain regular daily routines",
                "Stay connected with support system",
                "Seek immediate help for severe changes"
            )

            // Muscle-related
            eventLower.contains("muscle") || eventLower.contains("weakness") ||
                    eventLower.contains("spasm") -> listOf(
                "Avoid overexertion during activities",
                "Perform gentle stretching if appropriate",
                "Note any patterns in muscle symptoms",
                "Report progressive weakness promptly"
            )

            // Vision Changes
            eventLower.contains("vision") || eventLower.contains("eye") ||
                    eventLower.contains("sight") -> listOf(
                "Avoid driving if vision is impaired",
                "Protect eyes from bright light if sensitive",
                "Document any changes in vision",
                "Seek immediate care for severe changes"
            )

            // Default recommendations for other events
            else -> listOf(
                "Monitor and document symptoms",
                "Note timing relation to medication",
                "Report new or worsening symptoms",
                "Follow medication instructions carefully"
            )
        }
    }

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