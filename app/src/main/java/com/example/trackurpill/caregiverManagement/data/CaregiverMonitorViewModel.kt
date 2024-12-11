package com.example.trackurpill.caregiverManagement.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.trackurpill.data.CAREGIVER
import com.example.trackurpill.data.PATIENT
import com.example.trackurpill.data.Patient
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObjects
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CaregiverMonitorViewModel(app: Application) : AndroidViewModel(app) {

    private val db = FirebaseFirestore.getInstance()
    private val patientLD = MutableLiveData<List<Patient>>(emptyList())
    private val resultLD = MutableLiveData<List<Patient>>()
    private var listener: ListenerRegistration? = null
    private val functions: FirebaseFunctions = Firebase.functions

    // Filters and Sorting
    private var name = ""
    private var caregiverId = ""
    private var field = ""
    private var reverse = false

    init {
        listener = PATIENT.addSnapshotListener { snap, _ ->
            patientLD.value = snap?.toObjects()
            updateResult()
        }
    }

    override fun onCleared() {
        listener?.remove()
    }

    fun observePatients(caregiverId: String): MutableLiveData<List<Patient>> {
        val filteredPatientsLD = MutableLiveData<List<Patient>>()

        // Listen to real-time updates from the Patient collection
        PATIENT.addSnapshotListener { snap, exception ->
            if (exception != null) {
                println("Error observing patient data: ${exception.message}")
                filteredPatientsLD.value = emptyList()
                return@addSnapshotListener
            }

            val patients = snap?.toObjects<Patient>() ?: emptyList()

            // Observe caregiver's patient list
            val caregiverDocRef = db.collection("Caregiver").document(caregiverId)
            caregiverDocRef.addSnapshotListener { caregiverSnap, caregiverException ->
                if (caregiverException != null) {
                    println("Error observing caregiver data: ${caregiverException.message}")
                    filteredPatientsLD.value = emptyList()
                    return@addSnapshotListener
                }

                if (caregiverSnap != null && caregiverSnap.exists()) {
                    val patientIds = caregiverSnap.get("patientList") as? List<String> ?: emptyList()
                    filteredPatientsLD.value = patients.filter { patientIds.contains(it.userId) }
                    println("Updated filteredPatientsLD: ${filteredPatientsLD.value}")
                } else {
                    println("Caregiver document does not exist.")
                    filteredPatientsLD.value = emptyList()
                }
            }
        }

        return filteredPatientsLD
    }


    // Get all patients
    fun getAll() = patientLD.value ?: emptyList()

    // Get a specific patient by ID
    fun get(id: String) = getAll().find { it.userId == id }

    // Get all patients assigned to a caregiver
    private fun getAllByCaregiver(): List<Patient> {
        return getAll().filter { it.userId == caregiverId }
    }

    // Search patients by name
    fun search(name: String) {
        this.name = name
        updateResult()
    }

    // Sort patients by a specific field
    fun sort(field: String, reverse: Boolean) {
        this.field = field
        this.reverse = reverse
        updateResult()
    }

    // Fetch patient details
    fun fetchPatientDetails(patientId: String): MutableLiveData<Patient?> {
        val specificPatientLD = MutableLiveData<Patient?>()
        patientLD.observeForever { patient ->
            val patient = patient.find { it.userId == patientId }
            specificPatientLD.value = patient

        }
        return specificPatientLD
    }


    // Update filtered and sorted results
    private fun updateResult() {
        var list = getAllByCaregiver()

        // Filter by name
        list = list.filter {
            it.userName.contains(name, true)
        }

        // Sort by field
        list = when (field) {
            "name" -> list.sortedBy { it.userName }
            "age" -> list.sortedBy { it.userAge }
            else -> list
        }

        // Reverse the list if needed
        if (reverse) list = list.reversed()

        resultLD.value = list
    }


    fun sendPatientInvitation(
        email: String,
        caregiverId: String,
        callback: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmedEmail = email.trim()
                Log.d("CaregiverMonitorViewModel", "Searching for email: $trimmedEmail")

                val caregiverDoc = CAREGIVER.document(caregiverId).get().await()
                if (!caregiverDoc.exists()) {
                    Log.e("CaregiverMonitorViewModel", "Caregiver with ID $caregiverId does not exist.")
                    withContext(Dispatchers.Main) {
                        callback(false, "Caregiver not found.")
                    }
                    return@launch
                }

                val currentPatientIds = caregiverDoc.get("patientList") as? List<String> ?: emptyList()

                val patientQuery = PATIENT.whereEqualTo("userEmail", trimmedEmail).get().await()
                if (patientQuery.isEmpty) {
                    Log.e("CaregiverMonitorViewModel", "Patient with email $trimmedEmail not found.")
                    withContext(Dispatchers.Main) {
                        callback(false, "Email not found.")
                    }
                    return@launch
                }

                val patientDoc = patientQuery.documents[0]
                val patientId = patientDoc.id

                if (currentPatientIds.contains(patientId)) {
                    Log.e("CaregiverMonitorViewModel", "Patient ID $patientId is already in caregiver's patient list.")
                    withContext(Dispatchers.Main) {
                        callback(false, "User is already in your patient list.")
                    }
                    return@launch
                }

                Log.d("CaregiverMonitorViewModel", "Patient email found: ${patientDoc.getString("userEmail")} (ID: $patientId)")

                // 4. Call Cloud Function to Send Invitation
                val data = hashMapOf(
                    "patientEmail" to trimmedEmail,
                    "caregiverId" to caregiverId
                )

                val functionResult = functions
                    .getHttpsCallable("sendPatientInvitation")
                    .call(data)
                    .await()

                val resultData = functionResult.data as? Map<*, *> ?: emptyMap<Any, Any>()

                val success = resultData["success"] as? Boolean ?: false
                val message = resultData["message"] as? String ?: "Unknown response."

                if (success) {
                    Log.d("CaregiverMonitorViewModel", "Invitation sent successfully: $message")
                    withContext(Dispatchers.Main) {
                        callback(true, "Invitation sent successfully.")
                    }
                } else {
                    Log.e("CaregiverMonitorViewModel", "Failed to send invitation: $message")
                    withContext(Dispatchers.Main) {
                        callback(false, "Failed to send invitation: $message")
                    }
                }

            } catch (e: FirebaseFunctionsException) {
                Log.e("CaregiverMonitorViewModel", "FirebaseFunctionsException: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false, "Function error: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("CaregiverMonitorViewModel", "Exception: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false, "An unexpected error occurred: ${e.message}")
                }
            }
        }
    }

}
