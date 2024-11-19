package com.example.trackurpill.medicationManagement.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.trackurpill.R
import com.example.trackurpill.databinding.FragmentMedicationDetailsBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.medicationManagement.data.ReminderViewModel
import com.example.trackurpill.medicationManagement.util.ReminderAdapter
import java.text.SimpleDateFormat

class MedicationDetailsFragment : Fragment() {

    private lateinit var binding: FragmentMedicationDetailsBinding
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private val reminderVM: ReminderViewModel by activityViewModels()
    private lateinit var medicationId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMedicationDetailsBinding.inflate(inflater, container, false)

        // Get the medicationId from arguments
        medicationId = arguments?.getString("medicationId") ?: ""

        // Fetch medication details
        val medication = medicationVM.get(medicationId)
        if (medication != null) {
            binding.medicationName.text = medication.medicationName
            binding.medicationDosage.text = "Dosage: ${medication.dosage}"
            binding.expirationDate.text = "Expiration Date: ${
                SimpleDateFormat("MM/dd/yyyy").format(medication.expirationDate)
            }"
            binding.stockLevel.text = "Stock Level: ${medication.stockLevel}"

            // Convert Blob to Bitmap and display the image
            val medicationPhotoBlob = medication.medicationPhoto
            if (medicationPhotoBlob != null) {
                val bytes = medicationPhotoBlob.toBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                binding.medicationPhoto.setImageBitmap(bitmap)
            } else {
                binding.medicationPhoto.setImageResource(R.drawable.ic_medication_placeholder)
            }
        }

        // Set up the ReminderAdapter
        val adapter = ReminderAdapter { reminder ->
            // Handle delete reminder
            reminderVM.deleteReminder(reminder.reminderId)
        }
        binding.recyclerViewReminders.adapter = adapter

        // Observe reminders for this medication
        reminderVM.getReminderLD().observe(viewLifecycleOwner) { reminders ->
            val filteredReminders = reminders?.filter { it.medicationId == medicationId }
            adapter.submitList(filteredReminders ?: emptyList())
        }

        // Handle Add Reminder Button
        binding.addReminderButton.setOnClickListener {
            // Navigate to add reminder fragment
        }

        return binding.root
    }
}
