package com.example.trackurpill.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.trackurpill.R

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val medicationName = intent?.getStringExtra("medicationName") ?: "Medication"
        val medicationId = intent?.getStringExtra("medicationId") ?: ""
        val dosage = intent?.getStringExtra("dosage") ?: "1"

        // Create "Taken" Action
        val takenIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = "ACTION_TAKEN"
            putExtra("medicationId", medicationId)
            putExtra("dosage", dosage)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create "Remind Again" Action
        val remindAgainIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = "ACTION_REMIND_AGAIN"
            putExtra("medicationId", medicationId)
            putExtra("medicationName", medicationName)
            putExtra("dosage", dosage)
        }
        val remindAgainPendingIntent = PendingIntent.getBroadcast(
            context,
            (medicationId.hashCode() + 1),
            remindAgainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build Notification
        val notification = NotificationCompat.Builder(context, "REMINDER_CHANNEL")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Medication Reminder")
            .setContentText("It's time to take your $medicationName. Dosage: $dosage")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_check, "Taken", takenPendingIntent)
            .addAction(R.drawable.ic_alarm, "Remind Again", remindAgainPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(medicationId.hashCode(), notification)
    }
}
