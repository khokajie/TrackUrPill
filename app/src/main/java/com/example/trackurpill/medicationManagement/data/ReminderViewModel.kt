package com.example.trackurpill.medicationManagement.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.trackurpill.data.REMINDER
import com.example.trackurpill.data.Reminder
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ReminderViewModel(app: Application) : AndroidViewModel(app) {
    private val reminderLD = MutableLiveData<List<Reminder>>(emptyList())
    private var listener: ListenerRegistration? = null

    // Filters and Sorting
    private val resultLD = MutableLiveData<List<Reminder>>()
    private var medicationId = ""
    private var field = ""
    private var reverse = false

    private val firestore = Firebase.firestore
    private val functions = Firebase.functions

    init {
        setupRemindersListener()
    }

    private fun setupRemindersListener() {
        listener = REMINDER.addSnapshotListener { snapshot: QuerySnapshot?, _ ->
            snapshot?.toObjects<Reminder>()?.let { reminders ->
                reminderLD.value = reminders
                updateResult()
            }
        }
    }

    fun setReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                // Obtain the user's actual time zone dynamically
                val userTimeZone = TimeZone.getDefault().id // e.g., "America/New_York"

                // Create a new Reminder object with userTimeZone
                val reminderWithTimeZone = reminder.copy(userTimeZone = userTimeZone)

                // Save reminder to Firestore
                REMINDER.document(reminderWithTimeZone.reminderId).set(reminderWithTimeZone)

                // Schedule the reminder
                scheduleReminder(reminderWithTimeZone, userTimeZone)
            } catch (e: Exception) {
                Log.e("ReminderViewModel", "Error setting reminder", e)
            }
        }
    }

    private suspend fun scheduleReminder(reminder: Reminder, userTimeZone: String) {
        try {
            // Format date as MM/DD/YYYY to match cloud function expectations
            val dateFormatter = SimpleDateFormat("MM/dd/yyyy", Locale.US)
            dateFormatter.timeZone = TimeZone.getTimeZone(userTimeZone)

            // Add debug logging
            Log.d("ReminderScheduler", "Scheduling reminder with date: ${reminder.date}")

            val formattedDate = dateFormatter.format(reminder.date)

            Log.d("ReminderScheduler", "Formatted date: $formattedDate")
            Log.d("ReminderScheduler", "Hour: ${reminder.hour}, Minute: ${reminder.minute}")
            Log.d("ReminderScheduler", "Timezone: $userTimeZone")

            val data = mapOf(
                "reminder" to mapOf(
                    "reminderId" to reminder.reminderId,
                    "date" to formattedDate,         // Now in MM/dd/yyyy format
                    "hour" to reminder.hour,
                    "minute" to reminder.minute,
                    "frequency" to reminder.frequency,
                    "day" to (reminder.day ?: ""),
                    "medicationId" to reminder.medicationId,
                ),
                "userTimeZone" to userTimeZone       // Move timezone out of reminder object
            )

            // Log the complete data being sent
            Log.d("ReminderScheduler", "Sending data to Cloud Function: $data")

            val result = functions.getHttpsCallable("scheduleReminder")
                .call(data)
                .await()

            val response = result.data as? Map<*, *> ?: emptyMap<String, Any>()
            if (response["success"] == true) {
                when {
                    response["immediate"] == true ->
                        Log.d("ReminderScheduler", "Reminder sent immediately.")
                    response["scheduled"] == true ->
                        Log.d("ReminderScheduler", "Reminder scheduled successfully.")
                }
            } else {
                Log.e("ReminderScheduler", "Failed to schedule reminder: ${response["message"]}")
            }

        } catch (e: Exception) {
            Log.e("ReminderScheduler", "Failed to schedule reminder", e)
            throw e  // Rethrow to handle in the calling function
        }
    }


    override fun onCleared() {
        listener?.remove()
    }

    fun init() = Unit

    // Access LiveData
    fun getReminderLD() = reminderLD
    fun getResultLD() = resultLD

    // Get all reminders
    fun getAll() = reminderLD.value.orEmpty()

    // Get a reminder by its ID
    fun get(id: String) = getAll().find { it.reminderId == id }

    // Get reminders for a specific medication
    fun getAllByMedication(medicationId: String) = getAll().filter { it.medicationId == medicationId }

    // Delete a reminder
    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                REMINDER.document(reminderId).delete()
            } catch (e: Exception) {
                Log.e("ReminderViewModel", "Error deleting reminder", e)
            }
        }
    }

    // Filter and Sort Operations
    fun filterByMedication(medicationId: String) {
        this.medicationId = medicationId
        updateResult()
    }

    fun sort(field: String, reverse: Boolean) {
        this.field = field
        this.reverse = reverse
        updateResult()
    }

    private fun updateResult() {
        var list = getAll()

        // Filter by medicationId
        list = list.filter { it.medicationId == medicationId || medicationId.isEmpty() }

        // Sort by field
        /*list = when (field) {
            "reminderTime" -> list.sortedBy { it.reminderTime }
            "frequency"    -> list.sortedBy { it.frequency }
            else           -> list
        }*/

        // Reverse the list if needed
        if (reverse) list = list.reversed()

        resultLD.value = list
    }
}
