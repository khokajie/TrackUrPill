package com.example.trackurpill.medicationManagement.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.REMINDER
import com.example.trackurpill.data.Reminder
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObjects

class ReminderViewModel(app: Application) : AndroidViewModel(app) {
    private val reminderLD = MutableLiveData<List<Reminder>>(emptyList())
    private var listener: ListenerRegistration? = null

    // Filters and Sorting
    private val resultLD = MutableLiveData<List<Reminder>>()
    private var medicationId = ""
    private var field = ""
    private var reverse = false

    init {
        listener = REMINDER.addSnapshotListener { snap, _ ->
            reminderLD.value = snap?.toObjects()
            updateResult()
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
    fun getAll() = reminderLD.value ?: emptyList()

    // Get a reminder by its ID
    fun get(id: String) = getAll().find { it.reminderId == id }

    // Get reminders for a specific medication
    fun getAllByMedication(medicationId: String) = getAll().filter { it.medicationId == medicationId }

    // Add or update reminder
    fun setReminder(reminder: Reminder) {
        REMINDER.document(reminder.reminderId).set(reminder)
    }

    // Delete a reminder
    fun deleteReminder(reminderId: String) {
        REMINDER.document(reminderId).delete()
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
        list = when (field) {
            "reminderTime" -> list.sortedBy { it.reminderTime }
            "frequency"    -> list.sortedBy { it.frequency }
            else           -> list
        }

        // Reverse the list if needed
        if (reverse) list = list.reversed()

        resultLD.value = list
    }
}
