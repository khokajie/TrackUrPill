package com.example.trackurpill

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.trackurpill.data.LoggedInUser
import com.example.trackurpill.databinding.ActivityMainBinding
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val nav by lazy { supportFragmentManager.findFragmentById(R.id.host)!!.findNavController() }
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

        setSupportActionBar(binding.topAppBar)

        // Initialize ViewModel and observe user state
        userViewModel.init()
        userViewModel.loggedInUserLD.observe(this, Observer { loggedInUser ->
            if (loggedInUser != null) {
                configureNavigationBasedOnUserType(loggedInUser)
            } else {
                navigateToLogin()
            }
        })

        binding.bottomNavigationView.setupWithNavController(nav)
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
        setupActionBarWithNavController(nav, appBarConfiguration)
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

    override fun onSupportNavigateUp(): Boolean {
        return nav.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun navigateBasedOnRole(role: String) {
        val destination = if (role == "Patient") {
            R.id.patientMedicationFragment
        } else {
            R.id.caregiverMonitorFragment
        }
        if (nav.currentDestination?.id != destination) {
            nav.navigate(destination)
        }
    }

    fun hideTopAppBar() {
        supportActionBar?.hide()
    }

    fun showTopAppBar() {
        supportActionBar?.show()
    }


}
