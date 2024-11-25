package com.example.trackurpill.medicationManagement.ui

import MedicationHistoryAdapter
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.databinding.FragmentMedicationHistoryBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.medicationManagement.util.MedicationAdapter
import com.example.trackurpill.medicationManagement.util.ReminderAdapter
import com.example.trackurpill.util.ReminderScheduler
import com.google.firebase.auth.FirebaseAuth

class MedicationHistoryFragment : Fragment() {

    private lateinit var binding: FragmentMedicationHistoryBinding
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private var patientId: String? = null // To distinguish between caregiver and patient views

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMedicationHistoryBinding.inflate(inflater, container, false)

        // Retrieve the optional patientId argument
        patientId = arguments?.getString("patientId")

        // Determine whose medications to load
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId // If patientId is passed, use it; otherwise, use the logged-in user ID

        // Initialize the adapter
        val adapter = MedicationHistoryAdapter { medication ->
            val currentMedication = medicationVM.get(medication.medicationId)
            if (currentMedication != null) {
                val updatedMedication = currentMedication.copy(
                    medicationStatus = "Active" // Update status to "Deleted"
                )
                medicationVM.setMedication(updatedMedication) // Update the medication record
                Toast.makeText(requireContext(), "Medication recover as Active", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to find medication record", Toast.LENGTH_SHORT).show()
            }
        }

        binding.recyclerViewMedicationHistory.adapter = adapter
        binding.recyclerViewMedicationHistory.layoutManager = LinearLayoutManager(requireContext())

        // Observe medication LiveData
        medicationVM.getMedicationLD().observe(viewLifecycleOwner) { medications ->
            val filteredMedications = medications?.filter {
                it.userId == targetUserId && it.medicationStatus == "Deleted" // Filter by user ID and status
            } ?: emptyList()
            adapter.submitList(filteredMedications)
        }

        return binding.root
    }
}
