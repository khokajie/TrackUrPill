package com.example.trackurpill.medicationManagement.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.trackurpill.R
import com.example.trackurpill.databinding.FragmentPatientMedicationBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.medicationManagement.util.MedicationAdapter
import com.google.firebase.auth.FirebaseAuth

class PatientMedicationFragment : Fragment() {

    private lateinit var binding: FragmentPatientMedicationBinding
    private val nav by lazy { findNavController() }
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private lateinit var adapter: MedicationAdapter

    private var patientId: String? = null // To distinguish between caregiver and patient views
    private var searchQuery: String? = null // Preserve search query when navigating back

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPatientMedicationBinding.inflate(inflater, container, false)

        // Retrieve the optional patientId argument
        patientId = arguments?.getString("patientId")

        adapter = MedicationAdapter { medication ->
            nav.navigate(
                R.id.medicationDetailsFragment,
                Bundle().apply { putString("medicationId", medication.medicationId) }
            )
        }
        binding.recyclerViewMedications.adapter = adapter

        binding.searchViewMedications.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText // Save the search query
                adapter.filter.filter(newText)
                return true
            }
        })

        binding.searchViewMedications.setOnCloseListener {
            searchQuery = null // Reset search query when search is closed
            true
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId // If patientId is passed, use it; otherwise, use the logged-in user ID

        if (targetUserId != null) {
            medicationVM.getMedicationLD().observe(viewLifecycleOwner) { medications ->
                val filteredMedications = medications?.filter { it.userId == targetUserId && it.medicationStatus == "Active"} ?: emptyList()

                // Submit the filtered list only if it's non-null
                adapter.submitFullList(filteredMedications)

                // Apply the saved search query
                searchQuery?.let {
                    adapter.filter.filter(it)
                }

                // Update the visibility of the "No Records" text
                binding.noRecordText.visibility = if (filteredMedications.isEmpty()) View.VISIBLE else View.GONE
            }
        } else {
            // If targetUserId is null, show no records and clear the adapter
            binding.noRecordText.visibility = View.VISIBLE
            adapter.submitFullList(emptyList())
        }

        binding.fabAddMedication.setOnClickListener {
            if (patientId != null) {
                nav.navigate(
                    R.id.addPatientMedicationFragment,
                    Bundle().apply { putString("patientId", patientId) }
                )
            } else {
                nav.navigate(R.id.addPatientMedicationFragment)
            }
        }

        return binding.root
    }

}
