package com.example.trackurpill.api.data

data class GeminiResponse(
    val candidates: List<Candidate>,
    val usageMetadata: UsageMetadata,
    val modelVersion: String
)

data class Candidate(
    val content: ContentResponse,
    val finishReason: String,
    val avgLogprobs: Double
)

data class ContentResponse(
    val parts: List<GeneratedPart>,
    val role: String
)

data class GeneratedPart(
    val text: String
)

data class UsageMetadata(
    val promptTokenCount: Int,
    val candidatesTokenCount: Int,
    val totalTokenCount: Int
)
