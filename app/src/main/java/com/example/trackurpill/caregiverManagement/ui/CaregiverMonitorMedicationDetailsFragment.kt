package com.example.trackurpill.caregiverManagement.ui

import android.app.DatePickerDialog
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
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
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel
import com.example.trackurpill.util.ReminderScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import java.text.SimpleDateFormat
import java.util.*

class CaregiverMonitorMedicationDetailsFragment : Fragment() {

    private lateinit var binding: FragmentMedicationDetailsBinding
    private val nav by lazy { findNavController() }
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private val loggedInUserVM: LoggedInUserViewModel by activityViewModels()
    private val reminderVM: ReminderViewModel by activityViewModels()
    private lateinit var medicationId: String
    private lateinit var currentUserId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMedicationDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        medicationId = arguments?.getString("medicationId") ?: ""
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid.toString()

        // Fetch the user role
        loggedInUserVM.fetchUserRole()

        setupRoleObserver()
        setupUI()
        observeMedicationDetails()

        // Apply all adjustments
        adjustViewSizes()
        adjustTextSizes()
        adjustMarginsAndPadding()
    }

    private fun setupRoleObserver() {
        loggedInUserVM.userRoleLD.observe(viewLifecycleOwner) { role ->
            if (role.equals("Caregiver", ignoreCase = true)) {
                // Show the Send Reminder Button
                binding.sendReminderButton.visibility = View.VISIBLE
            } else {
                // Hide the Send Reminder Button
                binding.sendReminderButton.visibility = View.GONE
            }
        }
    }

    private fun setupUI() {
        val adapter = ReminderAdapter(
            onDelete = { reminder ->
                cancelReminder(reminder.reminderId,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Reminder canceled successfully", Toast.LENGTH_SHORT).show()
                        reminderVM.deleteReminder(reminder.reminderId)
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

        binding.apply {
            recyclerViewReminders.adapter = adapter
            recyclerViewReminders.layoutManager = LinearLayoutManager(requireContext())
            addReminderButton.setOnClickListener { showSetTimerDialog() }
            deleteMedicationButton.setOnClickListener { deleteMedication() }
            editMedicationButton.setOnClickListener { showEditMedicationDialog() }
        }

        reminderVM.getReminderLD().observe(viewLifecycleOwner) { reminders ->
            adapter.submitList(reminders?.filter { it.medicationId == medicationId })
        }
    }

    private fun observeMedicationDetails() {
        medicationVM.getMedicationLiveData(medicationId).observe(viewLifecycleOwner) { medication ->
            medication?.let {
                binding.apply {
                    medicationName.text = it.medicationName
                    medicationDosage.text = "Dosage: ${it.dosage}"
                    expirationDate.text = "Expiration Date: ${it.expirationDate}"
                    stockLevel.text = "Stock Level: ${it.stockLevel}"

                    it.medicationPhoto?.let { blob ->
                        val bytes = blob.toBytes()
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        medicationPhoto.setImageBitmap(bitmap)
                    } ?: medicationPhoto.setImageResource(R.drawable.ic_medication_placeholder)
                }
            }
        }
    }

    private fun cancelReminder(reminderId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val data = hashMapOf("reminderId" to reminderId)
        FirebaseFunctions.getInstance()
            .getHttpsCallable("cancelReminder")
            .call(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    private fun showSetTimerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_timer, null)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.timePicker)
        val frequencySpinner = dialogView.findViewById<Spinner>(R.id.frequencySpinner)
        val dayPicker = dialogView.findViewById<Spinner>(R.id.dayPicker)
        val datePicker = dialogView.findViewById<DatePicker>(R.id.datePicker)

        setupTimerDialogViews(timePicker, frequencySpinner, dayPicker, datePicker)

        // Set minimum date for date picker to today
        datePicker.minDate = System.currentTimeMillis() - 1000

        AlertDialog.Builder(requireContext())
            .setTitle("Set Reminder")
            .setView(dialogView)
            .setPositiveButton("Set", null) // Set to null initially to prevent automatic dismissal
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener { dialog ->
                    val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                    positiveButton.setOnClickListener {
                        val hour = timePicker.hour
                        val minute = timePicker.minute
                        val selectedFrequency = frequencySpinner.selectedItem.toString()
                        val reminderId = UUID.randomUUID().toString()

                        // Retrieve selected day for "Weekly" frequency
                        val selectedDay = if (selectedFrequency == "Weekly") dayPicker.selectedItem.toString() else null

                        // Retrieve selected date for "Once" frequency
                        val selectedDate = if (selectedFrequency == "Once") {
                            createDateFromPicker(datePicker)
                        } else null

                        // Validate reminder date and time
                        if (!validateReminderTime(selectedDate, hour, minute)) {
                            Toast.makeText(requireContext(), "Invalid reminder time", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            return@setOnClickListener
                        }

                        // Validate required fields based on frequency
                        if (selectedFrequency == "Once" && selectedDate == null) {
                            Toast.makeText(requireContext(), "Invalid date selected", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        if (selectedFrequency == "Weekly" && selectedDay == null) {
                            Toast.makeText(requireContext(), "Invalid day selected", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        // Create Reminder object with all required fields
                        val reminder = Reminder(
                            reminderId = reminderId,
                            date = if (selectedDate != null) {
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)
                            } else null,
                            hour = hour,
                            minute = minute,
                            frequency = selectedFrequency,
                            day = selectedDay,
                            medicationId = medicationId
                        )

                        reminderVM.setReminder(reminder)
                        scheduleReminderNotification(reminder, selectedFrequency)
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }

    private fun showEditReminderDialog(reminder: Reminder) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_timer, null)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.timePicker)
        val frequencySpinner = dialogView.findViewById<Spinner>(R.id.frequencySpinner)
        val dayPicker = dialogView.findViewById<Spinner>(R.id.dayPicker)
        val datePicker = dialogView.findViewById<DatePicker>(R.id.datePicker)

        // Set minimum date
        datePicker.minDate = System.currentTimeMillis() - 1000

        setupTimerDialogViews(timePicker, frequencySpinner, dayPicker, datePicker)
        prefillReminderData(reminder, timePicker, frequencySpinner, dayPicker, datePicker)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Reminder")
            .setView(dialogView)
            .setPositiveButton("Save") { dialogInterface, _ ->
                val hour = timePicker.hour
                val minute = timePicker.minute
                val selectedFrequency = frequencySpinner.selectedItem.toString()

                val selectedDay = if (selectedFrequency == "Weekly") dayPicker.selectedItem.toString() else null
                val selectedDate = if (selectedFrequency == "Once") {
                    Calendar.getInstance().apply {
                        set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                    }.time
                } else null

                // Validate reminder date and time
                if (!validateReminderTime(selectedDate, hour, minute)) {
                    Toast.makeText(requireContext(), "Invalid reminder time", Toast.LENGTH_SHORT).show()
                    dialogInterface.dismiss()
                    return@setPositiveButton
                }

                if (selectedFrequency == "Once" && selectedDate == null) {
                    Toast.makeText(requireContext(), "Invalid date selected", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (selectedFrequency == "Weekly" && selectedDay == null) {
                    Toast.makeText(requireContext(), "Invalid day selected", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val updatedReminder = reminder.copy(
                    date = selectedDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) },
                    hour = hour,
                    minute = minute,
                    frequency = selectedFrequency,
                    day = selectedDay
                )

                reminderVM.setReminder(updatedReminder)
                scheduleReminderNotification(updatedReminder, selectedFrequency)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scheduleReminderNotification(reminder: Reminder, frequency: String) {
        val medicationName = binding.medicationName.text.toString()
        val dosageText = binding.medicationDosage.text.toString().removePrefix("Dosage: ").trim()

        when (frequency) {
            "Once" -> {
                reminder.date?.let { dateStr ->
                    // dateStr is already in yyyy-MM-dd format, no need to format it again
                    ReminderScheduler.scheduleReminderAt(
                        context = requireContext(),
                        reminderId = reminder.reminderId,
                        medicationName = medicationName,
                        medicationId = reminder.medicationId,
                        dosage = dosageText,
                        userId = currentUserId,
                        date = dateStr, // Use the string directly
                        hour = reminder.hour,
                        minute = reminder.minute
                    )
                }
            }
            "Daily" -> {
                ReminderScheduler.scheduleDailyReminder(
                    context = requireContext(),
                    reminderId = reminder.reminderId,
                    medicationName = medicationName,
                    medicationId = reminder.medicationId,
                    dosage = dosageText,
                    userId = currentUserId,
                    hour = reminder.hour,
                    minute = reminder.minute
                )
            }
            "Weekly" -> {
                ReminderScheduler.scheduleWeeklyReminder(
                    context = requireContext(),
                    reminderId = reminder.reminderId,
                    medicationName = medicationName,
                    medicationId = reminder.medicationId,
                    dosage = dosageText,
                    userId = currentUserId,
                    hour = reminder.hour,
                    minute = reminder.minute,
                    day = reminder.day ?: "Sunday"
                )
            }
        }
    }

    private fun setupTimerDialogViews(
        timePicker: TimePicker,
        frequencySpinner: Spinner,
        dayPicker: Spinner,
        datePicker: DatePicker
    ) {
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
    }

    private fun prefillReminderData(
        reminder: Reminder,
        timePicker: TimePicker,
        frequencySpinner: Spinner,
        dayPicker: Spinner,
        datePicker: DatePicker
    ) {
        timePicker.hour = reminder.hour
        timePicker.minute = reminder.minute

        when (reminder.frequency) {
            "Once" -> {
                frequencySpinner.setSelection(0)
                datePicker.visibility = View.VISIBLE
                dayPicker.visibility = View.GONE
                reminder.date?.let { dateStr ->
                    try {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val date = dateFormat.parse(dateStr)
                        val calendar = Calendar.getInstance().apply { time = date }
                        datePicker.updateDate(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        )
                    } catch (e: Exception) {
                        Log.e("prefillReminderData", "Error parsing date: $dateStr", e)
                        val calendar = Calendar.getInstance()
                        datePicker.updateDate(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        )
                    }
                } ?: run {
                    val calendar = Calendar.getInstance()
                    datePicker.updateDate(
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                }
            }
            "Daily" -> {
                frequencySpinner.setSelection(1)
                datePicker.visibility = View.GONE
                dayPicker.visibility = View.GONE
            }
            "Weekly" -> {
                frequencySpinner.setSelection(2)
                datePicker.visibility = View.GONE
                dayPicker.visibility = View.VISIBLE
                val days = resources.getStringArray(R.array.days_of_week)
                val dayIndex = days.indexOf(reminder.day ?: "Sunday")
                dayPicker.setSelection(if (dayIndex >= 0) dayIndex else 0) // Default to first day
            }
            else -> {
                frequencySpinner.setSelection(0) // Default to "Once"
                datePicker.visibility = View.VISIBLE
                dayPicker.visibility = View.GONE
            }
        }
    }

    private fun showEditMedicationDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_medication, null)

        // Non-Editable Fields (TextInputEditText with enabled=false)
        val medicationName = dialogView.findViewById<TextInputEditText>(R.id.medicationName)
        val dosage = dialogView.findViewById<TextInputEditText>(R.id.dosage)

        // Editable Fields
        val expirationDate = dialogView.findViewById<TextInputEditText>(R.id.expirationDate)
        val stockLevel = dialogView.findViewById<TextInputEditText>(R.id.stockLevel)

        val saveButton = dialogView.findViewById<MaterialButton>(R.id.dialogSaveButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.dialogCancelButton)

        // Pre-fill with current medication data
        val currentMedication = medicationVM.get(medicationId)
        currentMedication?.let {
            medicationName.setText(it.medicationName)
            dosage.setText(it.dosage)
            expirationDate.setText(it.expirationDate)
            stockLevel.setText(it.stockLevel.toString())
        }

        // Show Date Picker when expirationDate is clicked
        expirationDate.setOnClickListener {
            showDatePickerDialog(expirationDate)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        saveButton.setOnClickListener {
            updateMedication(dialog, expirationDate, stockLevel)
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun showDatePickerDialog(expirationDate: TextInputEditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                if (selectedDate.before(Calendar.getInstance())) {
                    Toast.makeText(requireContext(), "Date cannot be in the past", Toast.LENGTH_SHORT).show()
                } else {
                    expirationDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time))
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateMedication(
        dialog: AlertDialog,
        expirationDate: TextInputEditText,
        stockLevel: TextInputEditText
    ) {
        val updatedExpirationDate = expirationDate.text.toString().trim()
        val updatedStockLevel = stockLevel.text.toString().trim()

        if (updatedStockLevel.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Stock Level is required.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val stockLevelInt = updatedStockLevel.toIntOrNull()
        if (stockLevelInt == null) {
            Toast.makeText(requireContext(), "Stock Level must be a valid number.", Toast.LENGTH_SHORT).show()
            return
        }

        val existingMedication = medicationVM.get(medicationId)
        if (existingMedication == null) {
            Toast.makeText(requireContext(), "Medication not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedMedication = existingMedication.copy(
            expirationDate = if (updatedExpirationDate.isEmpty()) {
                existingMedication.expirationDate // Retain existing expiration date
            } else {
                updatedExpirationDate
            },
            stockLevel = stockLevelInt
        )

        medicationVM.setMedication(updatedMedication)
        Toast.makeText(requireContext(), "Medication updated successfully.", Toast.LENGTH_SHORT).show()
        dialog.dismiss()
    }


    private fun deleteMedication() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Medication")
            .setMessage("Are you sure you want to delete this medication?")
            .setPositiveButton("Delete") { _, _ ->
                val currentMedication = medicationVM.get(medicationId)
                currentMedication?.let { medication ->
                    val updatedMedication = medication.copy(
                        medicationStatus = "Deleted"
                    )
                    medicationVM.setMedication(updatedMedication)
                    Toast.makeText(
                        requireContext(),
                        "Medication marked as deleted",
                        Toast.LENGTH_SHORT
                    ).show()
                    nav.navigateUp()
                } ?: run {
                    Toast.makeText(
                        requireContext(),
                        "Failed to find medication record",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun validateReminderTime(date: Date?, hour: Int, minute: Int): Boolean {
        if (date == null) return false
        val reminderCalendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentCalendar = Calendar.getInstance()
        return reminderCalendar.after(currentCalendar)
    }

    private fun createDateFromPicker(datePicker: DatePicker): Date? {
        return try {
            Calendar.getInstance().apply {
                set(Calendar.YEAR, datePicker.year)
                set(Calendar.MONTH, datePicker.month)
                set(Calendar.DAY_OF_MONTH, datePicker.dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Adjusts the sizes of various views to make the UI smaller.
     */
    private fun adjustViewSizes() {
        // Initialize ConstraintSet
        val constraintLayout = binding.root as ConstraintLayout
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        // Adjust Medication Card Width to 90% of parent
        constraintSet.constrainPercentWidth(binding.medicationCard.id, 0.7f) // 90% width

        // Set height to wrap_content
        constraintSet.constrainHeight(
            R.id.medicationCard,
            ConstraintSet.WRAP_CONTENT
        )

        // Apply the constraints
        constraintSet.applyTo(constraintLayout)

        binding.root.setPadding(0, 0, 0, 0)

        // Adjust Medication Photo Size
        val medicationPhotoView = binding.medicationPhoto
        val photoParams = medicationPhotoView.layoutParams
        photoParams.width = 80.dpToPx() // Reduced from 120dp to 80dp
        photoParams.height = 80.dpToPx()
        medicationPhotoView.layoutParams = photoParams

        // Adjust Send Reminder Button Size
        val sendReminderButton = binding.sendReminderButton
        val sendButtonParams = sendReminderButton.layoutParams
        sendButtonParams.height = 40.dpToPx() // Reduced height
        sendReminderButton.layoutParams = sendButtonParams

        // Adjust Edit and Delete Medication Buttons
        val editButton = binding.editMedicationButton
        val editButtonParams = editButton.layoutParams
        editButtonParams.height = 40.dpToPx()
        editButton.layoutParams = editButtonParams

        val deleteButton = binding.deleteMedicationButton
        val deleteButtonParams = deleteButton.layoutParams
        deleteButtonParams.height = 40.dpToPx()
        deleteButton.layoutParams = deleteButtonParams

        // Adjust Add Reminder Button Size
        val addReminderButton = binding.addReminderButton
        val addButtonParams = addReminderButton.layoutParams
        addButtonParams.width = 150.dpToPx() // Specific width
        addButtonParams.height = 40.dpToPx()
        addReminderButton.layoutParams = addButtonParams

    }

    /**
     * Adjusts the text sizes of various TextViews and Buttons to make the UI smaller.
     */
    private fun adjustTextSizes() {
        // Reduce Text Sizes
        binding.medicationName.textSize = 16f // From 18sp to 16sp
        binding.medicationDosage.textSize = 14f // From 16sp to 14sp
        binding.expirationDate.textSize = 14f
        binding.stockLevel.textSize = 14f
        binding.instruction.textSize = 14f

        // Adjust Button Text Sizes
        binding.sendReminderButton.textSize = 14f
        binding.editMedicationButton.textSize = 14f
        binding.deleteMedicationButton.textSize = 14f
        binding.addReminderButton.textSize = 14f
    }

    /**
     * Adjusts the margins and padding of various views to make the UI more compact.
     */
    private fun adjustMarginsAndPadding() {
        // Adjust Medication Card Margins
        val medicationCardView = binding.medicationCard
        val cardLayoutParams = medicationCardView.layoutParams as ConstraintLayout.LayoutParams
        cardLayoutParams.setMargins(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx()) // Reduced from 16dp
        medicationCardView.layoutParams = cardLayoutParams

        // Adjust Inner ConstraintLayout Padding
        val innerLayout = binding.medicationCard.findViewById<ConstraintLayout>(R.id.medicationDetailsGroup)
        innerLayout.setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx()) // Reduced from 16dp

        // Adjust Edit/Delete Button Group Margins
        val editDeleteButtonGroup = binding.editDeleteButtonGroup
        val groupLayoutParams = editDeleteButtonGroup.layoutParams as ConstraintLayout.LayoutParams
        groupLayoutParams.setMargins(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
        editDeleteButtonGroup.layoutParams = groupLayoutParams

        // Adjust RecyclerView Margins
        val recyclerView = binding.recyclerViewReminders
        val recyclerParams = recyclerView.layoutParams as ConstraintLayout.LayoutParams
        recyclerParams.setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
        recyclerView.layoutParams = recyclerParams

        // Adjust Add Reminder Button Margins
        val addReminderButton = binding.addReminderButton
        val addButtonParams = addReminderButton.layoutParams as ConstraintLayout.LayoutParams
        addButtonParams.setMargins(0, 8.dpToPx(), 0, 8.dpToPx())
        addReminderButton.layoutParams = addButtonParams
    }

    /**
     * Extension function to convert dp to pixels.
     */
    private fun Int.dpToPx(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            Resources.getSystem().displayMetrics
        ).toInt()
    }

    companion object {
        private const val ARG_MEDICATION_ID = "medicationId"
        private const val TAG = "MedicationDetailsFragment"

        fun newInstance(medicationId: String) = CaregiverMonitorMedicationDetailsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_MEDICATION_ID, medicationId)
            }
        }
    }
}
