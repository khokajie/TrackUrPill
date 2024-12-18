package com.example.trackurpill.caregiverManagement.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.R
import com.example.trackurpill.databinding.FragmentHealthHistoryBinding
import com.example.trackurpill.healthTrackingManagement.data.HealthHistoryViewModel
import com.example.trackurpill.healthTrackingManagement.util.HealthRecordAdapter
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaregiverMonitorHealthHistoryFragment : Fragment() {

    private lateinit var binding: FragmentHealthHistoryBinding
    private val healthHistoryVM: HealthHistoryViewModel by activityViewModels()
    private lateinit var adapter: HealthRecordAdapter
    private val nav by lazy { findNavController() }
    private var patientId: String? = null // To distinguish between caregiver and patient views

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHealthHistoryBinding.inflate(inflater, container, false)

        // Retrieve the optional patientId argument
        patientId = arguments?.getString("patientId")

        adapter = HealthRecordAdapter()

        binding.recyclerViewHealthRecords.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CaregiverMonitorHealthHistoryFragment.adapter
        }


        // Determine whose health records to load
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId // If patientId is passed, use it; otherwise, use the logged-in user ID

        if (targetUserId != null) {
            healthHistoryVM.getResultLD().observe(viewLifecycleOwner) { healthRecords ->
                val filteredHealthRecords = healthRecords?.filter { it.userId == targetUserId } ?: emptyList()

                // Update no record text visibility
                binding.noRecordText.visibility = if (filteredHealthRecords.isEmpty()) View.VISIBLE else View.GONE

                // Find the latest valid record for BMI, height, and weight
                val latestValidRecord = filteredHealthRecords
                    .filter { it.height > 0 && it.weight > 0 && it.bmi > 0 && it.recordDateTime != null }
                    .maxWithOrNull(compareBy { parseDate(it.recordDateTime) })

                // Update the UI with the latest valid record values
                if (latestValidRecord != null) {
                    binding.bmiValue.text = String.format("BMI\n%.1f", latestValidRecord.bmi)
                    binding.heightValue.text = "${latestValidRecord.height} cm"
                    binding.weightValue.text = "${latestValidRecord.weight} kg"
                } else {
                    // Display default or "N/A" if no valid record is found
                    binding.bmiValue.text = "N/A"
                    binding.heightValue.text = "N/A"
                    binding.weightValue.text = "N/A"
                }

                // Submit the filtered list to the adapter
                adapter.submitList(filteredHealthRecords)
            }
        } else {
            // If user ID is null, show no records and an empty list
            binding.noRecordText.visibility = View.VISIBLE
            adapter.submitList(emptyList())
        }

        // Floating Action Button to add a new health record
        binding.fabAddHealthRecord.visibility = View.GONE

        return binding.root
    }

    // Define the date format matching your recordDateTime string
    val dateFormatter = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

    // Function to safely parse date strings
    fun parseDate(dateString: String?): Date {
        return try {
            dateString?.let { dateFormatter.parse(it) } ?: Date(0)
        } catch (e: Exception) {
            // Log the error if needed
            Date(0) // Fallback to epoch if parsing fails
        }
    }

}