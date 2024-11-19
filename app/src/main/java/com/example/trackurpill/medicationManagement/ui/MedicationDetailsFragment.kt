package com.example.trackurpill.medicationManagement.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import android.widget.TimePicker
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.R
import com.example.trackurpill.data.Reminder
import com.example.trackurpill.databinding.FragmentMedicationDetailsBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.medicationManagement.data.ReminderViewModel
import com.example.trackurpill.medicationManagement.util.ReminderAdapter
import java.text.SimpleDateFormat
import java.util.Calendar

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
            println("Reminder received: $reminders")
            if (reminders != null) {
                val filteredReminders = reminders.filter { it.medicationId == medicationId }
                println("$filteredReminders");
                adapter.submitList(filteredReminders)
            } else {
                println("Reminders list is null")
                adapter.submitList(emptyList())
            }
        }

        binding.recyclerViewReminders.layoutManager = LinearLayoutManager(requireContext())

        // Handle Add Reminder Button
        binding.addReminderButton.setOnClickListener {
            showSetTimerDialog()
        }

        binding.deleteMedicationButton.setOnClickListener {
            medicationVM.deleteMedication(medicationId)
        }

        return binding.root
    }

    private fun showSetTimerDialog() {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_timer, null)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.timePicker)
        val frequencySpinner = dialogView.findViewById<Spinner>(R.id.frequencySpinner)
        val statusSpinner = dialogView.findViewById<Spinner>(R.id.statusSpinner)

        // Configure the TimePicker
        timePicker.setIs24HourView(true)

        // Build and show the dialog
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Set Reminder")
            .setView(dialogView)
            .setPositiveButton("Set") { _, _ ->
                // Get the selected time
                val hour = timePicker.hour
                val minute = timePicker.minute
                val selectedFrequency = frequencySpinner.selectedItem.toString()
                val selectedStatus = statusSpinner.selectedItem.toString()

                // Prepare reminder time
                val reminderTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }.time

                // Create a reminder object and save it
                val reminder = Reminder(
                    reminderId = generateUniqueReminderId(),
                    reminderTime = reminderTime,
                    frequency = selectedFrequency,
                    status = selectedStatus,
                    medicationId = medicationId
                )
                reminderVM.setReminder(reminder)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    fun generateUniqueReminderId(): String {
        return java.util.UUID.randomUUID().toString()
    }

}
