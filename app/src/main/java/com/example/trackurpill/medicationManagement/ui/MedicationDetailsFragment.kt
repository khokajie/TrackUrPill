package com.example.trackurpill.medicationManagement.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.R
import com.example.trackurpill.data.MedicationInteraction
import com.example.trackurpill.data.Reminder
import com.example.trackurpill.databinding.FragmentMedicationDetailsBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.medicationManagement.data.ReminderViewModel
import com.example.trackurpill.medicationManagement.util.InteractionsAdapter
import com.example.trackurpill.medicationManagement.util.ReminderAdapter
import com.example.trackurpill.userManagement.data.LoggedInUserViewModel
import com.example.trackurpill.util.ReminderScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.functions.FirebaseFunctions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MedicationDetailsFragment : Fragment() {

    private lateinit var binding: FragmentMedicationDetailsBinding
    private val nav by lazy { findNavController() }
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private val loggedInUserVM: LoggedInUserViewModel by activityViewModels()
    private val reminderVM: ReminderViewModel by activityViewModels()
    private lateinit var medicationId: String
    private lateinit var currentUserId: String

    private var medicationPhotoBlob: Blob? = null // To store the compressed image as Blob
    private lateinit var photoURI: Uri
    private var currentDialogView: View? = null

    private val captureImageLauncherForEditPhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            currentDialogView?.let { dialogView ->
                if (success) {
                    handleCapturedImageForEdit(dialogView)
                } else {
                    Toast.makeText(requireContext(), "Failed to capture image", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val getMedicationImageForEditPhoto =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            currentDialogView?.let { dialogView ->
                uri?.let {
                    processSelectedImageForEdit(it, dialogView)
                }
            }
        }

    // New: Capture Image for Viewing Interactions (if needed)
    // If you plan to allow interactions images, else skip.

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMedicationDetailsBinding.inflate(inflater, container, false)

        medicationId = arguments?.getString("medicationId") ?: ""
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid.toString()

        // Fetch the user role
        loggedInUserVM.fetchUserRole()

        setupUI()
        observeMedicationDetails()

        return binding.root
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
            viewInteractionsButton.setOnClickListener { showInteractions() } // New: Set click listener
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
                    instruction.text = "Instruction: ${it.instruction}"

                    // Display the photo if available
                    it.medicationPhoto?.let { blob ->
                        val bytes = blob.toBytes()
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        medicationPhoto.setImageBitmap(bitmap)
                    } ?: medicationPhoto.setImageResource(R.drawable.ic_medication_placeholder)

                    // Show or hide interactions button if needed
                    // The interactions button is always visible; enable/disable based on interactions
                    if (it.interactions.isNotEmpty()) {
                        binding.viewInteractionsButton.isEnabled = true
                        binding.viewInteractionsButton.alpha = 1.0f
                    } else {
                        binding.viewInteractionsButton.isEnabled = false
                        binding.viewInteractionsButton.alpha = 0.5f
                    }
                }
            }
        }
    }

    // New: Function to handle interactions button click
    private fun showInteractions() {
        val medication = medicationVM.get(medicationId)
        if (medication != null) {
            val interactions = medication.interactions
            if (interactions.isNotEmpty()) {
                showInteractionsDialog(interactions)
            } else {
                Toast.makeText(requireContext(), "No interactions found.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Medication not found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInteractionsDialog(interactions: List<MedicationInteraction>) {
        // Inflate the custom dialog layout
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_medication_interaction, null)

        // Initialize the RecyclerView
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerViewInteractions)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = InteractionsAdapter(interactions)

        // Initialize the OK button
        val okButton = dialogView.findViewById<MaterialButton>(R.id.dialogOkButton)

        // Build the dialog
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false) // Prevent dismissal on outside touch
            .create()

        // Set click listener for the OK button to dismiss the dialog
        okButton.setOnClickListener {
            dialog.dismiss()
        }

        // Show the dialog
        dialog.show()
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

                        if (selectedFrequency == "Once"){
                            // Validate reminder date and time
                            if (!validateReminderTime(selectedDate, hour, minute)) {
                                Toast.makeText(requireContext(), "Invalid reminder time", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
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

                if (selectedFrequency == "Once"){
                    // Validate reminder date and time
                    if (!validateReminderTime(selectedDate, hour, minute)) {
                        Toast.makeText(requireContext(), "Invalid reminder time", Toast.LENGTH_SHORT).show()
                        dialogInterface.dismiss()
                        return@setPositiveButton
                    }
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

        currentDialogView = dialogView

        // Non-Editable Fields
        val medicationName = dialogView.findViewById<TextInputEditText>(R.id.medicationName)
        val dosage = dialogView.findViewById<TextInputEditText>(R.id.dosage)

        // Editable Fields
        val expirationDate = dialogView.findViewById<TextInputEditText>(R.id.expirationDate)
        val stockLevel = dialogView.findViewById<TextInputEditText>(R.id.stockLevel)
        val instructions = dialogView.findViewById<TextInputEditText>(R.id.instructions)

        // Photo Editing Views
        val medicationPhoto = dialogView.findViewById<ImageView>(R.id.medicationPhoto)
        val selectPhotoButton = dialogView.findViewById<Button>(R.id.selectPhotoButton)

        val saveButton = dialogView.findViewById<MaterialButton>(R.id.dialogSaveButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.dialogCancelButton)

        // Pre-fill with current medication data
        val currentMedication = medicationVM.get(medicationId)
        currentMedication?.let {
            medicationName.setText(it.medicationName)
            dosage.setText(it.dosage)
            expirationDate.setText(it.expirationDate)
            stockLevel.setText(it.stockLevel.toString())
            instructions.setText(it.instruction)

            it.medicationPhoto?.let { blob ->
                val bytes = blob.toBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                medicationPhoto.setImageBitmap(bitmap)
            } ?: medicationPhoto.setImageResource(R.drawable.ic_medication_placeholder)
        }

        // Set up photo selection button
        selectPhotoButton.setOnClickListener {
            showImagePickerOptionsForEditPhoto(dialogView)
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
            val updatedExpirationDate = expirationDate.text.toString().trim()
            val updatedStockLevel = stockLevel.text.toString().trim()
            val updatedInstructions = instructions.text.toString().trim()

            if (!validateEditInputs(dialogView, updatedExpirationDate, updatedStockLevel, updatedInstructions)) {
                return@setOnClickListener
            }

            updateMedication(dialog, updatedExpirationDate, updatedStockLevel, updatedInstructions)
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            currentDialogView = null
        }

        dialog.show()
    }

    private fun showImagePickerOptionsForEditPhoto(dialogView: View) {
        val options = arrayOf("Take Photo", "Choose from Gallery")

        AlertDialog.Builder(requireContext())
            .setTitle("Select Option")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> onTakePhotoOptionSelected()  // <-- Use your new function
                    1 -> pickImageFromGalleryForEditPhoto()
                }
            }
            .show()
    }

    private fun onTakePhotoOptionSelected() {
        // Check if we already have the camera permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is already granted; proceed with taking photo
            captureImageFromCameraForEditPhoto()
        } else {
            // Request the permission
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // The user granted the permission.
                // You can now safely launch the camera.
                captureImageFromCameraForEditPhoto()
            } else {
                // The user denied the permission.
                Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }


    private fun captureImageFromCameraForEditPhoto() {
        val photoFile = createImageFileForEdit()
        photoURI = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            photoFile
        )
        captureImageLauncherForEditPhoto.launch(photoURI)
    }

    private fun pickImageFromGalleryForEditPhoto() {
        getMedicationImageForEditPhoto.launch("image/*")
    }

    private fun handleCapturedImageForEdit(dialogView: View) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(photoURI)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)

            // Compress the image
            val compressedBitmap = compressBitmap(originalBitmap, 800, 800)
            val byteArrayOutputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
            val compressedByteArray = byteArrayOutputStream.toByteArray()

            medicationPhotoBlob = Blob.fromBytes(compressedByteArray)

            // Display the compressed image in the ImageView
            val medicationPhoto = dialogView.findViewById<ImageView>(R.id.medicationPhoto)
            medicationPhoto.apply {
                visibility = View.VISIBLE
                setImageBitmap(compressedBitmap)
            }

            // Update the "Change Photo" button text
            val selectPhotoButton = dialogView.findViewById<Button>(R.id.selectPhotoButton)
            selectPhotoButton.text = "Change Photo"
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun processSelectedImageForEdit(uri: Uri, dialogView: View) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)

            // Compress the image
            val compressedBitmap = compressBitmap(originalBitmap, 800, 800)
            val byteArrayOutputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
            val compressedByteArray = byteArrayOutputStream.toByteArray()

            medicationPhotoBlob = Blob.fromBytes(compressedByteArray)

            // Display the compressed image in the ImageView
            val medicationPhoto = dialogView.findViewById<ImageView>(R.id.medicationPhoto)
            medicationPhoto.apply {
                visibility = View.VISIBLE
                setImageBitmap(compressedBitmap)
            }

            // Update the "Change Photo" button text
            val selectPhotoButton = dialogView.findViewById<Button>(R.id.selectPhotoButton)
            selectPhotoButton.text = "Change Photo"
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun createImageFileForEdit(): File {
        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? =
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    /**
     * Compress a Bitmap to the specified width and height.
     */
    private fun compressBitmap(original: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = original.width
        val height = original.height

        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxWidth
            newHeight = (maxWidth / aspectRatio).toInt()
        } else {
            newHeight = maxHeight
            newWidth = (maxHeight * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
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
        expirationDate: String,
        stockLevel: String,
        instructions: String
    ) {
        val existingMedication = medicationVM.get(medicationId)
        if (existingMedication == null) {
            Toast.makeText(requireContext(), "Medication not found.", Toast.LENGTH_SHORT).show()
            return
        }

        // If a new photo is selected, store the Blob directly; otherwise, retain existing
        val updatedMedication = existingMedication.copy(
            expirationDate = if (expirationDate.isEmpty()) {
                existingMedication.expirationDate // Retain existing expiration date
            } else {
                expirationDate
            },
            stockLevel = stockLevel.toInt(),
            instruction = instructions,
            medicationPhoto = medicationPhotoBlob ?: existingMedication.medicationPhoto // Use new Blob or retain existing
        )

        medicationVM.setMedication(updatedMedication)
        Toast.makeText(
            requireContext(),
            "Medication updated successfully.",
            Toast.LENGTH_SHORT
        ).show()
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

    private fun validateEditInputs(
        dialogView: View,
        expirationDate: String,
        stockLevel: String,
        instructions: String
    ): Boolean {
        var isValid = true

        // Validate Expiration Date
        val expirationDateLayout = dialogView.findViewById<TextInputLayout>(R.id.expirationDateLayout)
        if (expirationDate.isNotEmpty()) {
            try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(expirationDate)
                if (date.before(Calendar.getInstance().time)) {
                    expirationDateLayout.error = "Expiration date cannot be in the past"
                    isValid = false
                } else {
                    expirationDateLayout.error = null
                }
            } catch (e: Exception) {
                expirationDateLayout.error = "Invalid date format. Use yyyy-MM-dd"
                isValid = false
            }
        } else {
            expirationDateLayout.error = null
        }

        // Validate Stock Level
        val stockLevelLayout = dialogView.findViewById<TextInputLayout>(R.id.stockLevelLayout)
        val stockLevelInt = stockLevel.toIntOrNull()
        if (stockLevel.isEmpty() || stockLevelInt == null || stockLevelInt < 0) {
            stockLevelLayout.error = "Stock level must be a positive number"
            isValid = false
        } else {
            stockLevelLayout.error = null
        }

        // Validate Instructions
        val instructionsLayout = dialogView.findViewById<TextInputLayout>(R.id.txtLayoutInstructions)
        if (instructions.isEmpty()) {
            instructionsLayout.error = "Instructions cannot be empty"
            isValid = false
        } else {
            instructionsLayout.error = null
        }

        return isValid
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

    companion object {
        private const val TAG = "MedicationDetailsFragment"
    }

}

