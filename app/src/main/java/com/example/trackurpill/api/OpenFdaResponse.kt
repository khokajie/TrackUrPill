package com.example.trackurpill.api


// Data Classes
data class OpenFdaResponse(
    val meta: Meta,
    val results: List<FdaResult>
)

data class Meta(
    val disclaimer: String,
    val terms: String,
    val license: String,
    val last_updated: String
)

data class FdaResult(
    val patient: Patient
)

data class Patient(
    val reaction: List<Reaction>
)

data class Reaction(
    val reactionmeddrapt: String
)

data class DetailedHealthInfo(
    val adverseEvent: String,
    val recommendations: List<String>,
    val summary: String?,
    val severity: String?
)

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T): ApiResult<T>()
    data class Error(val exception: Exception): ApiResult<Nothing>()
}


