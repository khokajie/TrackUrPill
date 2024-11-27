package com.example.trackurpill.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.trackurpill.R
import com.example.trackurpill.data.Notification
import com.example.trackurpill.notification.util.ReminderActionReceiver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import java.util.UUID

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Retrieve intent extras
        val medicationName = intent?.getStringExtra("medicationName") ?: "Medication"
        val medicationId = intent?.getStringExtra("medicationId") ?: ""
        val dosage = intent?.getStringExtra("dosage") ?: "1"
        val intentUserId = intent?.getStringExtra("userId") ?: ""

        // Get the currently authenticated user
        val currentUser = FirebaseAuth.getInstance().currentUser
        val currentUserId = currentUser?.uid

        // Check if the userId from the intent matches the current user
        if (currentUserId == null || currentUserId != intentUserId) {
            // Either no user is logged in or the userId doesn't match
            println("User ID mismatch or no user logged in. Notification not sent.")
            return
        }

        val notificationManager = NotificationManagerCompat.from(context)

        // Create "Taken" Action
        val takenIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = "ACTION_TAKEN"
            putExtra("medicationId", medicationId)
            putExtra("dosage", dosage)
            putExtra("medicationName", medicationName)
            putExtra("userId", intentUserId)
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
            putExtra("userId", intentUserId)
        }
        val remindAgainPendingIntent = PendingIntent.getBroadcast(
            context,
            (medicationId.hashCode() + 1),
            remindAgainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss Notification Intent
        val dismissIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = "ACTION_DISMISS"
            putExtra("notificationId", medicationId.hashCode())
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (medicationId.hashCode() + 2),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build Notification
        val notification = NotificationCompat.Builder(context, "REMINDER_CHANNEL")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Medication Reminder")
            .setContentText("It's time to take your $medicationName. Dosage: $dosage")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Dismiss notification when tapped
            .setDeleteIntent(dismissPendingIntent) // Handle swiping away
            .addAction(R.drawable.ic_check, "Taken", takenPendingIntent)
            .addAction(R.drawable.ic_alarm, "Remind Again", remindAgainPendingIntent)
            .build()

        notificationManager.notify(medicationId.hashCode(), notification)

        // Save Notification Data to Firestore
        println("Notification created for userId: $currentUserId")
        saveNotificationToFirestore(context, medicationName, medicationId, dosage, currentUserId)
    }

    private fun saveNotificationToFirestore(
        context: Context,
        medicationName: String,
        medicationId: String,
        dosage: String,
        userId: String
    ) {
        val notificationId = UUID.randomUUID().toString()

        val notification = Notification(
            notificationId = notificationId,
            userId = userId,
            message = "It's time to take your $medicationName. Dosage: $dosage",
            receiveTime = Date(),
            type = "reminder"
        )

        val db = FirebaseFirestore.getInstance()
        db.collection("Notification")
            .document(notificationId)
            .set(notification)
            .addOnSuccessListener {
                println("Notification saved to Firestore for userId: $userId")
            }
            .addOnFailureListener { e ->
                println("Failed to save notification: ${e.message}")
            }
    }
}
