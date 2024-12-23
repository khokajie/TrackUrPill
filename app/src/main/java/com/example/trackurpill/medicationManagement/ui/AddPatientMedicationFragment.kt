package com.example.trackurpill.medicationManagement.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.trackurpill.data.Medication
import com.example.trackurpill.databinding.FragmentAddPatientMedicationBinding
import com.google.firebase.auth.FirebaseAuth
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.google.firebase.firestore.Blob
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.UUID

class AddPatientMedicationFragment : Fragment() {

    private lateinit var binding: FragmentAddPatientMedicationBinding
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val nav by lazy { findNavController() }
    private var medicationPhotoBlob: Blob? = null // Blob for storing the image

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAddPatientMedicationBinding.inflate(inflater, container, false)

        // Handle photo upload
        binding.clickToAdd.setOnClickListener { pickImage() }

        // Handle expiration date picker
        binding.txtExpirationDate.setOnClickListener { showDatePicker() }

        binding.btnAddMedication.setOnClickListener {
            val medicationName = binding.txtMedicationName.text.toString().trim()
            val dosage = binding.txtDosage.text.toString().trim()
            val expirationDateString = binding.txtExpirationDate.text.toString().trim()
            val stockLevel = binding.txtStockLevel.text.toString().trim()

            // Validate inputs
            if (!validateInputs(medicationName, dosage, expirationDateString, stockLevel)) return@setOnClickListener

            // Parse expiration date
            val expirationDate = SimpleDateFormat("MM/dd/yyyy").parse(expirationDateString)

            // Create Medication object
            val medication = Medication(
                medicationId = UUID.randomUUID().toString(),
                medicationName = medicationName,
                dosage = dosage,
                expirationDate = expirationDate,
                stockLevel = stockLevel.toInt(),
                medicationPhoto = medicationPhotoBlob,
                userId = firebaseAuth.currentUser?.uid.orEmpty()
            )

            // Save medication
            medicationVM.setMedication(medication)
            Toast.makeText(requireContext(), "Medication Added", Toast.LENGTH_SHORT).show()
            nav.navigateUp()
        }

        return binding.root
    }

    private fun pickImage() {
        getMedicationImage.launch("image/*")
    }

    private val getMedicationImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val inputStream = requireContext().contentResolver.openInputStream(it)
            val byteArray = inputStream?.readBytes()
            if (byteArray != null) {
                medicationPhotoBlob = Blob.fromBytes(byteArray)
                binding.imgMedicationPhoto.setImageURI(uri) // Show image preview
            }
        }
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
            SimpleDateFormat("MM/dd/yyyy").parse(expirationDateString)
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
