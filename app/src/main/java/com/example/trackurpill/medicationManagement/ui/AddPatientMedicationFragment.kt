package com.example.trackurpill.medicationManagement.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
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
import com.example.trackurpill.data.Medication
import com.example.trackurpill.databinding.FragmentAddPatientMedicationBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AddPatientMedicationFragment : Fragment() {

    private lateinit var binding: FragmentAddPatientMedicationBinding
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private val nav by lazy { findNavController() }
    private var medicationPhotoBlob: Blob? = null // Blob for storing the image
    private var patientId: String? = null // To distinguish between caregiver and patient views
    private lateinit var photoURI: Uri

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentAddPatientMedicationBinding.inflate(inflater, container, false)

        requestPermissionsIfNecessary()

        // Retrieve the optional patientId argument
        patientId = arguments?.getString("patientId")

        // Determine whose medications to load
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId

        // Handle photo upload
        binding.clickToAdd.setOnClickListener { showImagePickerOptions() }
        binding.photoContainer.setOnClickListener { showImagePickerOptions() }

        // Handle expiration date picker
        binding.txtExpirationDate.setOnClickListener { showDatePicker() }

        // Handle Add Medication button click
        binding.btnAddMedication.setOnClickListener {
            val medicationName = binding.txtMedicationName.text.toString().trim()
            val dosage = binding.txtDosage.text.toString().trim()
            val expirationDateString = binding.txtExpirationDate.text.toString().trim()
            val stockLevel = binding.txtStockLevel.text.toString().trim()

            // Validate inputs
            if (!validateInputs(medicationName, dosage, expirationDateString, stockLevel)) return@setOnClickListener

            // Parse expiration date
            val expirationDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(expirationDateString)

            // Create Medication object
            val medication = Medication(
                medicationId = UUID.randomUUID().toString(),
                medicationName = medicationName,
                dosage = dosage,
                expirationDate = expirationDate,
                stockLevel = stockLevel.toInt(),
                medicationPhoto = medicationPhotoBlob,
                medicationStatus = "Active",
                userId = targetUserId.toString()
            )

            // Save medication
            medicationVM.setMedication(medication)
            Toast.makeText(requireContext(), "Medication Added", Toast.LENGTH_SHORT).show()
            nav.navigateUp()
        }

        return binding.root
    }

    private fun requestPermissionsIfNecessary() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
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
                1 -> pickImageFromGallery()
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
                Toast.makeText(requireContext(), "Failed to capture image", Toast.LENGTH_SHORT).show()
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
            val compressedBitmap = compressBitmap(originalBitmap, 800, 800)
            val byteArrayOutputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
            val compressedByteArray = byteArrayOutputStream.toByteArray()

            medicationPhotoBlob = Blob.fromBytes(compressedByteArray)

            // Display the compressed image in the ImageView
            binding.imgMedicationPhoto.apply {
                visibility = View.VISIBLE
                setImageBitmap(compressedBitmap)
            }

            // Hide the placeholder icon
            binding.iconAddPhoto.visibility = View.GONE
            binding.clickToAdd.text = "Change photo"
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun pickImageFromGallery() {
        getMedicationImage.launch("image/*")
    }

    private val getMedicationImage =
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
            val compressedBitmap = compressBitmap(originalBitmap, 800, 800)
            val byteArrayOutputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
            val compressedByteArray = byteArrayOutputStream.toByteArray()

            medicationPhotoBlob = Blob.fromBytes(compressedByteArray)

            // Display the compressed image in the ImageView
            binding.imgMedicationPhoto.apply {
                visibility = View.VISIBLE
                setImageBitmap(compressedBitmap)
            }

            // Hide the placeholder icon
            binding.iconAddPhoto.visibility = View.GONE
            binding.clickToAdd.text = "Change photo"
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show()
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

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val formattedDate = String.format("%02d/%02d/%d", month + 1, dayOfMonth, year)
                binding.txtExpirationDate.setText(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun validateInputs(
        medicationName: String,
        dosage: String,
        expirationDateString: String,
        stockLevel: String
    ): Boolean {
        var isValid = true

        if (medicationName.isEmpty()) {
            binding.txtLayoutMedicationName.error = "Medication name cannot be empty"
            isValid = false
        } else {
            binding.txtLayoutMedicationName.error = null
        }

        if (dosage.isEmpty()) {
            binding.txtLayoutDosage.error = "Dosage cannot be empty"
            isValid = false
        } else {
            binding.txtLayoutDosage.error = null
        }

        val expirationDate = try {
            SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(expirationDateString)
        } catch (e: Exception) {
            null
        }

        if (expirationDate == null) {
            binding.txtLayoutExpirationDate.error = "Invalid date format. Use MM/dd/yyyy"
            isValid = false
        } else if (expirationDate.before(Calendar.getInstance().time)) {
            binding.txtLayoutExpirationDate.error = "Expiration date cannot be in the past"
            isValid = false
        } else {
            binding.txtLayoutExpirationDate.error = null
        }

        if (stockLevel.isEmpty() || stockLevel.toIntOrNull() == null || stockLevel.toInt() < 0) {
            binding.txtLayoutStockLevel.error = "Stock level must be a positive number"
            isValid = false
        } else {
            binding.txtLayoutStockLevel.error = null
        }

        if (medicationPhotoBlob == null) {
            Toast.makeText(requireContext(), "Please upload a photo", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

}
