package com.example.trackurpill.userManagement.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.LoggedInUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoggedInUserViewModel(app: Application) : AndroidViewModel(app) {

    private val _loggedInUserLD = MutableLiveData<LoggedInUser?>()
    val loggedInUserLD: LiveData<LoggedInUser?> get() = _loggedInUserLD

    private val sharedPreferences = app.getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)

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
