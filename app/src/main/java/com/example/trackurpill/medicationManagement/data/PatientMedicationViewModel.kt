// PatientMedicationViewModel.kt
package com.example.trackurpill.medicationManagement.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.MEDICATION
import com.example.trackurpill.data.Medication
import com.example.trackurpill.notification.data.NotificationViewModel
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.tasks.await

class PatientMedicationViewModel(app: Application) : AndroidViewModel(app) {

    private val medicationLD = MutableLiveData<List<Medication>>(emptyList())
    private var listener: ListenerRegistration? = null

    // Filtering and Sorting
    private val resultLD = MutableLiveData<List<Medication>>()
    private var nameFilter = ""
    private var userIdFilter = ""
    private var sortField = ""
    private var sortReverse = false

    // Notification ViewModel
    private val notificationVM = NotificationViewModel(app)

    // LiveData for interactions and adverse events
    private val _interactionResults = MutableLiveData<List<String>>()
    val interactionResults: LiveData<List<String>> get() = _interactionResults

    private val _adverseEventResults = MutableLiveData<List<String>>()
    val adverseEventResults: LiveData<List<String>> get() = _adverseEventResults

    // LiveData for errors
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    init {
        // Initialize Firestore listener
        listener = MEDICATION.addSnapshotListener { snap, error ->
            if (error != null) {
                _error.value = error.message
                return@addSnapshotListener
            }
            medicationLD.value = snap?.toObjects()
            updateResult()
        }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }

    // Access LiveData
    fun getMedicationLD(): LiveData<List<Medication>> = medicationLD
    fun getResultLD(): LiveData<List<Medication>> = resultLD

    // Get all medications
    fun getAll(): List<Medication> = medicationLD.value ?: emptyList()

    // Get a medication by its ID
    fun get(id: String): Medication? = getAll().find { it.medicationId == id }

    // Get medications for a specific user
    fun getAllByUser(userId: String): List<Medication> = getAll().filter { it.userId == userId }

    // Get the latest medication entry
    fun getLatestMedication(): Medication? = medicationLD.value?.lastOrNull()

    // Add or update medication
    fun setMedication(medication: Medication) {
        MEDICATION.document(medication.medicationId).set(medication)
    }

    // Delete a medication
    fun deleteMedication(medicationId: String) {
        MEDICATION.document(medicationId).delete()
    }

    // Filter and Sort Operations
    fun search(name: String) {
        this.nameFilter = name
        updateResult()
    }

    fun filterByUser(userId: String) {
        this.userIdFilter = userId
        updateResult()
    }

    fun sort(field: String, reverse: Boolean) {
        this.sortField = field
        this.sortReverse = reverse
        updateResult()
    }

    private fun updateResult() {
        var list = getAll()

        // Filter by name and userId
        list = list.filter {
            it.medicationName.contains(nameFilter, ignoreCase = true) &&
                    (it.userId == userIdFilter || userIdFilter.isEmpty())
        }

        // Sort by field
        list = when (sortField) {
            "name" -> list.sortedBy { it.medicationName }
            else -> list
        }

        // Reverse the list if needed
        if (sortReverse) list = list.reversed()

        resultLD.value = list
    }

    fun getMedicationLiveData(medicationId: String): MutableLiveData<Medication?> {
        val specificMedicationLD = MutableLiveData<Medication?>()
        medicationLD.observeForever { medications ->
            val medication = medications.find { it.medicationId == medicationId }
            specificMedicationLD.value = medication
        }
        return specificMedicationLD
    }

}
