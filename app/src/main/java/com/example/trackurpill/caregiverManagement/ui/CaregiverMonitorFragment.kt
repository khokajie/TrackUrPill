package com.example.trackurpill.caregiverManagement.ui

import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.R
import com.example.trackurpill.caregiverManagement.data.CaregiverMonitorViewModel
import com.example.trackurpill.caregiverManagement.util.PatientAdapter
import com.example.trackurpill.databinding.FragmentCaregiverMonitorBinding
import com.google.android.material.textfield.TextInputEditText
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
            nav.navigate(
                R.id.caregiverPatientDetailsFragment,
                Bundle().apply { putString("patientId", patient.userId) }
            )
        }
        binding.recyclerViewPatients.adapter = adapter
        binding.recyclerViewPatients.layoutManager = LinearLayoutManager(requireContext())

        binding.fabAddPatient.setOnClickListener {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_email_invitation, null)
            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()

            val emailInput = dialogView.findViewById<TextInputEditText>(R.id.emailInput)
            val btnCancel = dialogView.findViewById<View>(R.id.dialogCancelButton)
            val btnSend = dialogView.findViewById<View>(R.id.dialogSendButton)

            btnCancel.setOnClickListener { dialog.dismiss() }

            btnSend.setOnClickListener {
                val email = emailInput.text.toString().trim()

                if (email.isEmpty()) {
                    emailInput.error = "Email is required"
                    return@setOnClickListener
                }

                val caregiverId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

                // Send invitation and handle result
                patientViewModel.sendPatientInvitation(email, caregiverId) { result ->
                    Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
                    if (result == "Invitation sent successfully") {
                        dialog.dismiss()
                    }
                }
            }

            dialog.show()
        }




        val caregiverId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Observe filtered patients assigned to the caregiver
        val filteredPatientsLD = patientViewModel.observePatients(caregiverId)
        filteredPatientsLD.observeForever { patients ->
            adapter.submitList(patients)
        }

        return binding.root
    }
}
