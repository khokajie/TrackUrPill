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
        listener = REMINDER.addSnapshotListener { snapshot: QuerySnapshot?, error ->
            error?.let {
                Log.e("ReminderViewModel", "Error getting reminders", it)
                return@addSnapshotListener
            }

            try {
                snapshot?.toObjects<Reminder>()?.let { reminders ->
                    reminderLD.value = reminders
                    updateResult()
                }
            } catch (e: Exception) {
                Log.e("ReminderViewModel", "Error converting reminders", e)
            }
        }
    }

    fun setReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                val userTimeZone = TimeZone.getDefault().id
                val reminderWithTimeZone = reminder.copy(userTimeZone = userTimeZone)

                // Create map for Firestore
                val reminderMap = mapOf(
                    "reminderId" to reminderWithTimeZone.reminderId,
                    "medicationId" to reminderWithTimeZone.medicationId,
                    "date" to reminderWithTimeZone.date,  // Firestore handles Date objects natively
                    "hour" to reminderWithTimeZone.hour,
                    "minute" to reminderWithTimeZone.minute,
                    "frequency" to reminderWithTimeZone.frequency,
                    "day" to (reminderWithTimeZone.day ?: ""),
                    "userTimeZone" to userTimeZone
                )

                // Save to Firestore
                REMINDER.document(reminderWithTimeZone.reminderId)
                    .set(reminderMap)
                    .await()

                // Schedule with cloud function
                scheduleReminder(reminderWithTimeZone, userTimeZone)
            } catch (e: Exception) {
                Log.e("ReminderViewModel", "Error setting reminder", e)
            }
        }
    }

    private suspend fun scheduleReminder(reminder: Reminder, userTimeZone: String) {
        try {
            // Convert Date to string for cloud function
            val dateFormatter = SimpleDateFormat("MM/dd/yyyy", Locale.US).apply {
                timeZone = TimeZone.getTimeZone(userTimeZone)
            }

            val formattedDate = reminder.date?.let { dateFormatter.format(it) }

            val data = mapOf(
                "reminder" to mapOf(
                    "reminderId" to reminder.reminderId,
                    "date" to formattedDate,
                    "hour" to reminder.hour,
                    "minute" to reminder.minute,
                    "frequency" to reminder.frequency,
                    "day" to (reminder.day ?: ""),
                    "medicationId" to reminder.medicationId,
                ),
                "userTimeZone" to userTimeZone
            )

            val result = functions.getHttpsCallable("scheduleReminder")
                .call(data)
                .await()

            handleScheduleResponse(result.data as? Map<*, *>)
        } catch (e: Exception) {
            Log.e("ReminderViewModel", "Failed to schedule reminder", e)
            throw e
        }
    }

    private fun handleScheduleResponse(response: Map<*, *>?) {
        response?.let {
            when {
                it["success"] == true && it["immediate"] == true ->
                    Log.d("ReminderViewModel", "Reminder sent immediately")
                it["success"] == true && it["scheduled"] == true ->
                    Log.d("ReminderViewModel", "Reminder scheduled successfully")
                else ->
                    Log.e("ReminderViewModel", "Failed to schedule: ${it["message"]}")
            }
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
