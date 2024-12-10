// File: app/src/main/java/com/example/trackurpill/MainActivity.kt

package com.example.trackurpill

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Observer
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.trackurpill.data.MedicationLog
import com.example.trackurpill.databinding.ActivityMainBinding
import com.example.trackurpill.medicationManagement.data.MedicationLogViewModel
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.notification.data.NotificationViewModel
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val nav by lazy { supportFragmentManager.findFragmentById(R.id.host)!!.findNavController() }
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val userViewModel: LoggedInUserViewModel by viewModels()
    private val notificationVM: NotificationViewModel by viewModels()
    private val medicationVM: PatientMedicationViewModel by viewModels()
    private val logVM: MedicationLogViewModel by viewModels()

    // Define top-level destinations
    private val patientTLD = setOf(
        R.id.patientMedicationFragment,
        R.id.healthHistoryFragment,
        R.id.userProfileFragment
    )

    private val caregiverTLD = setOf(
        R.id.caregiverMonitorFragment,
        R.id.patientMedicationFragment,
        R.id.healthHistoryFragment,
        R.id.userProfileFragment
    )

    // Variable to store the current user type
    private var currentUserType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle the intent when the activity is created
        handleIntent(intent)

        createNotificationChannel()
        checkAndRequestNotificationPermission()

        // Set up the action bar
        setSupportActionBar(binding.topAppBar)

        // Initialize ViewModels
        userViewModel.init()

        // Observe the LiveData
        userViewModel.loggedInUserLD.observe(this, Observer { loggedInUser ->
            if (loggedInUser != null) {
                currentUserType = loggedInUser.userType
                configureAppBar(loggedInUser.userType)
                configureBottomNav(loggedInUser.userType)
                setupActionBarWithNavController(nav, appBarConfiguration)
                binding.bottomNavigationView.setupWithNavController(nav)
                showBottomNavigation()

                if (savedInstanceState == null) { // Only navigate if first creation
                    // Navigate to the main content and clear the back stack
                    val startDestination = if (loggedInUser.userType == "Patient") {
                        R.id.patientMedicationFragment
                    } else {
                        R.id.caregiverMonitorFragment
                    }

                    nav.navigate(startDestination, null, NavOptions.Builder()
                        .setPopUpTo(R.id.loginFragment, true) // Remove loginFragment from back stack
                        .build())
                }
            } else {
                if (savedInstanceState == null) {
                    navigateToLogin()
                }
            }
        })

        // Check login state after setting up the observer
        checkLoginState()
    }

    private fun configureAppBar(userType: String) {
        appBarConfiguration = when (userType) {
            "Patient" -> AppBarConfiguration(patientTLD)
            "Caregiver" -> AppBarConfiguration(caregiverTLD)
            else -> AppBarConfiguration(setOf())
        }
    }

    private fun configureBottomNav(userType: String) {
        binding.bottomNavigationView.menu.clear()
        when (userType) {
            "Patient" -> binding.bottomNavigationView.inflateMenu(R.menu.patient_bottom_nav_menu)
            "Caregiver" -> binding.bottomNavigationView.inflateMenu(R.menu.caregiver_bottom_nav_menu)
            // Add other user types if necessary
        }
    }

    private fun navigateToLogin() {
        hideBottomNavigation()
        nav.navigate(R.id.loginFragment, null, NavOptions.Builder()
            .setPopUpTo(nav.graph.startDestinationId, true) // Clear the entire back stack
            .build())
    }

    private fun showBottomNavigation() {
        binding.bottomNavigationView.visibility = View.VISIBLE
    }

    private fun hideBottomNavigation() {
        binding.bottomNavigationView.visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_app_bar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_medication_log -> {
                nav.navigate(R.id.medicationLogFragment)
                true
            }
            R.id.action_notification -> {
                nav.navigate(R.id.notificationFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return nav.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    fun hideTopAppBar() {
        supportActionBar?.hide()
    }

    fun showTopAppBar() {
        supportActionBar?.show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "medication_reminders"
            val channelName = "Medication Reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for medication reminders"
            }

            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("MainActivity", "Notification channel created: $channelId")
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "Notification permission granted")
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "Notification permission denied")
            }
        }
    }

    private fun checkLoginState() {
        val sharedPreferences = getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getString("userID", null)
        val userType = sharedPreferences.getString("userType", null)

        if (userId != null && userType != null) {
            // Set the loggedInUser in the ViewModel
            userViewModel.setLoggedInUser(userType, userId)
            Log.d("MainActivity", "User logged in: $userId, Type: $userType")
        } else {
            Log.d("MainActivity", "User not logged in")
        }
    }

    // Helper method to check if the current destination is a top-level destination
    private fun isTopLevelDestination(destinationId: Int?): Boolean {
        if (destinationId == null) return false
        return when (currentUserType) {
            "Patient" -> patientTLD.contains(destinationId)
            "Caregiver" -> caregiverTLD.contains(destinationId)
            else -> false
        }
    }

    // Override the onBackPressed method to customize back navigation
    override fun onBackPressed() {
        val currentDestination = nav.currentDestination?.id

        when {
            currentDestination == R.id.patientMedicationFragment -> {
                // Show exit confirmation dialog
                showExitConfirmationDialog()
            }
            isTopLevelDestination(currentDestination) && currentDestination != R.id.patientMedicationFragment -> {
                // Navigate back to patientMedicationFragment
                nav.navigate(R.id.patientMedicationFragment, null, NavOptions.Builder()
                    .setLaunchSingleTop(true) // Avoid multiple instances
                    .setRestoreState(true)
                    .build())
            }
            else -> {
                // Default back action
                super.onBackPressed()
            }
        }
    }

    // Method to display an exit confirmation dialog
    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit the app?")
            .setPositiveButton("Yes") { dialog, which ->
                // Exit the app
                finish()
            }
            .setNegativeButton("No") { dialog, which ->
                // Dismiss the dialog
                dialog.dismiss()
            }
            .create()
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Handles incoming intents, particularly those triggered by notification taps.
     */
    private fun handleIntent(intent: Intent) {
        val type = intent.getStringExtra("type") // Type of notification
        val notificationId = intent.getStringExtra("notificationId")
        val senderId = intent.getStringExtra("senderId")
        val reminderId = intent.getStringExtra("reminderId")
        val medicationId = intent.getStringExtra("medicationId")
        val dosageStr = intent.getStringExtra("dosage") ?: "1 Tablet"
        val message = intent.getStringExtra("message") ?: "You have a new notification."

        Log.d("MainActivity", "handleIntent called with type: $type")

        // Handle notification tap if needed
        if (type == null) return

        when (type) {
            "reminder" -> {
                // Navigate to Medication Details or relevant screen
                // Implement navigation logic as per your app's flow
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                Log.d("MainActivity", "Reminder notification tapped: $message")
            }
            "invitation" -> {
                // Navigate to Invitation screen or relevant action
                // Implement navigation logic as per your app's flow
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                Log.d("MainActivity", "Invitation notification tapped: $message")
            }
            else -> {
                Toast.makeText(this, "Unknown notification type.", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Unknown notification type: $type")
            }
        }
    }
}
