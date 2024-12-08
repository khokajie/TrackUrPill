package com.example.trackurpill.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import java.util.TimeZone

object ReminderScheduler {
    private val functions: FirebaseFunctions = Firebase.functions
    private const val TAG = "ReminderScheduler"

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
        day: String? = null
    ) {
        // Get the user's timezone
        val userTimeZone = TimeZone.getDefault().id

        val reminderData = hashMapOf(
            "reminderId" to reminderId,
            "medicationId" to medicationId,
            "frequency" to frequency,
            "hour" to hour,
            "minute" to minute
        )

        // Add optional fields if present
        if (date != null) {
            reminderData["date"] = date
        }

        if (day != null) {
            reminderData["day"] = day
        }

        // Create the complete data map including userTimeZone
        val data = hashMapOf(
            "reminder" to reminderData,
            "userTimeZone" to userTimeZone
        )

        // Log the data being sent
        Log.d(TAG, "Scheduling reminder with data: $data")

        functions.getHttpsCallable("scheduleReminder")
            .call(data)
            .addOnSuccessListener {
                Log.d(TAG, "Reminder scheduled successfully")
                Toast.makeText(context, "Reminder scheduled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to schedule reminder", e)
                Toast.makeText(
                    context,
                    "Failed to schedule reminder: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    fun scheduleReminderAt(
        context: Context,
        reminderId: String,
        medicationName: String,
        medicationId: String,
        dosage: String,
        userId: String,
        date: String,
        hour: Int,
        minute: Int
    ) {
        scheduleReminder(
            context,
            reminderId,
            medicationId,
            medicationName,
            dosage,
            userId,
            frequency = "Once",
            hour = hour,
            minute = minute,
            date = date
        )
    }

    fun scheduleDailyReminder(
        context: Context,
        reminderId: String,
        medicationName: String,
        medicationId: String,
        dosage: String,
        userId: String,
        hour: Int,
        minute: Int
    ) {
        scheduleReminder(
            context,
            reminderId,
            medicationId,
            medicationName,
            dosage,
            userId,
            frequency = "Daily",
            hour = hour,
            minute = minute
        )
    }

    fun scheduleWeeklyReminder(
        context: Context,
        reminderId: String,
        medicationName: String,
        medicationId: String,
        dosage: String,
        userId: String,
        hour: Int,
        minute: Int,
        day: String
    ) {
        scheduleReminder(
            context,
            reminderId,
            medicationId,
            medicationName,
            dosage,
            userId,
            frequency = "Weekly",
            hour = hour,
            minute = minute,
            day = day
        )
    }
}