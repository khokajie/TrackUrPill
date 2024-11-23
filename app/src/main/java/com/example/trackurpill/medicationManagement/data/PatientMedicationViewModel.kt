package com.example.trackurpill.medicationManagement.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.MEDICATION
import com.example.trackurpill.data.Medication
import com.example.trackurpill.data.REMINDER
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObjects

class PatientMedicationViewModel(app: Application) : AndroidViewModel(app) {
    private val medicationLD = MutableLiveData<List<Medication>>(emptyList())
    private var listener: ListenerRegistration? = null

    // Filters and Sorting
    private val resultLD = MutableLiveData<List<Medication>>()
    private var name = ""
    private var userId = ""
    private var field = ""
    private var reverse = false

    init {
        listener = MEDICATION.addSnapshotListener { snap, _ ->
            medicationLD.value = snap?.toObjects()
            updateResult()
        }
    }

    override fun onCleared() {
        listener?.remove()
    }

    fun init() = Unit

    // Access LiveData
    fun getMedicationLD() = medicationLD
    fun getResultLD() = resultLD

    // Get all medications
    fun getAll() = medicationLD.value ?: emptyList()

    // Get a medication by its ID
    fun get(id: String) = getAll().find { it.medicationId == id }

    // Get medications for a specific user
    fun getAllByUser(userId: String) = getAll().filter { it.userId == userId }

    // Get the latest medication entry
    fun getLatestMedication(): Medication? {
        return medicationLD.value?.lastOrNull()
    }

    // Add or update medication
    fun setMedication(medication: Medication) {
        MEDICATION.document(medication.medicationId).set(medication)
    }

    // Delete a reminder
    fun deleteMedication(medicationId: String) {
        MEDICATION.document(medicationId).delete()
    }

    // Filter and Sort Operations
    fun search(name: String) {
        this.name = name
        updateResult()
    }

    fun filterByUser(userId: String) {
        this.userId = userId
        updateResult()
    }


    fun sort(field: String, reverse: Boolean) {
        this.field = field
        this.reverse = reverse
        updateResult()
    }

    private fun updateResult() {
        var list = getAll()

        // Filter by name and userId
        list = list.filter {
            it.medicationName.contains(name, true) &&
                    (it.userId == userId || userId.isEmpty())
        }

        // Sort by field
        list = when (field) {
            "expirationDate" -> list.sortedBy { it.expirationDate }
            "name"           -> list.sortedBy { it.medicationName }
            else             -> list
        }

        // Reverse the list if needed
        if (reverse) list = list.reversed()

        resultLD.value = list
    }
}
