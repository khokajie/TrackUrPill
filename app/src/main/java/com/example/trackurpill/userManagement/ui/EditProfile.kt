package com.example.trackurpill.userManagement.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.trackurpill.R
import com.example.trackurpill.databinding.FragmentEditProfileBinding
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class EditProfile : Fragment() {

    private lateinit var binding: FragmentEditProfileBinding
    private val userViewModel: LoggedInUserViewModel by activityViewModels()
    private val firestore = FirebaseFirestore.getInstance()
    private val nav by lazy { findNavController() }

    private var profileImageBlob: Blob? = null // For storing image as Blob

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEditProfileBinding.inflate(inflater, container, false)

        // Load user profile
        userViewModel.loggedInUserLD.observe(viewLifecycleOwner) { loggedInUser ->
            loggedInUser?.let {
                loadUserProfile(it.userType, it.userId)
            }
        }

        binding.changeProfilePicture.setOnClickListener {
            selectImageFromGallery()
        }

        binding.saveButton.setOnClickListener {
            saveProfileChanges()
        }

        binding.cancelButton.setOnClickListener {
            nav.navigateUp()
        }

        return binding.root
    }

    private fun loadUserProfile(userType: String, userId: String) {
        val collection = if (userType == "Patient") "Patient" else "Caregiver"

        firestore.collection(collection).document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userName = document.getString("userName")
                    val userAge = document.getLong("userAge")?.toInt()
                    val userPhotoBlob = document.getBlob("userPhoto")

                    // Populate UI with user data
                    binding.txtUsername.setText(userName)
                    binding.txtAge.setText(userAge?.toString())

                    if (userPhotoBlob != null) {
                        Glide.with(this)
                            .load(userPhotoBlob.toBytes())
                            .placeholder(R.drawable.ic_profile)
                            .into(binding.profileImage)
                    }
                } else {
                    Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun selectImageFromGallery() {
        selectImageLauncher.launch("image/*")
    }

    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                try {
                    val inputStream = requireContext().contentResolver.openInputStream(it)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)

                    // Compress the image
                    val compressedBitmap =
                        compressBitmap(originalBitmap, 800, 800) // 800x800 resolution
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    compressedBitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        85,
                        byteArrayOutputStream
                    ) // 85% quality
                    val compressedByteArray = byteArrayOutputStream.toByteArray()

                    profileImageBlob = Blob.fromBytes(compressedByteArray)

                    // Display the selected image
                    binding.profileImage.apply {
                        visibility = View.VISIBLE
                        setImageURI(uri)
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT)
                        .show()
                    e.printStackTrace()
                }
            }
        }


    /**
     * Compress a Bitmap to the specified width and height.
     */
    private fun compressBitmap(original: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = original.width
        val height = original.height

        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxWidth
            newHeight = (maxWidth / aspectRatio).toInt()
        } else {
            newHeight = maxHeight
            newWidth = (maxHeight * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
    }


    private fun saveProfileChanges() {
        val userName = binding.txtUsername.text.toString().trim()
        val userAge = binding.txtAge.text.toString().trim()

        if (userName.isEmpty() || userAge.isEmpty()) {
            Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
            return
        }

        userViewModel.loggedInUserLD.value?.let { loggedInUser ->
            val collection = if (loggedInUser.userType == "Patient") "Patient" else "Caregiver"
            val userDocument = firestore.collection(collection).document(loggedInUser.userId)

            val updates = mutableMapOf<String, Any>(
                "userName" to userName,
                "userAge" to userAge.toInt()
            )

            // Update Blob only if a new image is selected
            if (profileImageBlob != null) {
                updates["userPhoto"] = profileImageBlob!!
            }

            userDocument.update(updates)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                    nav.navigateUp()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
