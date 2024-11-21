package com.example.trackurpill

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.trackurpill.data.AuthViewModel
import com.example.trackurpill.databinding.ActivityMainBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val nav by lazy { supportFragmentManager.findFragmentById(R.id.host)!!.findNavController() }
    private lateinit var appBarConfiguration: AppBarConfiguration

    private val userViewModel: LoggedInUserViewModel by viewModels()
    private val medicationViewModel: PatientMedicationViewModel by viewModels()

    private val patientDestinations = setOf(
        R.id.patientMedicationFragment,
        R.id.healthHistoryFragment,
        R.id.userProfileFragment
    )

    private val caregiverDestinations = setOf(
        R.id.patientMedicationFragment,
        R.id.caregiverMonitorFragment,
        R.id.patientHealthTrackingFragment,
        R.id.userProfileFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {

        userViewModel.init()
        medicationViewModel.init()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userViewModel.loggedInUserLD.observe(this, Observer { loggedInUser ->
            println("Observer triggered with value: $loggedInUser")
            if (loggedInUser != null) {
                configureNavigationBasedOnUserType(loggedInUser.userType)
                navigateBasedOnRole(loggedInUser.userType)
            } else {
                navigateToLogin()
            }
        })

        setSupportActionBar(binding.topAppBar)
        binding.bottomNavigationView.setupWithNavController(nav)

    }

    private fun configureNavigationBasedOnUserType(userType: String) {
        when (userType) {
            "Patient" -> {
                configureAppBar(patientDestinations)
                configureBottomNav(R.menu.patient_bottom_nav_menu)
            }
            "Caregiver" -> {
                configureAppBar(caregiverDestinations)
                configureBottomNav(R.menu.caregiver_bottom_nav_menu)
            }
        }
        setupActionBarWithNavController(nav, appBarConfiguration)
        showBottomNavigation()
    }

    private fun configureAppBar(destinations: Set<Int>) {
        appBarConfiguration = AppBarConfiguration(destinations)
    }

    private fun configureBottomNav(menuRes: Int) {
        binding.bottomNavigationView.menu.clear()
        binding.bottomNavigationView.inflateMenu(menuRes)
    }

    private fun navigateToLogin() {
        hideBottomNavigation()
        nav.navigate(R.id.loginFragment)
    }

    private fun showBottomNavigation() {
        binding.bottomNavigationView.visibility = View.VISIBLE
    }

    private fun hideBottomNavigation() {
        binding.bottomNavigationView.visibility = View.GONE
    }

    fun hideTopAppBar() {
        supportActionBar?.hide()
    }

    fun showTopAppBar() {
        supportActionBar?.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        return nav.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun navigateBasedOnRole(role: String) {
        val destination = if (role == "Patient") {
            R.id.addPatientMedicationFragment
        } else {
            R.id.caregiverMonitorFragment
        }
        nav.navigate(destination)
    }


}
