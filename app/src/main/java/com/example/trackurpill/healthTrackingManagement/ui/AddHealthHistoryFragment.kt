package com.example.trackurpill.healthTrackingManagement.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.trackurpill.data.HealthRecord
import com.example.trackurpill.databinding.FragmentAddHealthHistoryBinding
import com.example.trackurpill.healthTrackingManagement.data.HealthHistoryViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class AddHealthHistoryFragment : Fragment() {

    private lateinit var binding: FragmentAddHealthHistoryBinding
    private val healthHistoryVM: HealthHistoryViewModel by activityViewModels()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val nav by lazy { findNavController() }
    private var patientId: String? = null // To distinguish between caregiver and patient views

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAddHealthHistoryBinding.inflate(inflater, container, false)

        // Retrieve the optional patientId argument
        patientId = arguments?.getString("patientId")

        // Determine whose health records to load
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId // If patientId is passed, use it; otherwise, use the logged-in user ID

        // Set up focus change listeners for example hints
        setupFocusListeners()

        binding.btnAddRecord.setOnClickListener {
            if (!validateInputs()) return@setOnClickListener

            val weight = binding.txtWeight.text.toString().toDoubleOrNull()
            val height = binding.txtHeight.text.toString().toDoubleOrNull()
            val systolic = binding.txtSystolic.text.toString().toIntOrNull()
            val diastolic = binding.txtDiastolic.text.toString().toIntOrNull()
            val heartRate = binding.txtHeartRate.text.toString().toIntOrNull()
            val bloodSugarLevels = binding.txtBloodSugarLevels.text.toString().toDoubleOrNull()
            val cholesterolLevels = binding.txtCholesterolLevels.text.toString().toDoubleOrNull()
            val temperature = binding.txtTemperature.text.toString().toDoubleOrNull()

            val bmi = if (weight != null && height != null && weight > 0 && height > 0) {
                calculateBMI(weight, height)
            } else {
                0.0
            }

            // Create HealthRecord object
            val healthRecord = HealthRecord(
                recordId = UUID.randomUUID().toString(),
                weight = weight ?: 0.0,
                height = height ?: 0.0,
                systolic = systolic ?: 0,
                diastolic = diastolic ?: 0,
                heartRate = heartRate ?: 0,
                bloodSugarLevels = bloodSugarLevels ?: 0.0,
                cholesterolLevels = cholesterolLevels ?: 0.0,
                temperature = temperature ?: 0.0,
                bmi = bmi,
                // recordDateTime is automatically set to Timestamp.now() in the data class
                userId = targetUserId.toString()
            )

            // Save the health record
            healthHistoryVM.setHealthRecord(healthRecord)
            Toast.makeText(requireContext(), "Health record added successfully", Toast.LENGTH_SHORT).show()
            nav.navigateUp()

            // Dismiss the keyboard
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }

        return binding.root
    }

    // Function to calculate BMI
    private fun calculateBMI(weight: Double, height: Double): Double {
        // Convert height from cm to meters
        val heightInMeters = height / 100
        return weight / (heightInMeters * heightInMeters)
    }

    // Function to validate inputs
    private fun validateInputs(): Boolean {
        val weight = binding.txtWeight.text.toString().trim()
        val height = binding.txtHeight.text.toString().trim()
        val systolic = binding.txtSystolic.text.toString().trim()
        val diastolic = binding.txtDiastolic.text.toString().trim()
        val heartRate = binding.txtHeartRate.text.toString().trim()
        val bloodSugar = binding.txtBloodSugarLevels.text.toString().trim()
        val cholesterol = binding.txtCholesterolLevels.text.toString().trim()
        val temperature = binding.txtTemperature.text.toString().trim()

        var isValid = true

        // Check if at least one field is filled
        if (weight.isEmpty() && height.isEmpty() && systolic.isEmpty() && diastolic.isEmpty() &&
            heartRate.isEmpty() && bloodSugar.isEmpty() && cholesterol.isEmpty() && temperature.isEmpty()
        ) {
            Toast.makeText(requireContext(), "Please fill in at least one field.", Toast.LENGTH_SHORT).show()
            return false
        }

        // If weight or height is filled, both must be filled
        if (weight.isNotEmpty() || height.isNotEmpty()) {
            if (weight.isEmpty()) {
                binding.txtLayoutWeight.error = "Weight is required if height is entered"
                isValid = false
            } else {
                binding.txtLayoutWeight.error = null
            }

            if (height.isEmpty()) {
                binding.txtLayoutHeight.error = "Height is required if weight is entered"
                isValid = false
            } else {
                binding.txtLayoutHeight.error = null
            }
        }

        // If systolic or diastolic is filled, both must be filled
        if (systolic.isNotEmpty() || diastolic.isNotEmpty()) {
            if (systolic.isEmpty()) {
                binding.txtLayoutSystolic.error = "Systolic BP is required if Diastolic BP is entered"
                isValid = false
            } else {
                binding.txtLayoutSystolic.error = null
            }

            if (diastolic.isEmpty()) {
                binding.txtLayoutDiastolic.error = "Diastolic BP is required if Systolic BP is entered"
                isValid = false
            } else {
                binding.txtLayoutDiastolic.error = null
            }
        }

        // Validate systolic BP range
        systolic.toIntOrNull()?.let {
            if (it !in 80..200) {
                binding.txtLayoutSystolic.error = "Systolic BP must be between 80 and 200 mmHg"
                isValid = false
            } else {
                binding.txtLayoutSystolic.error = null
            }
        }

        // Validate diastolic BP range
        diastolic.toIntOrNull()?.let {
            if (it !in 50..150) {
                binding.txtLayoutDiastolic.error = "Diastolic BP must be between 50 and 150 mmHg"
                isValid = false
            } else {
                binding.txtLayoutDiastolic.error = null
            }
        }

        // Optionally: Validate other fields (e.g., heart rate, blood sugar levels, etc.)
        // Add similar validation as needed

        return isValid
    }

    /**
     * Sets up focus change listeners to display example hints when fields are focused.
     */
    private fun setupFocusListeners() {
        // Weight Field
        binding.txtWeight.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.txtLayoutWeight.helperText = "e.g., 70.5"
            } else {
                binding.txtLayoutWeight.helperText = null
            }
        }

        // Height Field
        binding.txtHeight.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.txtLayoutHeight.helperText = "e.g., 175.0"
            } else {
                binding.txtLayoutHeight.helperText = null
            }
        }

        // Systolic BP Field
        binding.txtSystolic.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.txtLayoutSystolic.helperText = "e.g., 120"
            } else {
                binding.txtLayoutSystolic.helperText = null
            }
        }

        // Diastolic BP Field
        binding.txtDiastolic.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.txtLayoutDiastolic.helperText = "e.g., 80"
            } else {
                binding.txtLayoutDiastolic.helperText = null
            }
        }

        // Heart Rate Field
        binding.txtHeartRate.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.txtLayoutHeartRate.helperText = "e.g., 75"
            } else {
                binding.txtLayoutHeartRate.helperText = null
            }
        }

        // Blood Sugar Levels Field
        binding.txtBloodSugarLevels.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.txtLayoutBloodSugarLevels.helperText = "e.g., 5.6"
            } else {
                binding.txtLayoutBloodSugarLevels.helperText = null
            }
        }

        // Cholesterol Levels Field
        binding.txtCholesterolLevels.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.txtLayoutCholesterolLevels.helperText = "e.g., 5.2"
            } else {
                binding.txtLayoutCholesterolLevels.helperText = null
            }
        }

        // Temperature Field
        binding.txtTemperature.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.txtLayoutTemperature.helperText = "e.g., 36.6"
            } else {
                binding.txtLayoutTemperature.helperText = null
            }
        }
    }
}
