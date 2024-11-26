package com.example.trackurpill.caregiverManagement.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.caregiverManagement.data.CaregiverMonitorViewModel
import com.example.trackurpill.caregiverManagement.util.PatientAdapter
import com.example.trackurpill.databinding.FragmentCaregiverMonitorBinding
import com.example.trackurpill.databinding.FragmentPatientMedicationBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.medicationManagement.util.MedicationAdapter
import com.google.firebase.auth.FirebaseAuth

class CaregiverMonitorFragment : Fragment() {

    private lateinit var binding: FragmentCaregiverMonitorBinding
    private val nav by lazy { findNavController() }
    private val patientViewModel: CaregiverMonitorViewModel by activityViewModels()
    private lateinit var adapter: PatientAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentCaregiverMonitorBinding.inflate(inflater, container, false)

        adapter = PatientAdapter { patient ->
            //
        }
        binding.recyclerViewPatients.adapter = adapter
        binding.recyclerViewPatients.layoutManager = LinearLayoutManager(requireContext())

        val caregiverId = FirebaseAuth.getInstance().currentUser?.uid
        println("Caregiver = ${caregiverId}")

        // Initialize ViewModel with the caregiver ID
        caregiverId?.let { patientViewModel.init(it) }

        // Observe patients LiveData
        patientViewModel.getPatientListLD().observe(viewLifecycleOwner) { patients ->
            adapter.submitList(patients)
            println("${patients}")
        }

        return binding.root
    }

}

