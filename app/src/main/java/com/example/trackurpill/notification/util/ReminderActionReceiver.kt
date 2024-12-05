package com.example.trackurpill.notification.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.example.trackurpill.data.Medication
import com.example.trackurpill.data.MedicationLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class ReminderActionReceiver : BroadcastReceiver() {

    private val firestore = FirebaseFirestore.getInstance()

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            "ACTION_TAKEN" -> {
                handleActionTaken(context, intent)
                clearNotification(context, intent.getStringExtra("medicationId"))
            }
            "ACTION_REMIND_AGAIN" -> {
                handleRemindAgain(context, intent)
                clearNotification(context, intent.getStringExtra("medicationId"))
            }
            "ACTION_DISMISS" -> {
                val notificationId = intent.getIntExtra("notificationId", -1)
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
                clearNotification(context, intent.getStringExtra("medicationId"))
            }
        }
    }

    private fun handleActionTaken(context: Context, intent: Intent) {
        val medicationId = intent.getStringExtra("medicationId")
        val medicationName = intent.getStringExtra("medicationName") ?: "Unknown"
        val dosageString = intent.getStringExtra("dosage")
        val dosage = dosageString?.let { extractNumericValue(it) }
        val userId = intent.getStringExtra("userId")

        if (medicationId != null && dosage != null) {
            // Update stock level
            firestore.collection("Medication")
                .document(medicationId)
                .get()
                .addOnSuccessListener { document ->
                    val medication = document.toObject(Medication::class.java)
                    medication?.let {
                        val updatedStock = it.stockLevel - dosage
                        if (updatedStock >= 0) {
                            firestore.collection("Medication")
                                .document(medicationId)
                                .update("stockLevel", updatedStock)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        context,
                                        "Stock updated! Remaining: $updatedStock",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        context,
                                        "Failed to update stock: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        } else {
                            Toast.makeText(context, "Insufficient stock! Remember to restock!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        context,
                        "Failed to fetch medication: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            // Record the log
            val log = MedicationLog(
                logId = UUID.randomUUID().toString(),
                medicationId = medicationId,
                medicationName = medicationName,
                dosage = dosage.toString(),
                takenDate = Date(),
                userId = userId.toString()
            )

            firestore.collection("MedicationLog").document(log.logId).set(log)
                .addOnSuccessListener {
                    Toast.makeText(context, "Medication log recorded.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to log medication: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "Invalid medication data.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleRemindAgain(context: Context, intent: Intent) {
        val medicationName = intent.getStringExtra("medicationName") ?: "Medication"
        val medicationId = intent.getStringExtra("medicationId") ?: ""
        val dosage = intent.getStringExtra("dosage") ?: "1"
        val userId = intent.getStringExtra("userId")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("medicationName", medicationName)
            putExtra("medicationId", medicationId)
            putExtra("dosage", dosage)
            putExtra("userId", userId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 10)
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Toast.makeText(context, "Reminder set for 10 minutes later.", Toast.LENGTH_SHORT).show()
    }

    private fun extractNumericValue(dosageString: String): Int? {
        val numericPart = Regex("\\d+").find(dosageString)?.value
        return numericPart?.toIntOrNull()
    }

    private fun clearNotification(context: Context, medicationId: String?) {
        if (medicationId != null) {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(medicationId.hashCode())
        }
    }
}
