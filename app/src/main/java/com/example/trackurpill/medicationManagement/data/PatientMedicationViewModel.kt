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
import com.example.trackurpill.data.MedicationInteraction
import com.example.trackurpill.notification.data.NotificationViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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

    private fun extractDosageNumber(dosageStr: String): Int {
        val regex = Regex("(\\d+)")
        val matchResult = regex.find(dosageStr)
        return matchResult?.groups?.get(1)?.value?.toIntOrNull() ?: 0
    }

    suspend fun markMedicationAsTaken(medicationId: String, userId: String, dosageStr: String): Boolean {
        return withContext(Dispatchers.IO) {
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
                    // Stock is insufficient, call cloud function to notify user
                    notifyLowStock(userId, medication.medicationName, medication.stockLevel)
                    throw FirebaseFirestoreException("Insufficient medication stock.", FirebaseFirestoreException.Code.ABORTED)
                }

                // Deduct dosage from stockLevel
                val updatedStockLevel = medication.stockLevel - dosage
                MEDICATION.document(medicationId)
                    .update("stockLevel", updatedStockLevel)
                    .await()
                Log.d("NotificationActionReceiver", "Medication stock updated successfully.")

                if (updatedStockLevel < 5) {
                    // Stock is insufficient, call cloud function to notify user
                    notifyLowStock(userId, medication.medicationName, updatedStockLevel)
                }

                true // Indicate success
            } catch (e: FirebaseFirestoreException) {
                Log.e("PatientMedicationVM", "Error marking medication as taken: ", e)
                false // Indicate failure
            } catch (e: Exception) {
                Log.e("PatientMedicationVM", "Unexpected error: ", e)
                false // Indicate failure
            }
        }
    }

    private suspend fun notifyLowStock(userId: String, medicationName: String, currentStock: Int) {
        try {
            // Initialize Firebase Functions
            val functions = Firebase.functions

            // Prepare data to send to the cloud function
            val data = hashMapOf(
                "userId" to userId,
                "medicationName" to medicationName,
                "currentStock" to currentStock
            )

            // Call the cloud function named "notifyLowMedicationStock"
            val result = functions
                .getHttpsCallable("notifyLowMedicationStock")
                .call(data)
                .await()

            // Optionally handle the result returned by the cloud function
            val success = result.data as? Boolean ?: false
            if (success) {
                Log.d("PatientMedicationVM", "Low stock notification sent successfully.")
            } else {
                Log.e("PatientMedicationVM", "Failed to send low stock notification.")
            }
        } catch (e: Exception) {
            Log.e("PatientMedicationVM", "Error calling notifyLowMedicationStock cloud function: ", e)
            // Handle exceptions related to the cloud function call
        }
    }

    // Function to retrieve interactions for a medication
    fun getInteractions(medicationId: String): LiveData<List<MedicationInteraction>> {
        val interactionsLD = MutableLiveData<List<MedicationInteraction>>()
        val medication = get(medicationId)
        interactionsLD.value = medication?.interactions ?: emptyList()
        return interactionsLD
    }

}
