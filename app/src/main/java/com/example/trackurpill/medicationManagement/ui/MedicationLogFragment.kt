package com.example.trackurpill.medicationManagement.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.R
import com.example.trackurpill.databinding.FragmentMedicationLogBinding
import com.example.trackurpill.medicationManagement.data.MedicationLogViewModel
import com.example.trackurpill.medicationManagement.util.MedicationAdapter
import com.example.trackurpill.medicationManagement.util.MedicationLogAdapter
import com.google.firebase.auth.FirebaseAuth

class MedicationLogFragment : Fragment() {

    private lateinit var binding: FragmentMedicationLogBinding
    private val medicationLogVM: MedicationLogViewModel by activityViewModels()
    private var patientId: String? = null // To distinguish between caregiver and patient views

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMedicationLogBinding.inflate(inflater, container, false)

        // Retrieve the optional patientId argument
        patientId = arguments?.getString("patientId")

        // Determine whose medications to load
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId // If patientId is passed, use it; otherwise, use the logged-in user ID

        val adapter = MedicationLogAdapter()
        binding.recyclerViewMedicationLog.adapter = adapter
        binding.recyclerViewMedicationLog.layoutManager = LinearLayoutManager(requireContext())

        medicationLogVM.getResultLD().observe(viewLifecycleOwner) { logs ->
            val filteredLog = logs?.filter {it.userId == targetUserId } ?: emptyList()
            adapter.submitList(filteredLog)
        }

        return binding.root
    }

}
