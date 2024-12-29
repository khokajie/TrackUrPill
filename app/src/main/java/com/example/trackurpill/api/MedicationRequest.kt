package com.example.trackurpill.api

data class MedicationRequest(
    val medicationName: String,
    val dosage: String,
    val expirationDate: String,
    val stockLevel: Int,
    val instruction: String
)
