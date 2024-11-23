package com.example.trackurpill.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.example.trackurpill.notification.ReminderBroadcastReceiver
import java.util.*

object ReminderScheduler {

    /**
     * Schedules a one-time reminder at a specific time.
     *
     * @param context The application context.
     * @param reminderTimeMillis The exact time in milliseconds for the reminder.
     * @param medicationName The name of the medication.
     * @param medicationId The ID of the medication.
     * @param dosage The dosage information.
     */
    fun scheduleReminderAt(
        context: Context,
        reminderTimeMillis: Long,
        medicationName: String,
        medicationId: String,
        dosage: String
    ) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("medicationName", medicationName)
            putExtra("medicationId", medicationId)
            putExtra("dosage", dosage)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Check if the app can schedule exact alarms (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                requestExactAlarmPermission(context)
                return
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
            Toast.makeText(context, "Reminder set for ${Date(reminderTimeMillis)}", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Failed to set reminder. Check permissions.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Schedules a daily recurring reminder at a specific time.
     *
     * @param context The application context.
     * @param reminderHour The hour of the reminder.
     * @param reminderMinute The minute of the reminder.
     * @param medicationName The name of the medication.
     * @param medicationId The ID of the medication.
     * @param dosage The dosage information.
     */
    fun scheduleDailyReminder(
        context: Context,
        reminderHour: Int,
        reminderMinute: Int,
        medicationName: String,
        medicationId: String,
        dosage: String
    ) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminderHour)
            set(Calendar.MINUTE, reminderMinute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1) // Schedule for the next day if the time has passed
            }
        }
        scheduleRecurringReminder(context, calendar.timeInMillis, medicationName, medicationId, dosage, AlarmManager.INTERVAL_DAY)
    }

    /**
     * Schedules a weekly recurring reminder at a specific time on a specific day.
     *
     * @param context The application context.
     * @param reminderHour The hour of the reminder.
     * @param reminderMinute The minute of the reminder.
     * @param dayOfWeek The day of the week (1 = Sunday, 7 = Saturday).
     * @param medicationName The name of the medication.
     * @param medicationId The ID of the medication.
     * @param dosage The dosage information.
     */
    fun scheduleWeeklyReminder(
        context: Context,
        reminderHour: Int,
        reminderMinute: Int,
        dayOfWeek: Int,
        medicationName: String,
        medicationId: String,
        dosage: String
    ) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, reminderHour)
            set(Calendar.MINUTE, reminderMinute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.WEEK_OF_YEAR, 1) // Schedule for the next week if the time has passed
            }
        }
        scheduleRecurringReminder(context, calendar.timeInMillis, medicationName, medicationId, dosage, AlarmManager.INTERVAL_DAY * 7)
    }

    /**
     * Cancels a previously scheduled reminder.
     *
     * @param context The application context.
     * @param medicationId The ID of the medication whose reminder needs to be canceled.
     */
    fun cancelReminder(context: Context, medicationId: String) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent?.let {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(it)
            Toast.makeText(context, "Reminder canceled.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Schedules a recurring reminder.
     *
     * @param context The application context.
     * @param startTimeMillis The start time in milliseconds for the reminder.
     * @param medicationName The name of the medication.
     * @param medicationId The ID of the medication.
     * @param dosage The dosage information.
     * @param intervalMillis The interval in milliseconds for repeating the reminder.
     */
    private fun scheduleRecurringReminder(
        context: Context,
        startTimeMillis: Long,
        medicationName: String,
        medicationId: String,
        dosage: String,
        intervalMillis: Long
    ) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("medicationName", medicationName)
            putExtra("medicationId", medicationId)
            putExtra("dosage", dosage)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                startTimeMillis,
                intervalMillis,
                pendingIntent
            )
            Toast.makeText(context, "Recurring reminder set.", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Failed to set recurring reminder. Check permissions.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Redirects the user to the exact alarm permission settings (for Android 12+).
     *
     * @param context The application context.
     */
    private fun requestExactAlarmPermission(context: Context) {
        Toast.makeText(context, "Permission to schedule exact alarms is required.", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
