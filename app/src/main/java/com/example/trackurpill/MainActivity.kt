package com.example.trackurpill

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.trackurpill.data.LoggedInUser
import com.example.trackurpill.databinding.ActivityMainBinding
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    private val userViewModel: LoggedInUserViewModel by viewModels()

    private val patientDestinations = setOf(
        R.id.patientMedicationFragment,
        R.id.healthHistoryFragment,
        R.id.userProfileFragment
    )

    private val caregiverDestinations = setOf(
        R.id.patientMedicationFragment,
        R.id.caregiverMonitorFragment,
        R.id.healthHistoryFragment,
        R.id.userProfileFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createNotificationChannel()
        checkAndRequestNotificationPermission()

        setSupportActionBar(binding.topAppBar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.host) as NavHostFragment
        navController = navHostFragment.navController

        // Initialize ViewModel and observe user state
        userViewModel.init()
        userViewModel.loggedInUserLD.observe(this, Observer { loggedInUser ->
            if (loggedInUser != null) {
                configureNavigationBasedOnUserType(loggedInUser)
            } else {
                navigateToLogin()
            }
        })

        setupBottomNavigation()
    }

    private fun configureNavigationBasedOnUserType(user: LoggedInUser) {
        when (user.userType) {
            "Patient" -> {
                configureAppBar(patientDestinations)
                configureBottomNav(R.menu.patient_bottom_nav_menu)
            }
            "Caregiver" -> {
                configureAppBar(caregiverDestinations)
                configureBottomNav(R.menu.caregiver_bottom_nav_menu)
            }
        }
        setupActionBarWithNavController(navController, appBarConfiguration)
        navigateBasedOnRole(user.userType)
        showBottomNavigation()
    }

    private fun configureAppBar(destinations: Set<Int>) {
        appBarConfiguration = AppBarConfiguration(destinations)
    }

    private fun configureBottomNav(menuRes: Int) {
        binding.bottomNavigationView.menu.clear()
        binding.bottomNavigationView.inflateMenu(menuRes)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val destination = item.itemId
            if (navController.currentDestination?.id != destination) {
                navController.navigate(destination)
            }
            true
        }
    }

    private fun navigateToLogin() {
        hideBottomNavigation()
        navController.navigate(R.id.loginFragment)
    }

    private fun showBottomNavigation() {
        binding.bottomNavigationView.visibility = View.VISIBLE
    }

    private fun hideBottomNavigation() {
        binding.bottomNavigationView.visibility = View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun navigateBasedOnRole(role: String) {
        val destination = if (role == "Patient") {
            R.id.patientMedicationFragment
        } else {
            R.id.caregiverMonitorFragment
        }
        if (navController.currentDestination?.id != destination) {
            navController.navigate(destination)
        }
    }

    fun hideTopAppBar() {
        supportActionBar?.hide()
    }

    fun showTopAppBar() {
        supportActionBar?.show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannelCompat.Builder(
                "REMINDER_CHANNEL",
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
                .setName("Medication Reminders")
                .setDescription("Notifications for scheduled medication reminders")
                .build()

            NotificationManagerCompat.from(this).createNotificationChannel(channel)
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
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
