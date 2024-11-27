package com.example.trackurpill.caregiverManagement.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.Patient
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects

class CaregiverMonitorViewModel(app: Application) : AndroidViewModel(app) {

    private val db = FirebaseFirestore.getInstance()
    private val patientListLD = MutableLiveData<List<Patient>>(emptyList())
    private val patientDetailsLD = MutableLiveData<Patient?>()
    private var listener: ListenerRegistration? = null

    // LiveData Accessor
    fun getPatientListLD() = patientListLD

    // LiveData Accessor
    fun getPatientDetailsLD() = patientDetailsLD

    // Initialize ViewModel with caregiver ID
    fun init(caregiverId: String) {
        listener?.remove() // Ensure no duplicate listeners
        listener = db.collection("Caregiver").document(caregiverId)
            .addSnapshotListener { snapshot, _ ->
                val patientIds = snapshot?.get("patientList") as? List<String> ?: emptyList()
                println("Patient: $patientIds")
                fetchPatients(patientIds)
            }
    }

    private fun fetchPatients(patientIds: List<String>) {
        println("fetchPatients called with IDs: $patientIds")

        if (patientIds.isEmpty()) {
            println("No patient IDs to fetch. Setting empty patient list.")
            patientListLD.value = emptyList()
            return
        }

        db.collection("Patient")
            .whereIn(FieldPath.documentId(), patientIds)
            .get()
            .addOnSuccessListener { snapshot ->
                val patients = snapshot.toObjects<Patient>()
                patientListLD.value = patients
            }
            .addOnFailureListener { e ->
                println("Error fetching patients: ${e.message}")
                e.printStackTrace()
                patientListLD.value = emptyList()
            }
    }

    // Fetch patient details with patient ID
    fun fetchPatientDetails(patientId: String) {
        listener?.remove() // Ensure no duplicate listeners
        listener = db.collection("Patient").document(patientId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    println("Error fetching patient details: ${e.message}")
                    e.printStackTrace()
                    patientDetailsLD.value = null
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val patient = snapshot.toObject<Patient>()
                    patientDetailsLD.value = patient
                } else {
                    println("Patient document does not exist")
                    patientDetailsLD.value = null
                }
            }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
