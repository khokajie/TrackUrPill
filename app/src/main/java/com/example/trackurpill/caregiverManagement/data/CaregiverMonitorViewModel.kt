package com.example.trackurpill.caregiverManagement.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.CAREGIVER
import com.example.trackurpill.data.Medication
import com.example.trackurpill.data.NOTIFICATION
import com.example.trackurpill.data.Notification
import com.example.trackurpill.data.PATIENT
import com.example.trackurpill.data.Patient
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import java.util.Date
import java.util.UUID

class CaregiverMonitorViewModel(app: Application) : AndroidViewModel(app) {

    private val db = FirebaseFirestore.getInstance()
    private val patientLD = MutableLiveData<List<Patient>>(emptyList())
    private val resultLD = MutableLiveData<List<Patient>>()
    private var listener: ListenerRegistration? = null

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
        callback: (String) -> Unit
    ) {
        val trimmedEmail = email.trim()
        println("Searching for email: $trimmedEmail")

        // Fetch caregiver's details
        CAREGIVER.document(caregiverId)
            .get()
            .addOnSuccessListener { caregiverDoc ->
                if (!caregiverDoc.exists()) {
                    callback("Caregiver not found")
                    return@addOnSuccessListener
                }

                val caregiverName = caregiverDoc.getString("userName") ?: "Unknown Caregiver"
                val currentPatientIds = caregiverDoc.get("patientList") as? List<String> ?: emptyList()

                // Search for the patient by email
                PATIENT.whereEqualTo("userEmail", trimmedEmail)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (documents.isEmpty) {
                            println("User email not found")
                            callback("Email not found")
                        } else {
                            val patientDocument = documents.documents[0]
                            val patientId = patientDocument.id

                            // Check if the patient is already in the caregiver's patientList
                            if (currentPatientIds.contains(patientId)) {
                                println("User is already in the patient list")
                                callback("User is already in your patient list")
                            } else {
                                println("User email found: ${patientDocument.getString("userEmail")}")

                                // Create a notification for the patient
                                createNotificationForPatient(
                                    patientId,
                                    "You have been invited by $caregiverName to connect as a caregiver.",
                                    caregiverId
                                ) { notificationStatus ->
                                    callback(notificationStatus)
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        println("Error occurred while querying: ${e.message}")
                        callback("Failed to query email: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                println("Error fetching caregiver details: ${e.message}")
                callback("Failed to fetch caregiver details: ${e.message}")
            }
    }

    private fun createNotificationForPatient(
        userId: String,
        message: String,
        caregiverId: String,
        callback: (String) -> Unit
    ) {
        val notification = Notification(
            notificationId = UUID.randomUUID().toString(),
            message = message,
            receiveTime = Date(),
            type = "invitation",
            userId = userId,
            status = "Pending",
            senderId = caregiverId // Include caregiverId for tracking
        )

        NOTIFICATION.document(notification.notificationId)
            .set(notification)
            .addOnSuccessListener {
                callback("Invitation sent successfully")
            }
            .addOnFailureListener { e ->
                callback("Failed to send invitation: ${e.message}")
            }
    }
}
