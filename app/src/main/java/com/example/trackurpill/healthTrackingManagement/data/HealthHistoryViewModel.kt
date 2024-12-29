package com.example.trackurpill.healthTrackingManagement.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.HEALTH_RECORD
import com.example.trackurpill.data.HealthRecord
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObjects

class HealthHistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val healthRecordsLD = MutableLiveData<List<HealthRecord>>(emptyList())
    private var listener: ListenerRegistration? = null

    // Filters and Sorting
    private val resultLD = MutableLiveData<List<HealthRecord>>()
    private var field = "recordDateTime"
    private var reverse = true

    init {
        listener = HEALTH_RECORD.addSnapshotListener { snap, _ ->
            healthRecordsLD.value = snap?.toObjects()
            updateResult()
        }
    }

    override fun onCleared() {
        listener?.remove()
    }

    fun init() = Unit

    // Access LiveData
    fun getHealthRecordsLD() = healthRecordsLD
    fun getResultLD() = resultLD

    // Get all health records
    fun getAll() = healthRecordsLD.value ?: emptyList()

    // Get a health record by its ID
    fun get(id: String) = getAll().find { it.recordId == id }

    // Add or update a health record
    fun setHealthRecord(healthRecord: HealthRecord) {
        HEALTH_RECORD.document(healthRecord.recordId).set(healthRecord)
    }


    private fun updateResult() {
        var list = getAll()

        // Sort by field
        list = when (field) {
            "recordDateTime" -> list.sortedBy { it.recordDateTime.toString() }
            "bmi"            -> list.sortedBy { it.bmi }
            else             -> list
        }

        // Reverse the list if needed
        if (reverse) list = list.reversed()

        resultLD.value = list
    }
}
