// File: app/src/main/java/com/example/trackurpill/util/NotificationActionReceiver.kt

package com.example.trackurpill.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        val notificationId = intent.getStringExtra("notificationId")
        val type = intent.getStringExtra("type") // "reminder" or "invitation"

        Log.d("NotificationActionReceiver", "Received action: $action, type: $type")

        when (type) {
            "reminder" -> {
                when (action) {
                    "ACTION_TAKE_MEDICATION" -> {
                        val reminderId = intent.getStringExtra("reminderId")
                        val medicationId = intent.getStringExtra("medicationId")
                        val dosage = intent.getStringExtra("dosage") ?: "1 Tablet"
                        takeMedication(context, reminderId, medicationId, dosage)
                        cancelNotification(context, notificationId)
                    }
                    "ACTION_DISMISS_REMINDER" -> {
                        val reminderId = intent.getStringExtra("reminderId")
                        dismissReminder(context, reminderId, notificationId)
                    }
                }
            }
            "invitation" -> {
                when (action) {
                    "ACTION_ACCEPT_INVITATION" -> {
                        val senderId = intent.getStringExtra("senderId")
                        acceptInvitation(context, notificationId, senderId)
                        cancelNotification(context, notificationId)
                    }
                    "ACTION_DECLINE_INVITATION" -> {
                        declineInvitation(context, notificationId)
                        cancelNotification(context, notificationId)
                    }
                }
            }
            else -> {
                Log.e("NotificationActionReceiver", "Unknown notification type: $type")
            }
        }
    }

    private fun takeMedication(
        context: Context,
        reminderId: String?,
        medicationId: String?,
        dosage: String
    ) {
        if (reminderId == null || medicationId == null) {
            Log.e("NotificationActionReceiver", "Missing reminderId or medicationId")
            Toast.makeText(context, "Invalid action parameters.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid

        if (userId == null) {
            Log.e("NotificationActionReceiver", "User not authenticated")
            Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT).show()
            return
        }

        // Update medication status
        db.collection("Medication").document(medicationId)
            .update(
                mapOf(
                    "status" to "taken",
                    "dosage" to dosage,
                    "takenDate" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Log.d("NotificationActionReceiver", "Medication marked as taken")
                Toast.makeText(context, "Medication marked as taken.", Toast.LENGTH_SHORT).show()

                // Optionally, add MedicationLog
                val medicationLog = mapOf(
                    "logId" to UUID.randomUUID().toString(),
                    "medicationId" to medicationId,
                    "dosage" to dosage,
                    "takenDate" to com.google.firebase.Timestamp.now(),
                    "userId" to userId
                )
                db.collection("MedicationLog").document(medicationLog["logId"]!!.toString())
                    .set(medicationLog)
                    .addOnSuccessListener {
                        Log.d("NotificationActionReceiver", "MedicationLog added")
                    }
                    .addOnFailureListener { e ->
                        Log.e("NotificationActionReceiver", "Error adding MedicationLog", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("NotificationActionReceiver", "Error updating medication", e)
                Toast.makeText(context, "Failed to mark medication as taken.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun dismissReminder(
        context: Context,
        reminderId: String?,
        notificationId: String?
    ) {
        if (reminderId == null || notificationId == null) {
            Log.e("NotificationActionReceiver", "Missing reminderId or notificationId")
            Toast.makeText(context, "Invalid action parameters.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()

        // Update reminder status
        db.collection("Reminder").document(reminderId)
            .update("status", "dismissed")
            .addOnSuccessListener {
                Log.d("NotificationActionReceiver", "Reminder dismissed")
                Toast.makeText(context, "Reminder dismissed.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("NotificationActionReceiver", "Error dismissing reminder", e)
                Toast.makeText(context, "Failed to dismiss reminder.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun acceptInvitation(
        context: Context,
        notificationId: String?,
        senderId: String?
    ) {
        if (notificationId == null || senderId == null) {
            Log.e("NotificationActionReceiver", "Missing notificationId or senderId")
            Toast.makeText(context, "Invalid action parameters.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid

        if (userId == null) {
            Log.e("NotificationActionReceiver", "User not authenticated")
            Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT).show()
            return
        }

        // Update invitation status
        db.collection("invitations").document(notificationId)
            .update("status", "accepted")
            .addOnSuccessListener {
                Log.d("NotificationActionReceiver", "Invitation accepted")
                Toast.makeText(context, "Invitation accepted.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("NotificationActionReceiver", "Error accepting invitation", e)
                Toast.makeText(context, "Failed to accept invitation.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun declineInvitation(context: Context, notificationId: String?) {
        if (notificationId == null) {
            Log.e("NotificationActionReceiver", "Missing notificationId")
            Toast.makeText(context, "Invalid action parameters.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()

        // Update invitation status
        db.collection("invitations").document(notificationId)
            .update("status", "declined")
            .addOnSuccessListener {
                Log.d("NotificationActionReceiver", "Invitation declined")
                Toast.makeText(context, "Invitation declined.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("NotificationActionReceiver", "Error declining invitation", e)
                Toast.makeText(context, "Failed to decline invitation.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cancelNotification(context: Context, notificationId: String?) {
        if (notificationId == null) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId.hashCode())
        Log.d("NotificationActionReceiver", "Notification canceled with ID: ${notificationId.hashCode()}")
    }
}
