package com.example.trackurpill.userManagement.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class TokenViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Updates the FCM token for the current user based on their role.
     * Removes the token from any other user documents to ensure uniqueness.
     */
    fun updateFCMToken(userId: String, role: String) {
        viewModelScope.launch {
            try {
                // Retrieve FCM token with timeout
                val token = withTimeout(5000L) { // Timeout after 5 seconds
                    FirebaseMessaging.getInstance().token.await()
                }

                if (token.isNullOrEmpty()) {
                    Log.e("TokenViewModel", "FCM token is null or empty")
                    return@launch
                }

                // Remove token from other users
                removeTokenFromCollection("Patient", token)
                removeTokenFromCollection("Caregiver", token)

                // Update FCM token for the current user
                updateUserToken(userId, role, token)
            } catch (e: CancellationException) {
                Log.d("TokenViewModel", "Token update operation cancelled.")
            } catch (e: Exception) {
                Log.e("TokenViewModel", "Error updating FCM token", e)
            }
        }
    }

    /**
     * Removes the specified token from the given collection.
     */
    private suspend fun removeTokenFromCollection(collection: String, token: String) {
        try {
            val querySnapshot = firestore.collection(collection)
                .whereEqualTo("fcmToken", token)
                .get()
                .await()

            querySnapshot.documents.forEach { document ->
                firestore.collection(collection).document(document.id)
                    .update("fcmToken", FieldValue.delete())
                    .await()
                Log.d("TokenViewModel", "Removed token from $collection user: ${document.id}")
            }
        } catch (e: CancellationException) {
            Log.d("TokenViewModel", "Token removal operation cancelled for $collection.")
        } catch (e: Exception) {
            Log.e("TokenViewModel", "Error removing token from $collection", e)
        }
    }

    /**
     * Updates the FCM token for a specific user based on their role.
     */
    private suspend fun updateUserToken(userId: String, role: String, token: String) {
        try {
            val collection = when (role) {
                "Caregiver" -> "Caregiver"
                "Patient" -> "Patient"
                else -> {
                    Log.e("TokenViewModel", "Unknown user role: $role")
                    return
                }
            }

            firestore.collection(collection).document(userId)
                .update("fcmToken", token)
                .await()
            Log.d("TokenViewModel", "FCM token updated for $role: $userId")
        } catch (e: Exception) {
            Log.e("TokenViewModel", "Error updating FCM token for $role: $userId", e)
        }
    }

    /**
     * Removes the FCM token associated with the current user upon logout.
     */
    fun removeFCMToken(userId: String, role: String) {
        viewModelScope.launch {
            try {
                val collection = when (role) {
                    "Caregiver" -> "Caregiver"
                    "Patient" -> "Patient"
                    else -> {
                        Log.e("TokenViewModel", "Unknown user role: $role")
                        return@launch
                    }
                }

                firestore.collection(collection).document(userId)
                    .update("fcmToken", FieldValue.delete())
                    .await()
                Log.d("TokenViewModel", "FCM token removed for $role: $userId")
            } catch (e: CancellationException) {
                Log.d("TokenViewModel", "Token removal operation cancelled.")
            } catch (e: Exception) {
                Log.e("TokenViewModel", "Error removing FCM token for $role: $userId", e)
            }
        }
    }
}
