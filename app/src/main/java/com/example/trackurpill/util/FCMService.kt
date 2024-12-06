package com.example.trackurpill.util

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.trackurpill.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val reminderId = message.data["reminderId"]
        val medicationId = message.data["medicationId"]
        val medicationName = message.data["medicationName"]
        val dosage = message.data["dosage"]

        // Create the notification using your existing ReminderBroadcastReceiver logic
        val intent = Intent(this, ReminderActionReceiver::class.java).apply {
            action = "ACTION_TAKEN"
            putExtra("medicationId", medicationId)
            putExtra("reminderId", reminderId)
            putExtra("medicationName", medicationName)
            putExtra("dosage", dosage)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "REMINDER_CHANNEL")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Medication Reminder")
            .setContentText("Time to take $medicationName ($dosage)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(reminderId.hashCode(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Store FCM token
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance()
                .collection("User")
                .document(userId)
                .update("fcmToken", token)
        }
    }
}