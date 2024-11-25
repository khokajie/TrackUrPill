package com.example.trackurpill.userManagement.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.trackurpill.R
import com.example.trackurpill.data.AuthViewModel
import com.example.trackurpill.databinding.FragmentUserProfileBinding
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch


class UserProfile : Fragment() {
    private lateinit var binding: FragmentUserProfileBinding
    private val authViewModel: AuthViewModel by activityViewModels()
    private val userViewModel: LoggedInUserViewModel by activityViewModels()
    private val nav by lazy { findNavController() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentUserProfileBinding.inflate(inflater, container, false)

        userViewModel.loggedInUserLD.observe(viewLifecycleOwner, Observer { loggedInUser ->
            if (loggedInUser != null) {
                // Launch a coroutine to fetch user details
                lifecycleScope.launch {
                    try {
                        val userDetails = authViewModel.fetchUserDetails(loggedInUser.userType, loggedInUser.userId)
                        if (userDetails != null) {
                            binding.txtUserName.text = "Username: ${userDetails.userName}"
                            binding.txtUserEmail.text = "Email: ${userDetails.userEmail}"
                            binding.txtUserAge.text = if (userDetails.userAge == 0) {
                                "Age: Not filled in yet"
                            } else {
                                "Age: ${userDetails.userAge}"
                            }

                            // Load medication photo
                            if (userDetails.userPhoto != null) {
                                val photoBytes = userDetails.userPhoto!!.toBytes() // Convert Blob to ByteArray
                                Glide.with(binding.imgProfilePicture)
                                    .load(photoBytes)
                                    .placeholder(R.drawable.ic_profile) // Replace with your placeholder image
                                    .into(binding.imgProfilePicture)
                            } else {
                                binding.imgProfilePicture.setImageResource(R.drawable.ic_profile) // Replace with your placeholder image
                            }
                        } else {
                            Toast.makeText(context, "Failed to load user details", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        // Button Listeners
        binding.btnEditProfile.setOnClickListener {
            nav.navigate(R.id.editProfileFragment)
        }

        binding.btnChangePassword.setOnClickListener {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null)
            val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()

            val currentPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.currentPassword)
            val newPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.newPassword)
            val confirmPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.confirmPassword)
            val btnCancel = dialogView.findViewById<View>(R.id.dialogCancelButton)
            val btnSave = dialogView.findViewById<View>(R.id.dialogSaveButton)

            btnCancel.setOnClickListener { dialog.dismiss() }

            btnSave.setOnClickListener {
                val currentPassword = currentPasswordInput.text.toString().trim()
                val newPassword = newPasswordInput.text.toString().trim()
                val confirmPassword = confirmPasswordInput.text.toString().trim()

                if (currentPassword.isEmpty()) {
                    currentPasswordInput.error = "Current password is required"
                    return@setOnClickListener
                }
                if (newPassword.isEmpty()) {
                    newPasswordInput.error = "New password is required"
                    return@setOnClickListener
                }
                if (confirmPassword.isEmpty()) {
                    confirmPasswordInput.error = "Confirm password is required"
                    return@setOnClickListener
                }
                if (newPassword != confirmPassword) {
                    confirmPasswordInput.error = "Passwords do not match"
                    return@setOnClickListener
                }

                // Handle password update logic with Firebase
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    val credential = FirebaseAuth.getInstance().signInWithEmailAndPassword(
                        user.email!!,
                        currentPassword
                    )

                    credential.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            user.updatePassword(newPassword)
                                .addOnCompleteListener { updateTask ->
                                    if (updateTask.isSuccessful) {
                                        Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                    } else {
                                        Toast.makeText(context, "Failed to update password: ${updateTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } else {
                            currentPasswordInput.error = "Incorrect current password"
                        }
                    }
                }
            }

            dialog.show()
        }


        binding.btnLogout.setOnClickListener {
            authViewModel.logout()
            userViewModel.clearData()
            //clear back stack every time enter top level destination
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView).menu.clear()
            nav.navigate(R.id.loginFragment)
        }

        binding.btnMedicationHistory.setOnClickListener {
            nav.navigate(R.id.medicationHistoryFragment)
        }

        return binding.root
    }


}