package com.example.trackurpill.caregiverManagement.ui// File: com.example.trackurpill.caregiverManagement.ui.CaregiverMonitorFragment.kt

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
import com.example.trackurpill.caregiverManagement.data.CaregiverMonitorViewModel
import com.example.trackurpill.caregiverManagement.util.PatientAdapter
import com.example.trackurpill.databinding.FragmentCaregiverMonitorBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

class CaregiverMonitorFragment : Fragment() {

    private lateinit var binding: FragmentCaregiverMonitorBinding
    private val nav by lazy { findNavController() }
    private val patientViewModel: CaregiverMonitorViewModel by activityViewModels()
    private lateinit var adapter: PatientAdapter
    private lateinit var functions: FirebaseFunctions

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentCaregiverMonitorBinding.inflate(inflater, container, false)
        functions = FirebaseFunctions.getInstance()

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
            val dialog = android.app.AlertDialog.Builder(requireContext())
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

                val caregiverId = FirebaseAuth.getInstance().currentUser?.uid
                if (caregiverId == null) {
                    Toast.makeText(requireContext(), "User not authenticated.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Call the Cloud Function
                sendPatientInvitation(email, caregiverId) { success, message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    if (success) {
                        dialog.dismiss()
                    }
                }
            }

            dialog.show()
        }

        val caregiverId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Observe filtered patients assigned to the caregiver
        patientViewModel.observePatients(caregiverId).observe(viewLifecycleOwner) { patients ->
            adapter.submitList(patients)
        }

        return binding.root
    }

    /**
     * Calls the sendPatientInvitation Cloud Function.
     */
    private fun sendPatientInvitation(email: String, caregiverId: String, callback: (Boolean, String) -> Unit) {
        val data = hashMapOf(
            "patientEmail" to email,
            "caregiverId" to caregiverId
        )

        functions
            .getHttpsCallable("sendPatientInvitation")
            .call(data)
            .continueWith { task ->
                // This continuation runs on the main thread
                val result = task.result?.data as? Map<*, *>
                result
            }
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    val e = task.exception
                    if (e is FirebaseFunctionsException) {
                        val code = e.code
                        val details = e.details
                        callback(false, "Error: ${e.message}")
                    } else {
                        callback(false, "Error: ${e?.message}")
                    }
                } else {
                    val result = task.result
                    val success = result?.get("success") as? Boolean ?: false
                    val message = result?.get("message") as? String ?: "Unknown response."

                    callback(success, message)
                }
            }
    }
}
