package com.example.trackurpill.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import java.util.UUID

object ReminderScheduler {
    private val functions: FirebaseFunctions = Firebase.functions
    private const val TAG = "ReminderScheduler"

    /**
     * A generic function to schedule a reminder with all necessary fields.
     *
     * @param context The application context
     * @param reminderId Unique ID of the reminder
     * @param medicationId The ID of the medication
     * @param medicationName The name of the medication (optional for display)
     * @param dosage The dosage of the medication (optional for display)
     * @param userId The ID of the user
     * @param frequency The frequency of the reminder ("Once", "Daily", or "Weekly")
     * @param hour The hour of the reminder (0-23)
     * @param minute The minute of the reminder (0-59)
     * @param date The date of the reminder (required if frequency is "Once")
     * @param day The day of the week for the reminder (required if frequency is "Weekly")
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
        day: String? = null
    ) {
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

        val data = hashMapOf("reminder" to reminderData)

        functions.getHttpsCallable("scheduleReminder")
            .call(data)
            .addOnSuccessListener {
                Log.d(TAG, "Reminder scheduled successfully")
                Toast.makeText(context, "Reminder scheduled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to schedule reminder", e)
                Toast.makeText(context, "Failed to schedule: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * For "Once" type reminders.
     */
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

    /**
     * For "Daily" type reminders.
     */
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

    /**
     * For "Weekly" type reminders.
     */
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