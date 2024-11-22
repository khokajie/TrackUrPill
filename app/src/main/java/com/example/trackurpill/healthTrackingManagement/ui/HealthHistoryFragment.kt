package com.example.trackurpill.healthTrackingManagement.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.R
import com.example.trackurpill.databinding.FragmentHealthHistoryBinding
import com.example.trackurpill.healthTrackingManagement.data.HealthHistoryViewModel
import com.example.trackurpill.healthTrackingManagement.util.HealthRecordAdapter
import com.google.firebase.auth.FirebaseAuth
import java.util.Date

class HealthHistoryFragment : Fragment() {

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

        adapter = HealthRecordAdapter { healthRecord ->
            healthHistoryVM.deleteHealthRecord(healthRecord.recordId)
            Toast.makeText(requireContext(), "Record deleted", Toast.LENGTH_SHORT).show()
        }

        binding.recyclerViewHealthRecords.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HealthHistoryFragment.adapter
        }

        // Determine whose medications to load
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId // If patientId is passed, use it; otherwise, use the logged-in user ID

        if (targetUserId != null) {
            healthHistoryVM.getHealthRecordsLD().observe(viewLifecycleOwner) { healthRecords ->
                val filteredHealthRecords = healthRecords?.filter { it.userId == targetUserId } ?: emptyList()
                println("HealthRecord: $filteredHealthRecords")

                // Update no record text visibility
                if (filteredHealthRecords.isEmpty()) {
                    binding.noRecordText.visibility = View.VISIBLE
                } else {
                    binding.noRecordText.visibility = View.GONE
                }

                // Find the latest valid record for BMI, height, and weight
                val latestValidRecord = filteredHealthRecords
                    .filter { it.height > 0 && it.weight > 0 && it.bmi > 0 }
                    .maxWithOrNull(compareBy { it.recordDateTime ?: Date(0) })

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

                // Update the RecyclerView with all filtered records
                adapter.submitList(filteredHealthRecords)
            }
        } else {
            // If user ID is null, show no records and an empty list
            binding.noRecordText.visibility = View.VISIBLE
            adapter.submitList(emptyList())
        }

        // Floating Action Button to add a new health record
        binding.fabAddHealthRecord.setOnClickListener {
            Toast.makeText(requireContext(), "Navigate to add new record", Toast.LENGTH_SHORT).show()
            if (patientId != null) {
                nav.navigate(
                    R.id.addHealthHistoryFragment,
                    Bundle().apply { putString("patientId", patientId) }
                )
            } else {
                nav.navigate(R.id.addHealthHistoryFragment)
            }
        }

        return binding.root
    }
}
