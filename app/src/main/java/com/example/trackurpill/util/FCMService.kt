package com.example.trackurpill.util

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.trackurpill.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("Message Received", "Message received, send Notification")

        val reminderId = message.data["reminderId"]
        val medicationId = message.data["medicationId"]
        val medicationName = message.data["medicationName"]
        val dosage = message.data["dosage"]

        createReminderNotification(reminderId, medicationId, medicationName, dosage)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        updateFCMToken(token)
    }

    /**
     * Creates a reminder notification using the provided data.
     *
     * @param reminderId The ID of the reminder.
     * @param medicationId The ID of the medication.
     * @param medicationName The name of the medication.
     * @param dosage The dosage of the medication.
     */
    private fun createReminderNotification(
        reminderId: String?,
        medicationId: String?,
        medicationName: String?,
        dosage: String?
    ) {
        val intent = Intent(this, ReminderActionReceiver::class.java).apply {
            action = "ACTION_TAKEN"
            putExtra("medicationId", medicationId)
            putExtra("reminderId", reminderId)
            putExtra("medicationName", medicationName)
            putExtra("dosage", dosage)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminderId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "REMINDER_CHANNEL")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Medication Reminders")
            .setContentText("Time to take $medicationName ($dosage)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(reminderId?.hashCode() ?: 0, notification)
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