package com.example.trackurpill.medicationManagement.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.trackurpill.data.REMINDER
import com.example.trackurpill.data.Reminder
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObjects
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

    private val functions = Firebase.functions

    init {
        setupRemindersListener()
    }

    private fun setupRemindersListener() {
        listener = REMINDER.addSnapshotListener { snapshot, _ ->
            snapshot?.toObjects<Reminder>()?.let { reminders ->
                reminderLD.value = reminders
                updateResult()
            }
        }
    }

    fun setReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                val userTimeZone = "Asia/Hong_Kong"
                Log.d("ReminderViewModel", "Setting reminder with timezone: $userTimeZone")
                scheduleReminder(reminder, userTimeZone)
            } catch (e: Exception) {
                Log.e("ReminderViewModel", "Error setting reminder", e)
            }
        }
    }

    private suspend fun scheduleReminder(reminder: Reminder, userTimeZone: String) {
        try {
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone(userTimeZone)
            }

            val formattedDate = reminder.date?.let { dateFormatter.format(it) }

            val reminderMap = mutableMapOf(
                "reminderId" to reminder.reminderId,
                "hour" to reminder.hour,
                "minute" to reminder.minute,
                "frequency" to reminder.frequency,
                "medicationId" to reminder.medicationId
            )

            // Add optional fields only if they are not null
            formattedDate?.let { reminderMap["date"] = it }
            reminder.day?.let { reminderMap["day"] = it }

            val data = mapOf(
                "reminder" to reminderMap,
                "userTimeZone" to userTimeZone
            )

            Log.d("ReminderViewModel", "Scheduling reminder with data: $data")

            val result = functions
                .getHttpsCallable("scheduleReminder")
                .call(data)
                .await()

            val response = result.data as? Map<*, *>
            if (response?.get("success") == true) {
                Log.d("ReminderViewModel", "Reminder scheduled successfully.")
            } else {
                Log.e("ReminderViewModel", "Failed to schedule reminder: $response")
            }
        } catch (e: Exception) {
            Log.e("ReminderViewModel", "Error scheduling reminder", e)
            throw e
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

        // Sort by field (uncomment and adjust as needed)
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
