package com.example.trackurpill.caregiverManagement.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.Patient
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObjects

class CaregiverMonitorViewModel(app: Application) : AndroidViewModel(app) {

    private val db = FirebaseFirestore.getInstance()
    private val patientListLD = MutableLiveData<List<Patient>>(emptyList())
    private var listener: ListenerRegistration? = null

    // LiveData Accessor
    fun getPatientListLD() = patientListLD

    // Initialize ViewModel with caregiver ID
    fun init(caregiverId: String) {
        listener?.remove() // Ensure no duplicate listeners
        listener = db.collection("Caregiver").document(caregiverId)
            .addSnapshotListener { snapshot, _ ->
                val patientIds = snapshot?.get("patientList") as? List<String> ?: emptyList()
                fetchPatients(patientIds)
            }
    }

    private fun fetchPatients(patientIds: List<String>) {
        if (patientIds.isEmpty()) {
            println("Patient exist")
            patientListLD.value = emptyList()
            return
        }

        db.collection("Patient")
            .whereIn("userId", patientIds)
            .get()
            .addOnSuccessListener { snapshot ->
                val patients = snapshot.toObjects<Patient>()
                patientListLD.value = patients
                // Log the fetched patients
                patients.forEach { patient ->
                    println("Fetched patient: ${patient.userName} (${patient.userId})")
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                patientListLD.value = emptyList()
            }
    }


    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
