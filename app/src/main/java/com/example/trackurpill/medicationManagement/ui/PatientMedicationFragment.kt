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

    private var isSearchViewFocused = false
    private var patientId: String? = null // To distinguish between caregiver and patient views

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPatientMedicationBinding.inflate(inflater, container, false)

        // Retrieve the optional patientId argument
        patientId = arguments?.getString("patientId")

        // Set up RecyclerView adapter
        adapter = MedicationAdapter { holder, medication ->
            holder.binding.root.setOnClickListener {
                nav.navigate(
                    R.id.medicationDetailsFragment,
                    Bundle().apply { putString("medicationId", medication.medicationId) }
                )
            }
        }
        binding.recyclerViewMedications.adapter = adapter

        // Determine whose medications to load
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId // If patientId is passed, use it; otherwise, use the logged-in user ID

        if (targetUserId != null) {
            medicationVM.getMedicationLD().observe(viewLifecycleOwner) { medications ->
                val filteredMedications = medications?.filter { it.userId == targetUserId } ?: emptyList()

                if (filteredMedications.isEmpty()) {
                    binding.noRecordText.visibility = View.VISIBLE
                } else {
                    binding.noRecordText.visibility = View.GONE
                }
                adapter.submitFullList(filteredMedications)
            }
        } else {
            binding.noRecordText.visibility = View.VISIBLE
            adapter.submitFullList(emptyList())
        }

        // Set up FAB click listener
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

        binding.searchViewMedications.clearFocus()

        // Set up SearchView functionality
        binding.searchViewMedications.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (isSearchViewFocused) { // Trigger filtering only when SearchView is focused
                    adapter.filter.filter(newText)
                }
                return true
            }
        })

        // Set focus change listener to monitor user interaction
        binding.searchViewMedications.setOnQueryTextFocusChangeListener { _, hasFocus ->
            isSearchViewFocused = hasFocus
        }

        return binding.root
    }

}
