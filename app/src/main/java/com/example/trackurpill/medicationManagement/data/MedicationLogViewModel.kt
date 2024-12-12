package com.example.trackurpill.medicationManagement.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.MEDICATION_LOG
import com.example.trackurpill.data.MedicationLog
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObjects

class MedicationLogViewModel(app: Application) : AndroidViewModel(app) {
    private val medicationLogLD = MutableLiveData<List<MedicationLog>>(emptyList())
    private var listener: ListenerRegistration? = null

    // Filters and Sorting
    private val resultLD = MutableLiveData<List<MedicationLog>>()
    private var field = "date"
    private var reverse = true

    init {
        listener = MEDICATION_LOG.addSnapshotListener { snap, _ ->
            medicationLogLD.value = snap?.toObjects()
            updateResult()
        }
    }

    override fun onCleared() {
        listener?.remove()
    }

    fun init() = Unit

    // Access LiveData
    fun getMedicationLogLD() = medicationLogLD
    fun getResultLD() = resultLD

    // Get all logs
    fun getAllLogs() = medicationLogLD.value ?: emptyList()

    // Add a medication log entry
    fun setLog(medicationLog: MedicationLog) {
        MEDICATION_LOG.document(medicationLog.logId).set(medicationLog)
    }

    // Delete a medication log entry
    fun deleteLog(logId: String) {
        MEDICATION_LOG.document(logId).delete()
    }

    fun sort(field: String, reverse: Boolean) {
        this.field = field
        this.reverse = reverse
        updateResult()
    }

    private fun updateResult() {
        var list = getAllLogs()

        // Sort by field
        list = when (field) {
            "date" -> list.sortedBy { it.takenDate }
            "medicationName" -> list.sortedBy { it.medicationName }
            else -> list
        }

        // Reverse the list if needed
        if (reverse) list = list.reversed()

        resultLD.value = list
    }
}
