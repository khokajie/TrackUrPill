package com.example.trackurpill.util

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import com.example.trackurpill.MainActivity
import com.example.trackurpill.R

class FCMService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "From: ${remoteMessage.from}")

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Title: ${it.title}")
            Log.d("FCM", "Message Notification Body: ${it.body}")
            sendNotification(it.title, it.body, remoteMessage.data)
        }

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")
            // Handle data payload if needed
        }
    }

    private fun sendNotification(title: String?, messageBody: String?, data: Map<String, String>) {
        // Extract notificationId and dosage from data payload
        val notificationId = data["notificationId"] ?: ""
        val dosage = data["dosage"]?.toIntOrNull() ?: 1

        // Create intents for each action
        val takeMedicationIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_TAKE_MEDICATION"
            putExtra("reminderId", data["reminderId"])
            putExtra("medicationId", data["medicationId"])
            putExtra("notificationId", notificationId)
            putExtra("dosage", dosage)
        }
        val takeMedicationPendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, takeMedicationIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissReminderIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_DISMISS_REMINDER"
            putExtra("reminderId", data["reminderId"])
            putExtra("medicationId", data["medicationId"])
            putExtra("notificationId", notificationId)
        }
        val dismissReminderPendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, dismissReminderIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification channel if not already created
        createNotificationChannel()

        val channelId = "medication_reminders"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_logo_blue) // Ensure you have this icon in your drawable resources
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_taken, // Icon for "Taken" action
                "Taken",
                takeMedicationPendingIntent
            )
            .addAction(
                R.drawable.ic_dismiss, // Icon for "Dismiss" action
                "Dismiss",
                dismissReminderPendingIntent
            )

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId.hashCode(), notificationBuilder.build()) // Use notificationId for uniqueness
        }
    }

    /**
     * Creates a notification channel for Android 8.0 and above.
     */
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = "medication_reminders"
            val channelName = "Medication Reminders"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for medication reminders"
            }
            val notificationManager: android.app.NotificationManager =
                getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        updateFcmTokenInFirestore(token)
    }

    private fun updateFcmTokenInFirestore(token: String) {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val userRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("Patient")
                .document(userId)
            userRef.update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d("FCM", "FCM Token updated successfully.")
                }
                .addOnFailureListener { e ->
                    Log.w("FCM", "Error updating FCM Token", e)
                }
        }
    }
}