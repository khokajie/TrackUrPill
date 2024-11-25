package com.example.trackurpill.medicationManagement.ui

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.DatePicker
import android.widget.Spinner
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.R
import com.example.trackurpill.data.Medication
import com.example.trackurpill.data.Reminder
import com.example.trackurpill.databinding.FragmentMedicationDetailsBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.medicationManagement.data.ReminderViewModel
import com.example.trackurpill.medicationManagement.util.ReminderAdapter
import com.example.trackurpill.util.ReminderScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class MedicationDetailsFragment : Fragment() {

    private lateinit var binding: FragmentMedicationDetailsBinding
    private val nav by lazy { findNavController() }
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private val reminderVM: ReminderViewModel by activityViewModels()
    private lateinit var medicationId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMedicationDetailsBinding.inflate(inflater, container, false)

        medicationId = arguments?.getString("medicationId") ?: ""

        observeMedicationDetails()

        val adapter = ReminderAdapter { reminder ->
            reminderVM.deleteReminder(reminder.reminderId)
            ReminderScheduler.cancelReminder(requireContext(), reminder.reminderId)
        }
        binding.recyclerViewReminders.adapter = adapter
        binding.recyclerViewReminders.layoutManager = LinearLayoutManager(requireContext())

        reminderVM.getReminderLD().observe(viewLifecycleOwner) { reminders ->
            val filteredReminders = reminders?.filter { it.medicationId == medicationId }
            adapter.submitList(filteredReminders ?: emptyList())
        }

        binding.addReminderButton.setOnClickListener {
            showSetTimerDialog()
        }

        binding.deleteMedicationButton.setOnClickListener {
            val currentMedication = medicationVM.get(medicationId)
            if (currentMedication != null) {
                val updatedMedication = currentMedication.copy(
                    medicationStatus = "Deleted" // Update status to "Deleted"
                )
                medicationVM.setMedication(updatedMedication) // Update the medication record
                Toast.makeText(requireContext(), "Medication marked as deleted", Toast.LENGTH_SHORT).show()
                nav.navigateUp() // Navigate back after the update
            } else {
                Toast.makeText(requireContext(), "Failed to find medication record", Toast.LENGTH_SHORT).show()
            }
        }

        binding.editMedicationButton.setOnClickListener {
            showEditMedicationDialog()
        }

        return binding.root
    }

    private fun observeMedicationDetails() {
        medicationVM.getMedicationLiveData(medicationId).observe(viewLifecycleOwner) { medication ->
            medication?.let {
                binding.medicationName.text = it.medicationName
                binding.medicationDosage.text = "Dosage: ${it.dosage}"
                binding.expirationDate.text = "Expiration Date: " + (it.expirationDate?.let { date ->
                    SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(date)
                } ?: "N/A")
                binding.stockLevel.text = "Stock Level: ${it.stockLevel}"

                it.medicationPhoto?.let { blob ->
                    val bytes = blob.toBytes()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    binding.medicationPhoto.setImageBitmap(bitmap)
                } ?: binding.medicationPhoto.setImageResource(R.drawable.ic_medication_placeholder)
            }
        }
    }

    private fun showSetTimerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_timer, null)

        val timePicker = dialogView.findViewById<TimePicker>(R.id.timePicker)
        val frequencySpinner = dialogView.findViewById<Spinner>(R.id.frequencySpinner)
        val dayPicker = dialogView.findViewById<Spinner>(R.id.dayPicker)
        val datePicker = dialogView.findViewById<DatePicker>(R.id.datePicker)

        timePicker.setIs24HourView(true)
        dayPicker.visibility = View.GONE
        datePicker.visibility = View.GONE

        frequencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        datePicker.visibility = View.VISIBLE
                        dayPicker.visibility = View.GONE
                    }
                    1 -> {
                        datePicker.visibility = View.GONE
                        dayPicker.visibility = View.GONE
                    }
                    2 -> {
                        datePicker.visibility = View.GONE
                        dayPicker.visibility = View.VISIBLE
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Set Reminder")
            .setView(dialogView)
            .setPositiveButton("Set") { _, _ ->
                val hour = timePicker.hour
                val minute = timePicker.minute
                val selectedFrequency = frequencySpinner.selectedItem.toString()

                val selectedDate = if (selectedFrequency == "Once") {
                    String.format("%02d/%02d/%04d", datePicker.month + 1, datePicker.dayOfMonth, datePicker.year)
                } else null

                val selectedDay = if (selectedFrequency == "Weekly") {
                    dayPicker.selectedItem.toString()
                } else null

                val reminder = Reminder(
                    reminderId = UUID.randomUUID().toString(),
                    date = selectedDate,
                    hour = hour,
                    minute = minute,
                    frequency = selectedFrequency,
                    day = selectedDay,
                    medicationId = medicationId
                )

                reminderVM.setReminder(reminder)

                when (selectedFrequency) {
                    "Once" -> {
                        val reminderTimeMillis = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                        }.timeInMillis
                        ReminderScheduler.scheduleReminderAt(
                            requireContext(),
                            reminderTimeMillis,
                            binding.medicationName.text.toString(),
                            medicationId,
                            binding.medicationDosage.text.toString()
                        )
                    }
                    "Daily" -> ReminderScheduler.scheduleDailyReminder(
                        requireContext(),
                        hour,
                        minute,
                        binding.medicationName.text.toString(),
                        medicationId,
                        binding.medicationDosage.text.toString()
                    )
                    "Weekly" -> ReminderScheduler.scheduleWeeklyReminder(
                        requireContext(),
                        hour,
                        minute,
                        dayPicker.selectedItemPosition + 1,
                        binding.medicationName.text.toString(),
                        medicationId,
                        binding.medicationDosage.text.toString()
                    )
                }
                Toast.makeText(requireContext(), "Reminder set successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showEditMedicationDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_medication, null)

        val medicationName = dialogView.findViewById<TextInputEditText>(R.id.medicationName)
        val dosage = dialogView.findViewById<TextInputEditText>(R.id.dosage)
        val expirationDate = dialogView.findViewById<TextInputEditText>(R.id.expirationDate)
        val stockLevel = dialogView.findViewById<TextInputEditText>(R.id.stockLevel)
        val saveButton = dialogView.findViewById<MaterialButton>(R.id.dialogSaveButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.dialogCancelButton)

        // Pre-fill existing data
        val currentMedication = medicationVM.get(medicationId)
        currentMedication?.let {
            medicationName.setText(it.medicationName)
            dosage.setText(it.dosage)
            expirationDate.setText(it.expirationDate?.let { date ->
                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(date)
            })
            stockLevel.setText(it.stockLevel.toString())
        }

        expirationDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                if (selectedDate.before(Calendar.getInstance())) {
                    Toast.makeText(requireContext(), "Date cannot be in the past", Toast.LENGTH_SHORT).show()
                } else {
                    expirationDate.setText(SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(selectedDate.time))
                }
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        saveButton.setOnClickListener {
            val updatedName = medicationName.text.toString().trim()
            val updatedDosage = dosage.text.toString().trim()
            val updatedExpirationDate = expirationDate.text.toString().trim()
            val updatedStockLevel = stockLevel.text.toString().trim()

            if (updatedName.isEmpty() || updatedDosage.isEmpty() || updatedExpirationDate.isEmpty() || updatedStockLevel.isEmpty()) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
            } else if (updatedStockLevel.toIntOrNull() == null) {
                Toast.makeText(requireContext(), "Stock level must be a number", Toast.LENGTH_SHORT).show()
            } else {
                val updatedMedication = currentMedication?.copy(
                    medicationName = updatedName,
                    dosage = updatedDosage,
                    expirationDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(updatedExpirationDate),
                    stockLevel = updatedStockLevel.toInt()
                )
                updatedMedication?.let { medicationVM.setMedication(it) }
                Toast.makeText(requireContext(), "Medication updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

}
