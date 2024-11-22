package com.example.trackurpill.userManagement.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.trackurpill.R
import com.example.trackurpill.data.AuthViewModel
import com.example.trackurpill.databinding.FragmentUserProfileBinding
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch


class UserProfile : Fragment() {
    private lateinit var binding: FragmentUserProfileBinding
    private val authViewModel: AuthViewModel by viewModels()
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

        }

        binding.btnLogout.setOnClickListener {
            authViewModel.logout()
            userViewModel.clearData()
            //clear back stack every time enter top level destination
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView).menu.clear()
            nav.navigate(R.id.loginFragment)
        }

        return binding.root
    }
}