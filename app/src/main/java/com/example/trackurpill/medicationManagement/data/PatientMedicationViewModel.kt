// PatientMedicationViewModel.kt
package com.example.trackurpill.medicationManagement.data

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.MEDICATION
import com.example.trackurpill.data.Medication
import com.example.trackurpill.notification.data.NotificationViewModel
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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


    fun fetchMedicationById(medicationId: String, callback: (Medication?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = MEDICATION.document(medicationId).get().await()
                if (snapshot.exists()) {
                    val medication = snapshot.toObject(Medication::class.java)
                    callback(medication)
                } else {
                    Log.w("PatientMedicationVM", "Medication with ID $medicationId not found.")
                    callback(null)
                }
            } catch (e: Exception) {
                Log.e("PatientMedicationVM", "Error fetching Medication: ", e)
                callback(null)
            }
        }
    }

    /**
     * Extracts the numerical dosage from a dosage string.
     * Example: "2 Tablet" -> 2
     */
    private fun extractDosageNumber(dosageStr: String): Int {
        val regex = Regex("(\\d+)")
        val matchResult = regex.find(dosageStr)
        return matchResult?.groups?.get(1)?.value?.toIntOrNull() ?: 0
    }

    /**
     * Marks medication as taken by deducting the dosage from stockLevel.
     */
    fun markMedicationAsTaken(medicationId: String, dosageStr: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Extract dosage number
                val dosage = extractDosageNumber(dosageStr)
                if (dosage <= 0) {
                    throw FirebaseFirestoreException("Invalid dosage value.", FirebaseFirestoreException.Code.INVALID_ARGUMENT)
                }

                // Fetch the Medication document
                val medicationSnapshot = MEDICATION.document(medicationId).get().await()
                if (!medicationSnapshot.exists()) {
                    throw FirebaseFirestoreException("Medication not found.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                val medication = medicationSnapshot.toObject(Medication::class.java)
                    ?: throw FirebaseFirestoreException("Failed to parse Medication.", FirebaseFirestoreException.Code.UNKNOWN)

                // Check stock availability
                if (medication.stockLevel < dosage) {
                    throw FirebaseFirestoreException("Insufficient medication stock.", FirebaseFirestoreException.Code.ABORTED)
                }

                // Deduct dosage from stockLevel
                val updatedMedication = medication.copy(stockLevel = medication.stockLevel - dosage)

                // Update the Medication document
                setMedication(updatedMedication)


            } catch (e: FirebaseFirestoreException) {
                Log.e("PatientMedicationVM", "Error marking medication as taken: ", e)
            } catch (e: Exception) {
                Log.e("PatientMedicationVM", "Unexpected error: ", e)
            }
        }
    }

}
