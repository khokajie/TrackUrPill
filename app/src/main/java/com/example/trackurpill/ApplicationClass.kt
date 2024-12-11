package com.example.trackurpill

import android.app.Application
import com.google.firebase.FirebaseApp
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate

class ApplicationClass : Application() {
    override fun onCreate() {
        super.onCreate()
        // Disable Dark Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        try {
            FirebaseApp.initializeApp(this)
            Log.d("ApplicationClass", "Firebase initialized successfully.")
        } catch (e: Exception) {
            Log.e("ApplicationClass", "Error initializing Firebase", e)
        }
    }
}