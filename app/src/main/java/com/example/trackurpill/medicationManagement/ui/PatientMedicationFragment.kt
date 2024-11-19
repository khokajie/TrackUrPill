package com.example.trackurpill.medicationManagement.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPatientMedicationBinding.inflate(inflater, container, false)

        val firebaseAuth = FirebaseAuth.getInstance()

        // Set up RecyclerView adapter
        val adapter = MedicationAdapter { holder, medication ->
            holder.binding.root.setOnClickListener { showMedicationDetails(medication.medicationId) }
        }
        binding.recyclerViewMedications.adapter = adapter

        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId != null) {
            medicationVM.getMedicationLD().observe(viewLifecycleOwner) { medications ->
                println("Medications received: $medications")
                if (medications != null) {
                    val filteredMedications = medications.filter { it.userId == currentUserId }
                    adapter.submitList(filteredMedications)
                } else {
                    println("Medications list is null")
                    adapter.submitList(emptyList())
                }
            }

        } else {
            // Handle the case where the user is not logged in
            println("Error: Current user is null.")
        }
        // Set up FAB click listener
        binding.fabAddMedication.setOnClickListener {
            nav.navigate(R.id.addPatientMedicationFragment)
        }

        return binding.root
    }

    private fun showMedicationDetails(medicationId: String) {
        nav.navigate(
            R.id.medicationDetails,
            Bundle().apply { putString("medicationId", medicationId) }
        )
    }
}


