// File: app/src/main/java/com/example/trackurpill/util/NotificationActionReceiver.kt

package com.example.trackurpill.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.util.Log
import android.widget.Toast
import com.example.trackurpill.data.Medication
import com.example.trackurpill.data.MedicationLog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
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
                        val dosageStr = intent.getStringExtra("dosage") ?: "1 Tablet"
                        handleTakeMedication(context, reminderId, medicationId, dosageStr, notificationId)
                    }
                    "ACTION_DISMISS_REMINDER" -> {
                        val reminderId = intent.getStringExtra("reminderId")
                        handleDismissReminder(context, reminderId, notificationId)
                    }
                }
            }
            "invitation" -> {
                when (action) {
                    "ACTION_ACCEPT_INVITATION" -> {
                        val senderId = intent.getStringExtra("senderId")
                        val patientId = intent.getStringExtra("patientId")
                        handleAcceptInvitation(context, notificationId, senderId, patientId)
                    }
                    "ACTION_DECLINE_INVITATION" -> {
                        handleDeclineInvitation(context, notificationId)
                    }
                }
            }
            else -> {
                Log.e("NotificationActionReceiver", "Unknown notification type: $type")
            }
        }
    }

    /**
     * Handles the "Take Medication" action.
     */
    private fun handleTakeMedication(
        context: Context,
        reminderId: String?,
        medicationId: String?,
        dosageStr: String,
        notificationId: String?
    ) {
        if (reminderId == null || medicationId == null || notificationId == null) {
            Toast.makeText(context, "Invalid action parameters.", Toast.LENGTH_SHORT).show()
            Log.e("NotificationActionReceiver", "Missing parameters for taking medication.")
            return
        }

        // Launch a coroutine to perform Firestore operations
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()

                // Step 1: Fetch the Medication document
                val medicationSnapshot = db.collection("Medication").document(medicationId).get().await()
                if (!medicationSnapshot.exists()) {
                    throw FirebaseFirestoreException("Medication not found.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                val medication = medicationSnapshot.toObject(Medication::class.java)
                    ?: throw FirebaseFirestoreException("Failed to parse Medication.", FirebaseFirestoreException.Code.UNKNOWN)

                // Step 2: Extract dosage number
                val dosageNumber = extractDosageNumber(dosageStr)
                if (dosageNumber <= 0) {

                    throw FirebaseFirestoreException("Invalid dosage value.", FirebaseFirestoreException.Code.INVALID_ARGUMENT)
                }

                // Step 3: Check stock availability
                if (medication.stockLevel < dosageNumber) {
                    throw FirebaseFirestoreException("Insufficient medication stock.", FirebaseFirestoreException.Code.ABORTED)
                }

                // Step 4: Deduct dosage from stockLevel
                val updatedStockLevel = medication.stockLevel - dosageNumber
                db.collection("Medication").document(medicationId)
                    .update("stockLevel", updatedStockLevel)
                    .await()
                Log.d("NotificationActionReceiver", "Medication stock updated successfully.")

                // Step 5: Create MedicationLog
                val medicationLog = MedicationLog(
                    logId = UUID.randomUUID().toString(),
                    medicationId = medicationId,
                    medicationName = medication.medicationName,
                    dosage = dosageStr,
                    takenDate = Date(),
                    userId = medication.userId
                )
                db.collection("MedicationLog").document(medicationLog.logId).set(medicationLog).await()
                Log.d("NotificationActionReceiver", "MedicationLog created successfully.")

                // Step 6: Update Notification status to 'Taken'
                db.collection("Notification").document(notificationId)
                    .update("status", "Taken")
                    .await()
                Log.d("NotificationActionReceiver", "Notification status updated to 'Taken'.")

                // Provide user feedback
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Medication marked as taken.", Toast.LENGTH_SHORT).show()
                }

                // Step 7: Cancel the notification
                cancelNotification(context, notificationId)

            } catch (e: FirebaseFirestoreException) {
                Log.e("NotificationActionReceiver", "Firestore error: ", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NotificationActionReceiver", "Unexpected error: ", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "An unexpected error occurred.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Handles the "Dismiss Reminder" action.
     */
    private fun handleDismissReminder(
        context: Context,
        reminderId: String?,
        notificationId: String?
    ) {
        if (reminderId == null || notificationId == null) {
            Toast.makeText(context, "Invalid action parameters.", Toast.LENGTH_SHORT).show()
            Log.e("NotificationActionReceiver", "Missing parameters for dismissing reminder.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()

                // Update Notification status to 'Dismissed'
                db.collection("Notification").document(notificationId)
                    .update("status", "Dismissed")
                    .await()
                Log.d("NotificationActionReceiver", "Notification status updated to 'Dismissed'.")

                // Provide user feedback
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Reminder dismissed.", Toast.LENGTH_SHORT).show()
                }

                // Cancel the notification
                cancelNotification(context, notificationId)

            } catch (e: FirebaseFirestoreException) {
                Log.e("NotificationActionReceiver", "Firestore error: ", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NotificationActionReceiver", "Unexpected error: ", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "An unexpected error occurred.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleAcceptInvitation(
        context: Context,
        notificationId: String?,
        senderId: String?,
        patientId: String?
    ) {
        // Validate all required parameters
        if (notificationId == null || senderId == null || patientId == null) {
            Toast.makeText(context, "Invalid action parameters.", Toast.LENGTH_SHORT).show()
            Log.e("NotificationActionReceiver", "Missing parameters for accepting invitation.")
            return
        }

        // Launch a coroutine in the IO dispatcher for network operations
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val auth = FirebaseAuth.getInstance()
                val currentUserId = auth.currentUser?.uid

                // Ensure the user is authenticated
                if (currentUserId == null) {
                    throw FirebaseFirestoreException(
                        "User not authenticated.",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED
                    )
                }

                db.collection("Notification").document(notificationId)
                    .update("status", "Accepted")
                    .await() // Await the completion of the update operation
                Log.d("NotificationActionReceiver", "Invitation status updated to 'Accepted'.")

                db.collection("Caregiver").document(senderId)
                    .update("patientList", FieldValue.arrayUnion(patientId))
                    .await() // Await the completion of the update operation
                Log.d("NotificationActionReceiver", "Patient ID '$patientId' added to caregiver '$senderId' patientList.")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Invitation accepted.", Toast.LENGTH_SHORT).show()
                }

                // 4. Cancel the notification from the notification tray
                cancelNotification(context, notificationId)

            } catch (e: FirebaseFirestoreException) {
                // Handle Firestore-specific exceptions
                Log.e("NotificationActionReceiver", "Firestore error: ", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Handle any other unexpected exceptions
                Log.e("NotificationActionReceiver", "Unexpected error: ", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "An unexpected error occurred.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleDeclineInvitation(
        context: Context,
        notificationId: String?
    ) {
        if (notificationId == null) {
            Toast.makeText(context, "Invalid action parameters.", Toast.LENGTH_SHORT).show()
            Log.e("NotificationActionReceiver", "Missing parameters for declining invitation.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()

                // Update invitation status to 'declined'
                db.collection("Notification").document(notificationId)
                    .update("status", "Declined")
                    .await()
                Log.d("NotificationActionReceiver", "Invitation status updated to 'declined'.")

                // need to send notification back to the caregiver

                // Provide user feedback
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Invitation declined.", Toast.LENGTH_SHORT).show()
                }

                // Cancel the notification
                cancelNotification(context, notificationId)

            } catch (e: FirebaseFirestoreException) {
                Log.e("NotificationActionReceiver", "Firestore error: ", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NotificationActionReceiver", "Unexpected error: ", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "An unexpected error occurred.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Extracts the numerical dosage from the dosage string.
     */
    private fun extractDosageNumber(dosageStr: String): Int {
        val regex = Regex("(\\d+)")
        val matchResult = regex.find(dosageStr)
        return matchResult?.groups?.get(1)?.value?.toIntOrNull() ?: 0
    }

    /**
     * Cancels the notification with the given ID.
     */
    private fun cancelNotification(context: Context, notificationId: String?) {
        if (notificationId == null) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId.hashCode())
        Log.d("NotificationActionReceiver", "Notification canceled with ID: ${notificationId.hashCode()}")
    }
}
