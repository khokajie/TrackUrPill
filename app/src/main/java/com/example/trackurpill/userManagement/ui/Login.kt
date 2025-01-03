package com.example.trackurpill.userManagement.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.trackurpill.MainActivity
import com.example.trackurpill.R
import com.example.trackurpill.data.AuthViewModel
import com.example.trackurpill.databinding.FragmentLoginBinding
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel
import com.example.trackurpill.userManagement.data.TokenViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.launch

class Login : Fragment() {

    private lateinit var binding: FragmentLoginBinding
    private val nav by lazy { findNavController() }
    private val authViewModel: AuthViewModel by activityViewModels()
    private val userViewModel: LoggedInUserViewModel by activityViewModels()
    private val tokenViewModel: TokenViewModel by activityViewModels()
    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)

        // Set click listeners
        binding.loginButton.setOnClickListener { handleLogin() }
        binding.registerLink.setOnClickListener { navigateToRegister() }
        binding.forgotPasswordLink.setOnClickListener { showForgotPasswordDialog() }
        binding.resendVerificationLink.setOnClickListener { showResendVerificationDialog() }

        // Initially hide the resend verification link
        binding.resendVerificationLink.visibility = View.GONE

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
                // Attempt to log in using AuthViewModel
                val (role, userId) = authViewModel.login(email, password)
                val user = authViewModel.getCurrentUser()

                if (user != null) {
                    // Reload user to get the latest verification status
                    user.reload().addOnCompleteListener { reloadTask ->
                        if (reloadTask.isSuccessful) {
                            if (user.isEmailVerified) {
                                // Email is verified, proceed with login
                                if (role != "NA" && userId != null) {
                                    storeUserSession(userId, role)
                                    userViewModel.setLoggedInUser(role, userId)
                                    Log.d("Login", "User logged in with role: $role and userId: $userId")

                                    // Update FCM token using TokenViewModel
                                    tokenViewModel.updateFCMToken(userId, role)

                                    navigateBasedOnRole(role)
                                } else {
                                    showToast("Invalid user role or ID.")
                                }
                            } else {
                                // Email is not verified, show message and offer to resend
                                showToast("Please verify your email before logging in.")
                                // Show the resend verification email link
                                binding.resendVerificationLink.visibility = View.VISIBLE
                                showResendVerificationDialog() // Automatically show the dialog
                            }
                        } else {
                            showToast("Failed to reload user. Please try again.")
                            Log.e("Login", "User reload failed", reloadTask.exception)
                        }
                    }
                } else {
                    showToast("Invalid email or password.")
                }
            } catch (e: Exception) {
                handleLoginException(e)
            } finally {
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
        Log.e("Login", "Login failed", e)
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

    private fun showForgotPasswordDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_forgot_password, null)
        val emailEditText = dialogView.findViewById<TextInputEditText>(R.id.forgotPasswordEmail)
        val dialogSendButton = dialogView.findViewById<View>(R.id.dialogSendButton)
        val dialogCancelButton = dialogView.findViewById<View>(R.id.dialogCancelButton)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Handle Send Button Click
        dialogSendButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your email address", Toast.LENGTH_SHORT).show()
            } else {
                sendPasswordResetEmail(email)
                dialog.dismiss()
            }
        }

        // Handle Cancel Button Click
        dialogCancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun sendPasswordResetEmail(email: String) {
        firebaseAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "Password reset email sent. Check your inbox.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    Log.e("Login", "Password reset email failed", task.exception)
                }
            }
    }

    private fun showResendVerificationDialog() {
        // Inflate the custom dialog layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_resend_verification, null)
        val dialogSendButton = dialogView.findViewById<View>(R.id.dialogSendButton)
        val dialogCancelButton = dialogView.findViewById<View>(R.id.dialogCancelButton)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Handle Resend Button Click
        dialogSendButton.setOnClickListener {
            resendVerificationEmail()
            dialog.dismiss()
        }

        // Handle Cancel Button Click
        dialogCancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun resendVerificationEmail() {
        val user = authViewModel.getCurrentUser()
        user?.let {
            if (!it.isEmailVerified) {
                it.sendEmailVerification()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            showToast("Verification email sent to ${it.email}. Please check your inbox.")
                        } else {
                            showToast("Failed to send verification email. Please try again.")
                            Log.e("Login", "Error sending verification email", task.exception)
                        }
                    }
            } else {
                showToast("Your email is already verified.")
            }
        } ?: run {
            showToast("No logged-in user found. Please log in again.")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun storeUserSession(userId: String, userType: String) {
        val sharedPreferences = requireContext().getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("userID", userId)
            putString("userType", userType)
            apply()
        }
        Log.d("Login", "User session stored: UserType=$userType, UserId=$userId")
    }

    override fun onResume() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView).visibility = View.GONE
        (requireActivity() as MainActivity).hideTopAppBar()
        super.onResume()
    }

    override fun onPause() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView).visibility = View.VISIBLE
        (requireActivity() as MainActivity).showTopAppBar()
        super.onPause()
    }
}
