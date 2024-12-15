package com.example.trackurpill.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val firebaseAuth = FirebaseAuth.getInstance()

    // LiveData for user roles and user objects
    private val patientLD = MutableLiveData<Patient?>()
    private val caregiverLD = MutableLiveData<Caregiver?>()

    private val allPatientsLD = MutableLiveData<List<Patient>>()
    private val allCaregiversLD = MutableLiveData<List<Caregiver>>()

    private var patientListener: ListenerRegistration? = null
    private var caregiverListener: ListenerRegistration? = null

    init {
        // Listen for updates to Firestore collections
        patientListener = PATIENT.addSnapshotListener { snapshot, _ ->
            allPatientsLD.value = snapshot?.toObjects()
        }

        caregiverListener = CAREGIVER.addSnapshotListener { snapshot, _ ->
            allCaregiversLD.value = snapshot?.toObjects()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Remove Firestore listeners to avoid memory leaks
        patientListener?.remove()
        caregiverListener?.remove()
    }

    // ---------------------------------------------------------------------------------------------
    // Login Operation
    suspend fun login(email: String, password: String): Pair<String, String?> {
        return try {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()

            // Check Firestore for the user's role
            val patientQuery = PATIENT.whereEqualTo("userEmail", email).get().await()
            if (!patientQuery.isEmpty) {
                val patient = patientQuery.documents.first().toObject<Patient>()
                return "Patient" to patient?.userId
            }

            val caregiverQuery = CAREGIVER.whereEqualTo("userEmail", email).get().await()
            if (!caregiverQuery.isEmpty) {
                val caregiver = caregiverQuery.documents.first().toObject<Caregiver>()
                return "Caregiver" to caregiver?.userId
            }

            "NA" to null
        } catch (e: Exception) {
            println("Login failed: ${e.message}")
            "NA" to null
        }
    }

    fun logout() {
        firebaseAuth.signOut()
        patientLD.value = null
        caregiverLD.value = null
    }

    // ---------------------------------------------------------------------------------------------
    // Registration Operation
    suspend fun registerUser(role: String, userDetails: User, password: String): Boolean {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(userDetails.userEmail, password).await()
            val firebaseUserId = authResult.user?.uid ?: throw Exception("Registration failed to retrieve user ID")

            // Generate a new custom user ID
            val newUserId = generateNewUserId(role)

            // Update the userDetails object with the custom ID
            userDetails.userId = newUserId

            // Save user details in Firestore
            val collection = if (role == "Patient") PATIENT else CAREGIVER
            collection.document(firebaseUserId).set(userDetails).await()
            return true
        } catch (e: Exception) {
            println("Registration failed: ${e.message}")
            false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Utility Functions
    suspend fun generateNewUserId(role: String): String {
        val collection = if (role == "Patient") PATIENT else CAREGIVER
        return try {
            val allUsers = collection.get().await().toObjects<User>()
            val lastId = allUsers
                .mapNotNull { it.userId.removePrefix(role).toIntOrNull() }
                .maxOrNull() ?: 0
            val newId = lastId + 1
            "${role}${newId.toString().padStart(3, '0')}" // Format: Patient001 or Caregiver001
        } catch (e: Exception) {
            "${role}001" // Default ID
        }
    }


    suspend fun fetchUserDetails(userType: String, userId: String): User? {
        return try {
            val collection = if (userType == "Patient") PATIENT else CAREGIVER
            val documentSnapshot = collection.document(userId).get().await()
            documentSnapshot.toObject<User>()
        } catch (e: Exception) {
            println("Error fetching user details: ${e.message}")
            null
        }
    }

    fun getCurrentUser(): FirebaseUser? {
        return FirebaseAuth.getInstance().currentUser
    }

}
