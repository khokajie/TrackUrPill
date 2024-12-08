package com.example.trackurpill.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.trackurpill.MainActivity
import com.example.trackurpill.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "From: ${remoteMessage.from}")

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Title: ${it.title}")
            Log.d("FCM", "Message Notification Body: ${it.body}")
            sendNotification(it.title, it.body)
        }

        // Check if message contains a data payload.
        remoteMessage.data.isNotEmpty().let {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")
            // Handle data payload if needed
        }
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "medication_reminders"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // Ensure you have this icon
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(this)) {
            notify(0, notificationBuilder.build())
        }
    }

    /**
     * Updates the FCM token in Firestore based on the user's role.
     *
     * @param token The new FCM token.
     */
    private fun updateFCMToken(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        updateFCMTokenForRole(userId, "Patient", token)
        updateFCMTokenForRole(userId, "Caregiver", token)
    }

    /**
     * Updates the FCM token in Firestore for the specified role.
     *
     * @param userId The ID of the user.
     * @param role The role of the user ("Patient" or "Caregiver").
     */
    private fun updateFCMTokenForRole(userId: String, role: String, token: String) {
        val roleCollection = firestore.collection(role)
        val userDocument: DocumentReference = roleCollection.document(userId)

        userDocument.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userDocument.update("fcmToken", token)
                        .addOnSuccessListener {
                            Log.d("FCMService", "FCM token updated for $role.")
                        }
                        .addOnFailureListener { e ->
                            Log.e("FCMService", "Failed to update FCM token for $role", e)
                        }
                } else {
                    Log.e("FCMService", "User not found in $role collection.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FCMService", "Error fetching $role document", e)
            }
    }
}