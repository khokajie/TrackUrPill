package com.example.trackurpill.userManagement.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.trackurpill.MainActivity
import com.example.trackurpill.R
import com.example.trackurpill.data.AuthViewModel
import com.example.trackurpill.databinding.FragmentLoginBinding
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class Login : Fragment() {

    private lateinit var binding: FragmentLoginBinding
    private val nav by lazy { findNavController() }
    private val authViewModel: AuthViewModel by viewModels()
    private val userViewModel: LoggedInUserViewModel by activityViewModels()
    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)

        binding.loginButton.setOnClickListener { handleLogin() }
        binding.registerLink.setOnClickListener { navigateToRegister() }

        return binding.root
    }

    private fun handleLogin() {
        val email = binding.txtEmail.text.toString().trim()
        val password = binding.txtPassword.text.toString().trim()

        if (validateInputs(email, password)) {
            performLogin(email, password)
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        return when {
            email.isEmpty() -> {
                showToast("Email is required.")
                false
            }
            password.isEmpty() -> {
                showToast("Password is required.")
                false
            }
            else -> true
        }
    }

    private fun performLogin(email: String, password: String) {
        // Disable login button to prevent multiple clicks
        binding.loginButton.isEnabled = false
        lifecycleScope.launch {
            try {
                // issue here***
                // Authenticate and retrieve role and userId
                val (role, userId) = authViewModel.login(email, password)
                userViewModel.setLoggedInUser(role, userId.toString())
                println("User logged in")

                if (role != "NA" && userId != null) {
                    // Navigate based on role
                    navigateBasedOnRole(role)
                } else {
                    showToast("Invalid email or password.")
                }
            } catch (e: Exception) {
                handleLoginException(e)
            } finally {
                // Re-enable login button
                binding.loginButton.isEnabled = true
            }
        }
    }

    private fun handleLoginException(e: Exception) {
        when (e) {
            is FirebaseAuthInvalidUserException -> showToast("No account found with this email.")
            is FirebaseAuthInvalidCredentialsException -> showToast("Wrong email or password.")
            else -> showToast("Login failed: ${e.message}")
        }
    }

    private fun navigateBasedOnRole(role: String) {
        val destination = if (role == "Patient") {
            R.id.patientMedicationFragment
        } else {
            R.id.caregiverMonitorFragment
        }
        nav.navigate(destination)
    }

    private fun navigateToRegister() {
        nav.navigate(R.id.registerFragment)
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        // Hides bottom navigation
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView).visibility = View.GONE
        (requireActivity() as MainActivity).hideTopAppBar()
        super.onResume()
    }

    override fun onPause() {
        // Unhidden bottom navigation
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView).visibility = View.VISIBLE
        (requireActivity() as MainActivity).showTopAppBar()
        super.onPause()
    }
}
