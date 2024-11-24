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

    // Schedules a one-time reminder
    fun scheduleReminderAt(
        context: Context,
        reminderTimeMillis: Long,
        medicationName: String,
        medicationId: String,
        dosage: String
    ) {
        val intent = createIntent(context, medicationName, medicationId, dosage)
        val pendingIntent = createPendingIntent(context, medicationId, intent)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            requestExactAlarmPermission(context)
            return
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
            Toast.makeText(context, "Reminder set for ${Date(reminderTimeMillis)}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to set reminder: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Schedules a daily reminder
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

            // If the time has passed, schedule for the next day
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }

        scheduleRecurringReminder(
            context,
            calendar.timeInMillis,
            medicationName,
            medicationId,
            dosage,
            AlarmManager.INTERVAL_DAY
        )
    }

    // Schedules a weekly reminder
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

            // If the time has passed, schedule for the next week
            if (before(Calendar.getInstance())) add(Calendar.WEEK_OF_YEAR, 1)
        }

        scheduleRecurringReminder(
            context,
            calendar.timeInMillis,
            medicationName,
            medicationId,
            dosage,
            AlarmManager.INTERVAL_DAY * 7
        )
    }

    // Internal method to schedule recurring reminders
    private fun scheduleRecurringReminder(
        context: Context,
        startTimeMillis: Long,
        medicationName: String,
        medicationId: String,
        dosage: String,
        intervalMillis: Long
    ) {
        val intent = createIntent(context, medicationName, medicationId, dosage)
        val pendingIntent = createPendingIntent(context, medicationId, intent)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            // Reschedule using exact alarms for recurring reminders
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startTimeMillis, pendingIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to set recurring reminder: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Cancels a reminder
    fun cancelReminder(context: Context, medicationId: String) {
        val intent = createIntent(context, "", medicationId, "")
        val pendingIntent = createPendingIntent(context, medicationId, intent)

        pendingIntent?.let {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(it)
            Toast.makeText(context, "Reminder canceled.", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper to create Intent
    private fun createIntent(context: Context, medicationName: String, medicationId: String, dosage: String): Intent {
        return Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("medicationName", medicationName)
            putExtra("medicationId", medicationId)
            putExtra("dosage", dosage)
        }
    }

    // Helper to create PendingIntent
    private fun createPendingIntent(context: Context, medicationId: String, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Request exact alarm permission
    private fun requestExactAlarmPermission(context: Context) {
        Toast.makeText(context, "Permission to schedule exact alarms is required.", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
