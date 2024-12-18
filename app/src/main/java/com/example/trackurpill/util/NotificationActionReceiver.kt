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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

class NotificationActionReceiver : BroadcastReceiver() {

    // Initialize Firebase Functions
    private val functions: FirebaseFunctions by lazy { Firebase.functions }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        val notificationId = intent.getStringExtra("notificationId")
        val reminderId = intent.getStringExtra("reminderId")
        val type = intent.getStringExtra("type") // "reminder" or "invitation"

        Log.d("NotificationActionReceiver", "Received action: $action, type: $type")

        when (type) {
            "reminder" -> {
                when (action) {
                    "ACTION_TAKE_MEDICATION" -> {
                        val medicationId = intent.getStringExtra("medicationId")
                        val dosageStr = intent.getStringExtra("dosage") ?: "1 Tablet"
                        handleTakeMedication(context, reminderId, medicationId, dosageStr, notificationId)
                    }
                    "ACTION_DISMISS_REMINDER" -> {
                        handleDismissReminder(context, reminderId, notificationId)
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
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Medication not found.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val medication = medicationSnapshot.toObject(Medication::class.java)
                    ?: run {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to parse medication data.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                // Step 2: Extract dosage number
                val dosageNumber = extractDosageNumber(dosageStr)
                if (dosageNumber <= 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Invalid dosage value.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Step 3: Check stock availability
                if (medication.stockLevel < dosageNumber) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Insufficient medication stock.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Step 4: Deduct dosage from stockLevel
                val updatedStockLevel = medication.stockLevel - dosageNumber
                db.collection("Medication").document(medicationId)
                    .update("stockLevel", updatedStockLevel)
                    .await()
                Log.d("NotificationActionReceiver", "Medication stock updated successfully.")

                // Step 5: If stock level < 5, notify the user
                if (updatedStockLevel < 5) {
                    notifyUserLowStock(medication.userId, medication.medicationName, updatedStockLevel)
                }

                // Step 6: Create MedicationLog
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

                // Step 7: Update Notification status to 'Taken'
                db.collection("Notification").document(notificationId)
                    .update("status", "Taken")
                    .await()
                Log.d("NotificationActionReceiver", "Notification status updated to 'Taken'.")

                // Step 8: Cancel the notification
                cancelNotification(context, notificationId)

                // Provide user feedback on the Main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Medication marked as taken.", Toast.LENGTH_SHORT).show()
                }

            } catch (e: FirebaseFirestoreException) {
                Log.e("NotificationActionReceiver", "Firestore error: ", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NotificationActionReceiver", "Unexpected error: ", e)
                withContext(Dispatchers.Main) {
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

    private suspend fun notifyUserLowStock(userId: String, medicationName: String, currentStock: Int) {
        try {
            // Prepare data to send to the cloud function
            val data = hashMapOf(
                "userId" to userId,
                "medicationName" to medicationName,
                "currentStock" to currentStock
            )

            // Call the cloud function named "notifyLowMedicationStock"
            val result = functions
                .getHttpsCallable("notifyLowMedicationStock")
                .call(data)
                .await()

            // Optionally handle the result returned by the cloud function
            val success = (result.data as? Map<*, *>)?.get("success") as? Boolean ?: false

            if (success) {
                Log.d("NotificationActionReceiver", "Low stock notification sent successfully.")
            } else {
                Log.e("NotificationActionReceiver", "Failed to send low stock notification.")
            }
        } catch (e: Exception) {
            Log.e("NotificationActionReceiver", "Error calling notifyLowMedicationStock cloud function: ", e)
        }
    }
}
