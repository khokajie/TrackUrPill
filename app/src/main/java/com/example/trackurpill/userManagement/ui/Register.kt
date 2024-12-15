package com.example.trackurpill.userManagement.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.trackurpill.MainActivity
import com.example.trackurpill.R
import com.example.trackurpill.data.AuthViewModel
import com.example.trackurpill.data.Caregiver
import com.example.trackurpill.data.Patient
import com.example.trackurpill.databinding.FragmentRegisterBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class Register : Fragment() {

    private lateinit var binding: FragmentRegisterBinding
    private val nav by lazy { findNavController() }
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)

        binding.registerButton.setOnClickListener { handleRegistration() }
        binding.loginLink.setOnClickListener { navigateToLogin() }

        return binding.root
    }

    private fun handleRegistration() {
        val username = binding.txtUsername.text.toString().trim()
        val email = binding.txtEmail.text.toString().trim()
        val password = binding.txtPassword.text.toString().trim()
        val confirmPassword = binding.txtConfirmPassword.text.toString().trim()

        // Check if a role is selected
        val role = when {
            binding.roleUser.isChecked -> "Patient"
            binding.roleCaregiver.isChecked -> "Caregiver"
            else -> {
                showToast("Please select whether you are a Patient or a Caregiver.")
                return
            }
        }

        if (validateInputs(username, email, password, confirmPassword)) {
            registerUser(username, email, password, role)
        }
    }

    private fun validateInputs(username: String, email: String, password: String, confirmPassword: String): Boolean {
        return when {
            username.isEmpty() -> {
                showToast("Username is required.")
                false
            }
            email.isEmpty() -> {
                showToast("Email is required.")
                false
            }
            password.isEmpty() -> {
                showToast("Password is required.")
                false
            }
            confirmPassword.isEmpty() -> {
                showToast("Please confirm your password.")
                false
            }
            password != confirmPassword -> {
                showToast("Passwords do not match.")
                false
            }
            else -> true
        }
    }

    private fun registerUser(username: String, email: String, password: String, role: String) {
        lifecycleScope.launch {
            try {
                // Create a user object
                val userDetails = if (role == "Patient") {
                    Patient(
                        userId = "", // To be generated in ViewModel
                        userName = username,
                        userEmail = email,
                        userAge = 0,
                        userPhoto = null,
                        isCaregiver = false
                    )
                } else {
                    Caregiver(
                        userId = "", // To be generated in ViewModel
                        userName = username,
                        userEmail = email,
                        userAge = 0,
                        isCaregiver = true,
                        userPhoto = null,
                        patientList = emptyList()
                    )
                }

                // Call ViewModel to handle registration
                val isSuccess = authViewModel.registerUser(role, userDetails, password)
                if (isSuccess) {
                    val user = authViewModel.getCurrentUser()
                    user?.sendEmailVerification()?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            showToast("Registration successful! Verification email sent to $email. Please verify before logging in.")
                            navigateToLogin()
                        } else {
                            showToast("Registration successful, but failed to send verification email.")
                        }
                    }
                } else {
                    showToast("Registration failed. Please try again.")
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
            }
        }
    }

    private fun navigateToLogin() {
        nav.navigate(R.id.loginFragment)
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
