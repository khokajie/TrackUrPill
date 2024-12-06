package com.example.trackurpill.util

import android.content.Context
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

object ReminderManager {
    private val functions = Firebase.functions

    fun scheduleReminder(
        context: Context,
        scheduledTime: Long,
        medicationName: String,
        medicationId: String,
        dosage: String,
        userId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val data = hashMapOf(
            "scheduledTime" to scheduledTime,
            "medicationName" to medicationName,
            "medicationId" to medicationId,
            "dosage" to dosage,
            "userId" to userId
        )

        functions
            .getHttpsCallable("scheduleReminder")
            .call(data)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }
}