package com.example.trackurpill.userManagement.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class EditProfile : Fragment() {

    private lateinit var binding: FragmentEditProfileBinding
    private val userViewModel: LoggedInUserViewModel by activityViewModels()
    private val firestore = FirebaseFirestore.getInstance()
    private val nav by lazy { findNavController() }

    private var profileImageBlob: Blob? = null // For storing image as Blob
    private lateinit var photoURI: Uri

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentEditProfileBinding.inflate(inflater, container, false)

        requestPermissionsIfNecessary()

        // Load user profile
        userViewModel.loggedInUserLD.observe(viewLifecycleOwner) { loggedInUser ->
            loggedInUser?.let {
                loadUserProfile(it.userType, it.userId)
            }
        }

        binding.changeProfilePicture.setOnClickListener {
            showImagePickerOptions()
        }

        binding.saveButton.setOnClickListener {
            saveProfileChanges()
        }

        binding.cancelButton.setOnClickListener {
            nav.navigateUp()
        }

        return binding.root
    }

    private fun requestPermissionsIfNecessary() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = permissions.zip(grantResults.toTypedArray())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }

            if (deniedPermissions.isNotEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Permissions denied: $deniedPermissions",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery")

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Select Option")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> captureImageFromCamera()
                1 -> selectImageFromGallery()
            }
        }
        builder.show()
    }

    private fun captureImageFromCamera() {
        val photoFile = createImageFile()
        photoURI = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            photoFile
        )
        captureImageLauncher.launch(photoURI)
    }

    private val captureImageLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                handleCapturedImage()
            } else {
                Toast.makeText(requireContext(), "Failed to capture image", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? =
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun handleCapturedImage() {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(photoURI)
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

            // Display the captured image
            binding.profileImage.apply {
                visibility = View.VISIBLE
                setImageBitmap(compressedBitmap)
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Failed to process image",
                Toast.LENGTH_SHORT
            ).show()
            e.printStackTrace()
        }
    }

    private fun selectImageFromGallery() {
        selectImageLauncher.launch("image/*")
    }

    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                processSelectedImage(it)
            }
        }

    private fun processSelectedImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
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
                setImageBitmap(compressedBitmap)
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Failed to process image",
                Toast.LENGTH_SHORT
            ).show()
            e.printStackTrace()
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
                    } else {
                        binding.profileImage.setImageResource(R.drawable.ic_profile)
                    }
                } else {
                    Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT)
                    .show()
            }
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
                    Toast.makeText(
                        requireContext(),
                        "Failed to update profile",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}
