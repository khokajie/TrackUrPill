// File: app/src/main/java/com/example/trackurpill/util/FCMService.kt

package com.example.trackurpill.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.trackurpill.MainActivity
import com.example.trackurpill.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.UUID

class FCMService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCMService", "From: ${remoteMessage.from}")

        // Always handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCMService", "Messag data payload: ${remoteMessage.data}")
            val type = remoteMessage.data["type"]
            sendNotification(
                remoteMessage.data["title"],
                remoteMessage.data["body"],
                remoteMessage.data,
                type
            )
        } else {
            Log.w("FCMService", "Message data payload is empty")
        }
    }

    private fun sendNotification(
        title: String?,
        messageBody: String?,
        data: Map<String, String>,
        type: String?
    ) {
        Log.d("FCMService", "Building notification for type: $type")

        // Extract necessary fields from data payload
        val notificationIdStr = data["notificationId"] ?: UUID.randomUUID().toString()
        val dosage = data["dosage"] ?: "1 Tablet"

        // Determine the channel ID based on the notification type
        val channelId = when (type) {
            "reminder" -> "medication_reminders"
            "invitation" -> "invitation_notifications"
            "response" -> "response_notifications"
            else -> "default_channel"
        }

        // Create intents for each action based on type
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Create the main intent to open MainActivity and navigate to NotificationFragment
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            Log.d("FCMService", "Open Notification, navigate to Notification Fragment")
            putExtra("fragmentToOpen", "NotificationFragment")
        }

        val mainPendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            notificationIdStr.hashCode(),
            mainIntent,
            pendingIntentFlags
        )

        // Define action buttons based on type
        val actions = mutableListOf<NotificationCompat.Action>()

        when (type) {
            "reminder" -> {
                // Action 1: Take Medication
                val takeIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                    action = "ACTION_TAKE_MEDICATION"
                    putExtra("notificationId", notificationIdStr)
                    putExtra("medicationId", data["medicationId"])
                    putExtra("dosage", dosage)
                    putExtra("type", type)
                }
                val takePendingIntent = PendingIntent.getBroadcast(
                    this,
                    notificationIdStr.hashCode() + 1,
                    takeIntent,
                    pendingIntentFlags
                )
                actions.add(
                    NotificationCompat.Action(
                        R.drawable.ic_taken, // Replace with your actual icon
                        "Take Medication",
                        takePendingIntent
                    )
                )

                // Action 2: Dismiss Reminder
                val dismissIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                    action = "ACTION_DISMISS_REMINDER"
                    putExtra("notificationId", notificationIdStr)
                    putExtra("type", type)
                }
                val dismissPendingIntent = PendingIntent.getBroadcast(
                    this,
                    notificationIdStr.hashCode() + 2,
                    dismissIntent,
                    pendingIntentFlags
                )
                actions.add(
                    NotificationCompat.Action(
                        R.drawable.ic_dismiss, // Replace with your actual icon
                        "Dismiss",
                        dismissPendingIntent
                    )
                )
            }
        }

        // Create notification channel if not already created
        createNotificationChannel(type)

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_logo_blue) // Ensure you have this icon in your drawable resources
            .setContentTitle(title ?: "New Notification")
            .setContentText(messageBody ?: "You have a new notification.")
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))

        // Add each action individually
        for (action in actions) {
            notificationBuilder.addAction(action)
        }

        with(NotificationManagerCompat.from(this)) {
            notify(notificationIdStr.hashCode(), notificationBuilder.build()) // Use notificationId for uniqueness
            Log.d("FCMService", "Notification sent with ID: ${notificationIdStr.hashCode()}")
        }
    }

    /**
     * Creates a notification channel based on the notification type.
     */
    private fun createNotificationChannel(type: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = when (type) {
                "reminder" -> "medication_reminders"
                "invitation" -> "invitation_notifications"
                "response" -> "response_notifications"
                else -> "default_channel"
            }
            val channelName = when (type) {
                "reminder" -> "Medication Reminders"
                "invitation" -> "Invitation Notifications"
                "response" -> "Response Notifications"
                else -> "Default Notifications"
            }
            val importance = when (type) {
                "reminder", "invitation", "response"-> NotificationManager.IMPORTANCE_HIGH
                else -> NotificationManager.IMPORTANCE_DEFAULT
            }

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = when (type) {
                    "reminder" -> "Notifications for medication reminders"
                    "invitation" -> "Notifications for patient invitations"
                    "response" -> "Notifications for patient response"
                    else -> "General notifications"
                }
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("FCMService", "Notification channel created: $channelId")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMService", "Refreshed token: $token")
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
                    Log.d("FCMService", "FCM Token updated successfully.")
                }
                .addOnFailureListener { e ->
                    Log.w("FCMService", "Error updating FCM Token", e)
                }
        }
    }
}
