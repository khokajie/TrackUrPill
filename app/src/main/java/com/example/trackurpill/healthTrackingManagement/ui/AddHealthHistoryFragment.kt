package com.example.trackurpill.healthTrackingManagement.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.trackurpill.data.HealthRecord
import com.example.trackurpill.databinding.FragmentAddHealthHistoryBinding
import com.example.trackurpill.healthTrackingManagement.data.HealthHistoryViewModel
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

        // Determine whose medications to load
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId // If patientId is passed, use it; otherwise, use the logged-in user ID

        binding.btnAddRecord.setOnClickListener {
            if (!validateInputs()) return@setOnClickListener

            val weight = binding.txtWeight.text.toString().toDoubleOrNull()
            val height = binding.txtHeight.text.toString().toDoubleOrNull()
            val bmi = if (weight != null && height != null && weight > 0 && height > 0) {
                calculateBMI(weight, height)
            } else {
                0.0
            }

            // Format the current date to "dd/MM/yyyy"
            val formattedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

            // Create HealthRecord object
            val healthRecord = HealthRecord(
                recordId = UUID.randomUUID().toString(),
                weight = weight ?: 0.0,
                height = height ?: 0.0,
                bloodPressure = parseBloodPressure(binding.txtBloodPressure.text.toString()),
                heartRate = binding.txtHeartRate.text.toString().toIntOrNull() ?: 0,
                bloodSugarLevels = binding.txtBloodSugarLevels.text.toString().toDoubleOrNull() ?: 0.0,
                cholesterolLevels = binding.txtCholesterolLevels.text.toString().toDoubleOrNull() ?: 0.0,
                temperature = binding.txtTemperature.text.toString().toDoubleOrNull() ?: 0.0,
                bmi = bmi,
                recordDateTime = getCurrentFormattedDateTime(), // Save as formatted string
                userId = targetUserId.toString()
            )

            // Save the health record
            healthHistoryVM.setHealthRecord(healthRecord)
            Toast.makeText(requireContext(), "Health record added successfully", Toast.LENGTH_SHORT).show()
            nav.navigateUp()
        }

        return binding.root
    }

    // Function to create a formatted date string
    fun getCurrentFormattedDateTime(): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formatter.format(System.currentTimeMillis())
    }

    private fun calculateBMI(weight: Double, height: Double): Double {
        // Convert height from cm to meters
        val heightInMeters = height / 100
        return weight / (heightInMeters * heightInMeters)
    }

    private fun validateInputs(): Boolean {
        val weight = binding.txtWeight.text.toString().trim()
        val height = binding.txtHeight.text.toString().trim()
        val bloodPressure = binding.txtBloodPressure.text.toString().trim()
        val heartRate = binding.txtHeartRate.text.toString().trim()
        val bloodSugar = binding.txtBloodSugarLevels.text.toString().trim()
        val cholesterol = binding.txtCholesterolLevels.text.toString().trim()
        val temperature = binding.txtTemperature.text.toString().trim()

        var isValid = true

        // Check if at least one field is filled
        if (weight.isEmpty() && height.isEmpty() && bloodPressure.isEmpty() && heartRate.isEmpty() &&
            bloodSugar.isEmpty() && cholesterol.isEmpty() && temperature.isEmpty()
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

        return isValid
    }

    private fun parseBloodPressure(bloodPressureInput: String): Int {
        return try {
            val values = bloodPressureInput.split("/")
            if (values.size == 2) {
                values[0].toIntOrNull() ?: 0 // Systolic pressure
            } else 0
        } catch (e: Exception) {
            0
        }
    }
}
