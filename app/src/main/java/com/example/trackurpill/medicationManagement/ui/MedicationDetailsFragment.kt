package com.example.trackurpill.medicationManagement.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.DatePicker
import android.widget.Spinner
import android.widget.TimePicker
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

        // Get the medicationId from arguments
        medicationId = arguments?.getString("medicationId") ?: ""

        // Fetch medication details
        val medication = medicationVM.get(medicationId)
        medication?.let { populateMedicationDetails(it) }

        // Set up the ReminderAdapter
        val adapter = ReminderAdapter { reminder ->
            reminderVM.deleteReminder(reminder.reminderId)
            ReminderScheduler.cancelReminder(requireContext(), reminder.reminderId)
        }
        binding.recyclerViewReminders.adapter = adapter
        binding.recyclerViewReminders.layoutManager = LinearLayoutManager(requireContext())

        // Observe reminders for this medication
        reminderVM.getReminderLD().observe(viewLifecycleOwner) { reminders ->
            val filteredReminders = reminders?.filter { it.medicationId == medicationId }
            adapter.submitList(filteredReminders ?: emptyList())
        }

        // Handle Add Reminder Button
        binding.addReminderButton.setOnClickListener {
            showSetTimerDialog()
        }

        // Handle Delete Medication Button
        binding.deleteMedicationButton.setOnClickListener {
            medicationVM.deleteMedication(medicationId)
            nav.navigateUp()
        }

        return binding.root
    }

    private fun populateMedicationDetails(medication: Medication) {
        binding.medicationName.text = medication.medicationName
        binding.medicationDosage.text = "Dosage: ${medication.dosage}"
        binding.expirationDate.text = "Expiration Date: ${
            medication.expirationDate?.let {
                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(it)
            } ?: "N/A"
        }"
        binding.stockLevel.text = "Stock Level: ${medication.stockLevel}"

        medication.medicationPhoto?.let { blob ->
            val bytes = blob.toBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            binding.medicationPhoto.setImageBitmap(bitmap)
        } ?: binding.medicationPhoto.setImageResource(R.drawable.ic_medication_placeholder)
    }

    private fun showSetTimerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_timer, null)

        val timePicker = dialogView.findViewById<TimePicker>(R.id.timePicker)
        val frequencySpinner = dialogView.findViewById<Spinner>(R.id.frequencySpinner)
        val statusSpinner = dialogView.findViewById<Spinner>(R.id.statusSpinner)
        val dayPicker = dialogView.findViewById<Spinner>(R.id.dayPicker)
        val datePicker = dialogView.findViewById<DatePicker>(R.id.datePicker)

        timePicker.setIs24HourView(true)
        dayPicker.visibility = View.GONE
        datePicker.visibility = View.GONE

        frequencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { // Once
                        datePicker.visibility = View.VISIBLE
                        dayPicker.visibility = View.GONE
                    }
                    1 -> { // Daily
                        datePicker.visibility = View.GONE
                        dayPicker.visibility = View.GONE
                    }
                    2 -> { // Weekly
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
                val selectedStatus = statusSpinner.selectedItem.toString()

                val selectedDate = if (selectedFrequency == "Once") {
                    String.format(
                        "%02d/%02d/%04d",
                        datePicker.month + 1,
                        datePicker.dayOfMonth,
                        datePicker.year
                    )
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
                    status = selectedStatus,
                    day = selectedDay,
                    medicationId = medicationId
                )

                reminderVM.setReminder(reminder)

                // Schedule the reminder
                when (selectedFrequency) {
                    "Once" -> ReminderScheduler.scheduleReminderAt(
                        context = requireContext(),
                        reminderTimeMillis = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                        }.timeInMillis,
                        medicationName = binding.medicationName.text.toString(),
                        medicationId = medicationId,
                        dosage = binding.medicationDosage.text.toString()
                    )
                    "Daily" -> ReminderScheduler.scheduleDailyReminder(
                        context = requireContext(),
                        reminderHour = hour,
                        reminderMinute = minute,
                        medicationName = binding.medicationName.text.toString(),
                        medicationId = medicationId,
                        dosage = binding.medicationDosage.text.toString()
                    )
                    "Weekly" -> ReminderScheduler.scheduleWeeklyReminder(
                        context = requireContext(),
                        reminderHour = hour,
                        reminderMinute = minute,
                        dayOfWeek = dayPicker.selectedItemPosition + 1,
                        medicationName = binding.medicationName.text.toString(),
                        medicationId = medicationId,
                        dosage = binding.medicationDosage.text.toString()
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }
}
