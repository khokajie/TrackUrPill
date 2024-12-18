package com.example.trackurpill.userManagement.data

import android.app.Application
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.LoggedInUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.firestore.FirebaseFirestore

class LoggedInUserViewModel(app: Application) : AndroidViewModel(app) {

    private val _loggedInUserLD = MutableLiveData<LoggedInUser?>()
    val loggedInUserLD: LiveData<LoggedInUser?> get() = _loggedInUserLD

    private val _userRoleLD = MutableLiveData<String?>()
    val userRoleLD: LiveData<String?> get() = _userRoleLD

    private val sharedPreferences = app.getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun init() {
        val firebaseAuth = FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser

        if (currentUser != null) {
            val userId = currentUser.uid
            val cachedUserType = sharedPreferences.getString("userType", null)

            if (cachedUserType != null) {
                // Use cached data to restore session quickly
                _loggedInUserLD.value = LoggedInUser(cachedUserType, userId)
                println("Restored cached user session: $cachedUserType, $userId")
            } else {
                // Fetch user details from Firestore and cache them
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val userType = document.getString("userType") ?: "Unknown"
                            setLoggedInUser(userType, userId)
                        } else {
                            _loggedInUserLD.value = null
                            println("No user document found for userId=$userId")
                        }
                    }
                    .addOnFailureListener { e ->
                        _loggedInUserLD.value = null
                        println("Error fetching user document: ${e.message}")
                    }
            }
        } else {
            _loggedInUserLD.value = null
        }
    }

    fun setLoggedInUser(userType: String, userID: String) {
        _loggedInUserLD.value = LoggedInUser(userType, userID)

        // Cache user data in SharedPreferences
        with(sharedPreferences.edit()) {
            putString("userType", userType)
            putString("userID", userID)
            apply()
        }
        println("User session cached: UserType=$userType, UserId=$userID")
    }

    fun fetchUserRole() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val userId = currentUser.uid
            val cachedUserType = sharedPreferences.getString("userType", null)

            if (cachedUserType != null) {
                // Use cached data
                _userRoleLD.value = cachedUserType
                Log.d(TAG, "Restored cached user role: $cachedUserType")
            } else {
                // Fetch custom claims (user role) from Firebase Auth
                currentUser.getIdToken(true)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val result: GetTokenResult? = task.result
                            val userType = result?.claims?.get("role") as? String

                            if (userType != null) {
                                _userRoleLD.value = userType
                                // Cache the user type in SharedPreferences
                                with(sharedPreferences.edit()) {
                                    putString("userType", userType)
                                    apply()
                                }
                                Log.d(TAG, "Fetched and cached user role: $userType")
                            } else {
                                _userRoleLD.value = null
                                Log.e(TAG, "User role claim not found.")
                            }
                        } else {
                            _userRoleLD.value = null
                            Log.e(TAG, "Error fetching user token: ${task.exception?.message}")
                        }
                    }
            }
        } else {
            // No user is currently logged in
            _userRoleLD.value = null
            Log.d(TAG, "No authenticated user found.")
        }
    }

    fun clearData() {
        _loggedInUserLD.value = null

        // Clear cached user data
        with(sharedPreferences.edit()) {
            clear()
            apply()
        }
        println("Cleared user session and cache")
    }
}
