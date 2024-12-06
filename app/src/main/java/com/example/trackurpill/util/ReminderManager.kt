package com.example.trackurpill.util

import android.content.Context
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

object ReminderManager {
    private val functions: FirebaseFunctions = Firebase.functions

    /**
     * Schedule a reminder by calling the "scheduleReminder" Cloud Function.
     *
     * @param context Context
     * @param reminderId Unique ID of the reminder
     * @param medicationId Medication ID
     * @param medicationName Medication name (optional for display)
     * @param dosage Medication dosage (optional for display)
     * @param userId User ID
     * @param frequency "Once", "Daily", or "Weekly"
     * @param hour Reminder hour (0-23)
     * @param minute Reminder minute (0-59)
     * @param date Required if frequency is "Once" (format: "dd/MM/yyyy")
     * @param day Required if frequency is "Weekly" (e.g. "Monday")
     * @param onSuccess Callback function to be called on successful scheduling
     * @param onError Callback function to be called on error
     */
    fun scheduleReminder(
        context: Context,
        reminderId: String,
        medicationId: String,
        medicationName: String,
        dosage: String,
        userId: String,
        frequency: String,
        hour: Int,
        minute: Int,
        date: String? = null,
        day: String? = null,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val reminderData = hashMapOf(
                "reminderId" to reminderId,
                "medicationId" to medicationId,
                "frequency" to frequency,
                "hour" to hour,
                "minute" to minute
            )

            if (date != null) {
                reminderData["date"] = date
            }

            if (day != null) {
                reminderData["day"] = day
            }

            // The Cloud Function expects { "reminder": { ...fields... } }
            val data = hashMapOf("reminder" to reminderData)

            functions.getHttpsCallable("scheduleReminder")
                .call(data)
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    onError(e)
                }
        } catch (e: Exception) {
            Log.e("ReminderManager", "Error scheduling reminder", e)
            onError(e)
        }
    }
}