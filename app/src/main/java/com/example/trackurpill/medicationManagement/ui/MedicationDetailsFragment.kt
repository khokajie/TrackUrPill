package com.example.trackurpill.medicationManagement.ui

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.os.Bundle
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
import com.example.trackurpill.data.Reminder
import com.example.trackurpill.databinding.FragmentMedicationDetailsBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.medicationManagement.data.ReminderViewModel
import com.example.trackurpill.medicationManagement.util.ReminderAdapter
import com.example.trackurpill.util.ReminderScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import java.text.SimpleDateFormat
import java.util.*

class MedicationDetailsFragment : Fragment() {

    private lateinit var binding: FragmentMedicationDetailsBinding
    private val nav by lazy { findNavController() }
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private val reminderVM: ReminderViewModel by activityViewModels()
    private lateinit var medicationId: String
    private lateinit var currentUserId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMedicationDetailsBinding.inflate(inflater, container, false)

        medicationId = arguments?.getString("medicationId") ?: ""
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid.toString()

        observeMedicationDetails()

        val adapter = ReminderAdapter(
            onDelete = { reminder ->
                // Delete from local/Firestore
                reminderVM.deleteReminder(reminder.reminderId)
                // Cancel on the server
                cancelReminder(reminder.reminderId,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Reminder canceled successfully", Toast.LENGTH_SHORT).show()
                    },
                    onError = { e ->
                        Toast.makeText(requireContext(), "Failed to cancel reminder: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onEdit = { reminder ->
                showEditReminderDialog(reminder)
            }
        )

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
            deleteMedication()
        }

        binding.editMedicationButton.setOnClickListener {
            showEditMedicationDialog()
        }

        return binding.root
    }

    private fun cancelReminder(reminderId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val data = hashMapOf("reminderId" to reminderId)
        FirebaseFunctions.getInstance()
            .getHttpsCallable("cancelReminder")
            .call(data)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    private fun observeMedicationDetails() {
        medicationVM.getMedicationLiveData(medicationId).observe(viewLifecycleOwner) { medication ->
            medication?.let {
                binding.medicationName.text = it.medicationName
                binding.medicationDosage.text = "Dosage: ${it.dosage}"
                binding.expirationDate.text = "Expiration Date: ${it.expirationDate}"
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

                val reminderId = UUID.randomUUID().toString()
                val medicationName = binding.medicationName.text.toString()
                val dosageText = binding.medicationDosage.text.toString().removePrefix("Dosage: ").trim()

                var date: String? = null
                var day: String? = null

                when (selectedFrequency) {
                    "Once" -> {
                        date = String.format("%02d/%02d/%04d", datePicker.dayOfMonth, datePicker.month + 1, datePicker.year)
                    }
                    "Weekly" -> {
                        day = dayPicker.selectedItem.toString()
                    }
                }

                val reminder = Reminder(
                    reminderId = reminderId,
                    date = date,
                    hour = hour,
                    minute = minute,
                    frequency = selectedFrequency,
                    day = day,
                    medicationId = medicationId
                )
                reminderVM.setReminder(reminder)

                when (selectedFrequency) {
                    "Once" -> {
                        val reminderTimeMillis = getReminderTimeMillis(date, hour, minute)
                        if (reminderTimeMillis != null) {
                            ReminderScheduler.scheduleReminderAt(
                                context = requireContext(),
                                reminderId = reminderId,
                                medicationName = medicationName,
                                medicationId = medicationId,
                                dosage = dosageText,
                                userId = currentUserId,
                                date = date!!,
                                hour = hour,
                                minute = minute
                            )
                        } else {
                            Toast.makeText(requireContext(), "Invalid date or time", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Daily" -> {
                        ReminderScheduler.scheduleDailyReminder(
                            context = requireContext(),
                            reminderId = reminderId,
                            medicationName = medicationName,
                            medicationId = medicationId,
                            dosage = dosageText,
                            userId = currentUserId,
                            hour = hour,
                            minute = minute
                        )
                    }
                    "Weekly" -> {
                        ReminderScheduler.scheduleWeeklyReminder(
                            context = requireContext(),
                            reminderId = reminderId,
                            medicationName = medicationName,
                            medicationId = medicationId,
                            dosage = dosageText,
                            userId = currentUserId,
                            hour = hour,
                            minute = minute,
                            day = day ?: "Sunday"
                        )
                    }
                }
                Toast.makeText(requireContext(), "Reminder set successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showEditReminderDialog(reminder: Reminder) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_timer, null)

        val timePicker = dialogView.findViewById<TimePicker>(R.id.timePicker)
        val frequencySpinner = dialogView.findViewById<Spinner>(R.id.frequencySpinner)
        val dayPicker = dialogView.findViewById<Spinner>(R.id.dayPicker)
        val datePicker = dialogView.findViewById<DatePicker>(R.id.datePicker)

        timePicker.setIs24HourView(true)
        dayPicker.visibility = View.GONE
        datePicker.visibility = View.GONE

        // Pre-fill data
        timePicker.hour = reminder.hour
        timePicker.minute = reminder.minute

        // Set frequency selection
        when (reminder.frequency) {
            "Once" -> {
                frequencySpinner.setSelection(0)
                datePicker.visibility = View.VISIBLE
                reminder.date?.split("/")?.let { parts ->
                    if (parts.size == 3) {
                        val d = parts[0].toInt()
                        val m = parts[1].toInt() - 1
                        val y = parts[2].toInt()
                        datePicker.updateDate(y, m, d)
                    }
                }
            }
            "Daily" -> frequencySpinner.setSelection(1)
            "Weekly" -> {
                frequencySpinner.setSelection(2)
                dayPicker.visibility = View.VISIBLE
                val days = resources.getStringArray(R.array.days_of_week)
                val dayIndex = days.indexOf(reminder.day ?: "Sunday")
                if (dayIndex >= 0) {
                    dayPicker.setSelection(dayIndex)
                }
            }
        }

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
            .setTitle("Edit Reminder")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val hour = timePicker.hour
                val minute = timePicker.minute
                val selectedFrequency = frequencySpinner.selectedItem.toString()

                val medicationName = binding.medicationName.text.toString()
                val dosageText = binding.medicationDosage.text.toString().removePrefix("Dosage: ").trim()

                var updatedDate: String? = null
                var updatedDay: String? = null

                when (selectedFrequency) {
                    "Once" -> {
                        updatedDate = String.format("%02d/%02d/%04d", datePicker.dayOfMonth, datePicker.month + 1, datePicker.year)
                    }
                    "Weekly" -> {
                        updatedDay = dayPicker.selectedItem.toString()
                    }
                }

                val updatedReminder = reminder.copy(
                    date = updatedDate,
                    hour = hour,
                    minute = minute,
                    frequency = selectedFrequency,
                    day = updatedDay
                )

                reminderVM.setReminder(updatedReminder)

                when (selectedFrequency) {
                    "Once" -> {
                        val reminderTimeMillis = getReminderTimeMillis(updatedDate, hour, minute)
                        if (reminderTimeMillis != null) {
                            ReminderScheduler.scheduleReminderAt(
                                context = requireContext(),
                                reminderId = updatedReminder.reminderId,
                                medicationName = medicationName,
                                medicationId = updatedReminder.medicationId,
                                dosage = dosageText,
                                userId = currentUserId,
                                date = updatedDate!!,
                                hour = hour,
                                minute = minute
                            )
                        } else {
                            Toast.makeText(requireContext(), "Invalid date or time", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Daily" -> {
                        ReminderScheduler.scheduleDailyReminder(
                            context = requireContext(),
                            reminderId = updatedReminder.reminderId,
                            medicationName = medicationName,
                            medicationId = updatedReminder.medicationId,
                            dosage = dosageText,
                            userId = currentUserId,
                            hour = hour,
                            minute = minute
                        )
                    }
                    "Weekly" -> {
                        ReminderScheduler.scheduleWeeklyReminder(
                            context = requireContext(),
                            reminderId = updatedReminder.reminderId,
                            medicationName = medicationName,
                            medicationId = updatedReminder.medicationId,
                            dosage = dosageText,
                            userId = currentUserId,
                            hour = hour,
                            minute = minute,
                            day = updatedDay ?: "Sunday"
                        )
                    }
                }

                Toast.makeText(requireContext(), "Reminder updated successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun getReminderTimeMillis(date: String?, hour: Int, minute: Int): Long? {
        return try {
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val parsedDate = dateFormat.parse(date ?: return null)
            calendar.time = parsedDate
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    private fun showEditMedicationDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_medication, null)

        val medicationName = dialogView.findViewById<TextInputEditText>(R.id.medicationName)
        val dosage = dialogView.findViewById<TextInputEditText>(R.id.dosage)
        val expirationDate = dialogView.findViewById<TextInputEditText>(R.id.expirationDate)
        val stockLevel = dialogView.findViewById<TextInputEditText>(R.id.stockLevel)
        val saveButton = dialogView.findViewById<MaterialButton>(R.id.dialogSaveButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.dialogCancelButton)

        val currentMedication = medicationVM.get(medicationId)
        currentMedication?.let {
            medicationName.setText(it.medicationName)
            dosage.setText(it.dosage)
            expirationDate.setText(it.expirationDate)
            stockLevel.setText(it.stockLevel.toString())
        }

        expirationDate.setOnClickListener {
            showDatePickerDialog(expirationDate)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        saveButton.setOnClickListener {
            updateMedication(dialog, medicationName, dosage, expirationDate, stockLevel)
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDatePickerDialog(expirationDate: TextInputEditText) {
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

    private fun updateMedication(
        dialog: AlertDialog,
        medicationName: TextInputEditText,
        dosage: TextInputEditText,
        expirationDate: TextInputEditText,
        stockLevel: TextInputEditText
    ) {
        val updatedName = medicationName.text.toString().trim()
        val updatedDosage = dosage.text.toString().trim()
        val updatedExpirationDate = expirationDate.text.toString().trim()
        val updatedStockLevel = stockLevel.text.toString().trim()

        if (updatedName.isEmpty() || updatedDosage.isEmpty() || updatedExpirationDate.isEmpty() || updatedStockLevel.isEmpty()) {
            Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
        } else if (updatedStockLevel.toIntOrNull() == null) {
            Toast.makeText(requireContext(), "Stock level must be a number", Toast.LENGTH_SHORT).show()
        } else {
            val updatedMedication = medicationVM.get(medicationId)?.copy(
                medicationName = updatedName,
                dosage = updatedDosage,
                expirationDate = updatedExpirationDate,
                stockLevel = updatedStockLevel.toInt()
            )
            updatedMedication?.let { medicationVM.setMedication(it) }
            Toast.makeText(requireContext(), "Medication updated", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }




    private fun deleteMedication() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Medication")
            .setMessage("Are you sure you want to delete this medication?")
            .setPositiveButton("Delete") { _, _ ->
                val currentMedication = medicationVM.get(medicationId)
                if (currentMedication != null) {
                    val updatedMedication = currentMedication.copy(
                        medicationStatus = "Deleted"
                    )
                    medicationVM.setMedication(updatedMedication)
                    Toast.makeText(requireContext(), "Medication marked as deleted", Toast.LENGTH_SHORT).show()
                    nav.navigateUp()
                } else {
                    Toast.makeText(requireContext(), "Failed to find medication record", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }
}
