// AddPatientMedicationFragment.kt
package com.example.trackurpill.medicationManagement.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.trackurpill.R
import com.example.trackurpill.api.*
import com.example.trackurpill.data.Medication
import com.example.trackurpill.databinding.FragmentAddPatientMedicationBinding
import com.example.trackurpill.medicationManagement.data.AdverseEventViewModel
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.notification.data.NotificationViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import com.example.trackurpill.api.data.GeminiRequest
import com.example.trackurpill.api.data.Content
import com.example.trackurpill.api.data.Part
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.Observer

class AddPatientMedicationFragment : Fragment() {

    private lateinit var binding: FragmentAddPatientMedicationBinding
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private val notificationVM: NotificationViewModel by activityViewModels()
    private val adverseEventVM: AdverseEventViewModel by viewModels()
    private val geminiViewModel: GeminiViewModel by viewModels()
    private val nav by lazy { findNavController() }
    private var medicationPhotoBlob: Blob? = null // Blob for storing the image
    private var patientId: String? = null // To distinguish between caregiver and patient views
    private lateinit var photoURI: Uri
    private var loadingDialog: AlertDialog? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "AddPatientMedication"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentAddPatientMedicationBinding.inflate(inflater, container, false)

        requestPermissionsIfNecessary()

        // Retrieve the optional patientId argument
        patientId = arguments?.getString("patientId")

        // Determine whose medications to load
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId

        // Handle photo upload (no OCR)
        binding.clickToAdd.setOnClickListener { showImagePickerOptionsForPhoto() }
        binding.photoContainer.setOnClickListener { showImagePickerOptionsForPhoto() }

        // Handle scan medication info (with OCR)
        binding.btnScanMedicationInfo.setOnClickListener { showImagePickerOptionsForOCR() }

        // Handle expiration date picker
        binding.txtExpirationDate.setOnClickListener { showDatePicker() }

        // Handle Add Medication button click
        binding.btnAddMedication.setOnClickListener {
            val medicationName = binding.txtMedicationName.text.toString().trim()
            val dosage = binding.txtDosage.text.toString().trim()
            val expirationDateString = binding.txtExpirationDate.text.toString().trim()
            val stockLevel = binding.txtStockLevel.text.toString().trim()
            val instructions = binding.txtInstructions.text.toString().trim()

            if (!validateInputs(
                    medicationName,
                    dosage,
                    expirationDateString,
                    targetUserId.toString(),
                    stockLevel,
                    instructions
                )
            ) return@setOnClickListener

            // Create Medication object
            val medication = Medication(
                medicationId = UUID.randomUUID().toString(),
                medicationName = medicationName,
                dosage = dosage,
                expirationDate = expirationDateString,
                stockLevel = stockLevel.toInt(),
                instruction = instructions,
                medicationPhoto = medicationPhotoBlob,
                medicationStatus = "Active",
                userId = targetUserId.toString()
            )

            // Save medication
            medicationVM.setMedication(medication)

            // Retrieve all medications for the user including the newly added one
            val allMedications = medicationVM.getAllByUser(targetUserId.toString()).toMutableList().apply {
                add(
                    Medication(
                        medicationId = medication.medicationId,
                        medicationName = medication.medicationName,
                        dosage = medication.dosage,
                        expirationDate = medication.expirationDate,
                        stockLevel = medication.stockLevel,
                        instruction = medication.instruction,
                        userId = medication.userId
                    )
                )
            }

            // 1. Construct the Prompt
            val medicationNames = allMedications.joinToString(", ") { it.medicationName }
            val promptText = "Check the interactions between the following medications: $medicationNames."

            // 2. Create Parts with the Prompt
            val partsList = listOf(
                Part(text = promptText)
            )

            // 3. Wrap Parts into Content
            val contentsList = listOf(
                Content(parts = partsList)
            )

            // 4. Prepare GeminiRequest with List<Content>
            val geminiRequest = GeminiRequest(contents = contentsList)


            // Initiate API call
            geminiViewModel.generateContent(geminiRequest, "AIzaSyC_5oXR_z3oL2AxPhmS6dwIFDYv-2WdZOU")

            // Show loading dialog
            /*loadingDialog = AlertDialog.Builder(requireContext())
                .setTitle("Checking Drug Safety")
                .setMessage("Loading...")
                .setCancelable(false)
                .create()
            loadingDialog?.show()

            // Launch a coroutine to fetch data and show the dialog
            viewLifecycleOwner.lifecycleScope.launch {
                showAdverseEventDialog()
            }*/

            // Observe ViewModel LiveData
            observeViewModel()
        }

        return binding.root
    }



    private fun observeViewModel() {
        geminiViewModel.geminiResponse.observe(viewLifecycleOwner) { response ->
            // Hide loading indicator
            //binding.progressBar.visibility = View.GONE

            if (response != null && response.candidates.isNotEmpty()) {
                val generatedText = response.candidates.joinToString(separator = "\n") { candidate ->
                    candidate.content.parts.joinToString(separator = " ") { it.text }
                }
                // Handle the generated text as needed
                showGeneratedContent(generatedText)

                // Inform the user
                Toast.makeText(requireContext(), "Content Generated Successfully!", Toast.LENGTH_SHORT).show()
            } else if (response != null) {
                Toast.makeText(requireContext(), "No content generated.", Toast.LENGTH_SHORT).show()
                nav.navigateUp()
            } else {
                Toast.makeText(requireContext(), "Failed to generate content", Toast.LENGTH_SHORT).show()
                nav.navigateUp()
            }
        }

        geminiViewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            // Hide loading indicator
            //binding.progressBar.visibility = View.GONE

            errorMsg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                nav.navigateUp()
            }
        }
    }


    private fun showGeneratedContent(content: String) {
        // Implement how you want to display the generated content
        // For example, show in a dialog or populate a TextView
        AlertDialog.Builder(requireContext())
            .setTitle("Generated Content")
            .setMessage(content)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                nav.navigateUp()
            }
            .setCancelable(false)
            .show()
    }

    /*private suspend fun showAdverseEventDialog() {
        try {
            val medicationName = binding.txtMedicationName.text.toString().trim().uppercase(Locale.getDefault())
            val info = adverseEventVM.getHealthInfo(medicationName)

            loadingDialog?.dismiss()
            if (info.isNotEmpty()) {
                showCustomAdverseEventDialog(info)
            } else {
                showNoAdverseEventsDialog()
            }
        } catch (e: Exception) {
            loadingDialog?.dismiss()
            Toast.makeText(requireContext(), "Error checking drug information", Toast.LENGTH_SHORT).show()
            nav.navigateUp()
            Log.e(TAG, "Error in showAdverseEventDialog", e)
        }
    }

    private fun showCustomAdverseEventDialog(info: List<DetailedHealthInfo>) {
        // Inflate custom dialog layout
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_adverse_events, null)
        val eventsContainer = dialogView.findViewById<LinearLayout>(R.id.eventsContainer)
        val btnUnderstood = dialogView.findViewById<Button>(R.id.btnUnderstood)

        // Populate the events
        populateAdverseEvents(eventsContainer, info)

        // Build and show the dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnUnderstood.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(requireContext(), "Medication Added", Toast.LENGTH_SHORT).show()
            nav.navigateUp()
        }

        dialog.show()
    }

    private fun showNoAdverseEventsDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("No Adverse Events Found")
            .setMessage("No known adverse events were found for this medication.")
            .setPositiveButton("OK") { _, _ ->
                Toast.makeText(requireContext(), "Medication Added", Toast.LENGTH_SHORT).show()
                nav.navigateUp()
            }
            .setCancelable(false)
            .create()
        dialog.show()
    }

    private fun populateAdverseEvents(container: LinearLayout, info: List<DetailedHealthInfo>) {
        try {
            info.forEach { healthInfo ->
                val itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_adverse_event, container, false)

                val imgSeverityIcon = itemView.findViewById<ImageView>(R.id.imgSeverityIcon)
                val txtAdverseEvent = itemView.findViewById<TextView>(R.id.txtAdverseEvent)
                val txtEventDetails = itemView.findViewById<TextView>(R.id.txtEventDetails)

                // Set the adverse event name
                txtAdverseEvent.text = healthInfo.adverseEvent

                // Set the severity icon and color
                val (iconRes, colorRes) = getSeverityIconAndColor(healthInfo.severity)
                imgSeverityIcon.setImageResource(iconRes)
                imgSeverityIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), colorRes)


                // Set the event details
                val detailsBuilder = StringBuilder()
                if (!healthInfo.summary.isNullOrEmpty()) {
                    detailsBuilder.append(healthInfo.summary).append("\n\n")
                }
                if (!healthInfo.recommendations.isNullOrEmpty()) {
                    detailsBuilder.append("Recommendations:\n")
                    healthInfo.recommendations.take(3).forEach {
                        detailsBuilder.append("• $it\n")
                    }
                }
                txtEventDetails.text = detailsBuilder.toString()

                // Add the item to the container
                container.addView(itemView)
            }
            Log.d(TAG, "populateAdverseEvents completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in populateAdverseEvents", e)
        }
    }

    private fun getSeverityIconAndColor(severity: String?): Pair<Int, Int> {
        return when (severity?.toLowerCase(Locale.ROOT)) {
            "high" -> Pair(R.drawable.ic_warning, R.color.severityHigh)
            "medium" -> Pair(R.drawable.ic_warning, R.color.severityMedium)
            "low" -> Pair(R.drawable.ic_warning, R.color.severityLow)
            else -> Pair(R.drawable.ic_warning, R.color.severityUnknown)
        }
    }*/

    override fun onDestroyView() {
        loadingDialog?.dismiss()
        super.onDestroyView()
    }


    private fun requestPermissionsIfNecessary() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = permissions.zip(grantResults.toTypedArray())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }

            if (deniedPermissions.isNotEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Permissions denied: $deniedPermissions",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Functions for selecting medication photo (no OCR)
    private fun showImagePickerOptionsForPhoto() {
        val options = arrayOf("Take Photo", "Choose from Gallery")

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Select Option")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> captureImageFromCameraForPhoto()
                1 -> pickImageFromGalleryForPhoto()
            }
        }
        builder.show()
    }

    private fun captureImageFromCameraForPhoto() {
        val photoFile = createImageFile()
        photoURI = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            photoFile
        )
        captureImageLauncherForPhoto.launch(photoURI)
    }

    private fun pickImageFromGalleryForPhoto() {
        getMedicationImageForPhoto.launch("image/*")
    }

    private val captureImageLauncherForPhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                handleCapturedImage()
                // Do not perform OCR here
            } else {
                Toast.makeText(requireContext(), "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }

    private val getMedicationImageForPhoto =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                processSelectedImage(it)
                // Do not perform OCR here
            }
        }

    // Functions for scanning medication info (with OCR)
    private fun showImagePickerOptionsForOCR() {
        val options = arrayOf("Take Photo", "Choose from Gallery")

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Select Option")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> captureImageFromCameraForOCR()
                1 -> pickImageFromGalleryForOCR()
            }
        }
        builder.show()
    }

    private fun captureImageFromCameraForOCR() {
        val photoFile = createImageFile()
        photoURI = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            photoFile
        )
        captureImageLauncherForOCR.launch(photoURI)
    }

    private fun pickImageFromGalleryForOCR() {
        getMedicationImageForOCR.launch("image/*")
    }

    private val captureImageLauncherForOCR =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                processImageForOCR(photoURI)
            } else {
                Toast.makeText(requireContext(), "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }

    private val getMedicationImageForOCR =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                processImageForOCR(it)
            }
        }

    private fun processImageForOCR(uri: Uri) {
        // Optionally, display the image if desired
        // Then perform OCR
        performOCR(uri)
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
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

    private fun handleCapturedImage() {
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
            binding.imgMedicationPhoto.apply {
                visibility = View.VISIBLE
                setImageBitmap(compressedBitmap)
            }

            // Hide the placeholder icon
            binding.iconAddPhoto.visibility = View.GONE
            binding.clickToAdd.text = "Change photo"
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun processSelectedImage(uri: Uri) {
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
            binding.imgMedicationPhoto.apply {
                visibility = View.VISIBLE
                setImageBitmap(compressedBitmap)
            }

            // Hide the placeholder icon
            binding.iconAddPhoto.visibility = View.GONE
            binding.clickToAdd.text = "Change photo"
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
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

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val formattedDate = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                binding.txtExpirationDate.setText(formattedDate) // Set formatted string
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun validateInputs(
        medicationName: String,
        dosage: String,
        expirationDate: String,
        currentUserId: String,
        stockLevel: String,
        instructions: String,

    ): Boolean {
        var isValid = true

        // Check if medication name is empty
        if (medicationName.isEmpty()) {
            binding.txtLayoutMedicationName.error = "Medication name cannot be empty"
            isValid = false
        } else {
            // Check if medication name already exists for the current user
            val existingMedications = medicationVM.getAllByUser(currentUserId)
            val isNameExists = existingMedications.any { it.medicationName.equals(medicationName, ignoreCase = true) }

            if (isNameExists) {
                binding.txtLayoutMedicationName.error = "This medication already exists"
                isValid = false
            } else {
                binding.txtLayoutMedicationName.error = null
            }
        }

        // Check if dosage is empty
        if (dosage.isEmpty()) {
            binding.txtLayoutDosage.error = "Dosage cannot be empty"
            isValid = false
        } else {
            binding.txtLayoutDosage.error = null
        }

        // Expiration date can be null or empty, no validation required
        if (expirationDate.isNotEmpty()) {
            try {
                val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(binding.txtExpirationDate.text.toString())
                if (date.before(Calendar.getInstance().time)) {
                    binding.txtLayoutExpirationDate.error = "Expiration date cannot be in the past"
                    isValid = false
                } else {
                    binding.txtLayoutExpirationDate.error = null
                }
            } catch (e: Exception) {
                binding.txtLayoutExpirationDate.error = "Invalid date format. Use dd/MM/yyyy"
                isValid = false
            }
        } else {
            binding.txtLayoutExpirationDate.error = null
        }

        // Check if stock level is a valid positive number
        if (stockLevel.isEmpty() || stockLevel.toIntOrNull() == null || stockLevel.toInt() < 0) {
            binding.txtLayoutStockLevel.error = "Stock level must be a positive number"
            isValid = false
        } else {
            binding.txtLayoutStockLevel.error = null
        }

        // Instructions cannot be empty
        if (instructions.isEmpty()) {
            binding.txtLayoutInstructions.error = "Instructions cannot be empty"
            isValid = false
        } else {
            binding.txtLayoutInstructions.error = null
        }

        return isValid
    }


    /**
     * Performs OCR on the provided image URI and updates the form fields.
     */
    private fun performOCR(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)

            // Create InputImage
            val image = InputImage.fromBitmap(originalBitmap, 0)

            // Initialize Text Recognizer
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    extractMedicationInfo(visionText)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to recognize text", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to perform OCR", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun extractMedicationInfo(visionText: Text) {
        val fullText = visionText.text
        Log.d("OCR Full Text", "\n$fullText")

        // Initialize variables to hold extracted information
        var medicationName: String? = null
        var dosage: String? = null
        var instructions: String? = null
        var expirationDate: String? = null
        var stockLevel: Int? = null // Added stockLevel variable

        // Split the recognized text into lines
        val lines = fullText.split("\n")

        // Define regex patterns
        // Medication Name: text ending with "MG" or "ML"
        val medicationNameRegex = Regex(
            """^([A-Za-z\s\-]+?)\s+\d+(mg|ml)""",
            RegexOption.IGNORE_CASE
        )
        // Dosage Extraction: numbers followed by "tablet" or "capsule"
        val dosageRegex = Regex(
            """\b(\d+(\.\d+)?)\s*(tablet|capsule|tablets|capsules)\b""",
            RegexOption.IGNORE_CASE
        )
        // Dosage Instruction Extraction: text within parentheses following dosage
        val dosageInstructionRegex = Regex(
            """\((\d+\s+TIMES\s+PER\s+DAY)\)""",
            RegexOption.IGNORE_CASE
        )
        // Instructions Extraction: text within parentheses OR text starting with instruction verbs
        val instructionsRegex = Regex(
            """\(([^)]+)\)|\b(?:take|dose|apply|use|instill|inhale|drink|swallow|insert)\b\s+([^.\\n]+)""",
            RegexOption.IGNORE_CASE
        )
        // Expiration Date Extraction: prioritize "Expiry" over "Date"
        val expiryDateRegex = Regex(
            """\b(expiry|exp|expires?|expiration)\s*[:\-]?\s*(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b.*""",
            RegexOption.IGNORE_CASE
        )
        val dateRegex = Regex(
            """\bdate\s*[:\-]?\s*(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b.*""",
            RegexOption.IGNORE_CASE
        )
        // Stock Level Extraction: Match lines starting with "Qty:" followed by the quantity
        val stockLevelRegex = Regex(
            """\b(?:qty|quantity)[:\-]?\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )

        // Define a list of keywords to identify non-medication lines
        val nonMedicationKeywords = listOf(
            "clinic",
            "medical centre",
            "batch",
            "lot no",
            "manufacturer",
            "address",
            "phone",
            "fax",
            "website",
            "null",
            "shot",
            "test",
            "software",
            "ulations" // captures partial words like "Regulations"
        )

        // Iterate through lines to extract information
        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            val lowerLine = trimmedLine.lowercase(Locale.getDefault())

            // Skip lines that contain non-medication related keywords
            if (nonMedicationKeywords.any { lowerLine.contains(it) }) {
                Log.d("ExtractMedicationInfo", "Skipping non-medication line: $trimmedLine")
                continue
            }

            // Extract Expiration Date (prioritize "Expiry" over "Date")
            val expiryMatch = expiryDateRegex.find(line)
            if (expiryMatch != null) {
                val day = expiryMatch.groups[2]?.value
                val month = expiryMatch.groups[3]?.value
                val year = expiryMatch.groups[4]?.value
                val formattedDate = formatCompleteDate(day, month, year)
                if (formattedDate != null) {
                    expirationDate = formattedDate
                    Log.d("ExtractMedicationInfo", "Found Expiry Date: $formattedDate")
                }
                continue
            }

            // Extract Expiration Date from "Date" if not set by "Expiry"
            if (expirationDate == null) {
                val dateMatch = dateRegex.find(line)
                if (dateMatch != null) {
                    val day = dateMatch.groups[1]?.value
                    val month = dateMatch.groups[2]?.value
                    val year = dateMatch.groups[3]?.value
                    val formattedDate = formatCompleteDate(day, month, year)
                    if (formattedDate != null) {
                        expirationDate = formattedDate
                        Log.d("ExtractMedicationInfo", "Found Date as Expiry Date: $formattedDate")
                    }
                    continue
                }
            }

            // Extract Medication Name
            if (medicationName == null) {
                val medNameMatch = medicationNameRegex.find(line)
                if (medNameMatch != null) {
                    val name = medNameMatch.groups[1]?.value?.trim()
                    if (name != null && !nonMedicationKeywords.any {
                            name.lowercase(Locale.getDefault()).contains(it)
                        }) {
                        medicationName = name
                        Log.d("ExtractMedicationInfo", "Found Medication Name: $medicationName")
                    }
                    continue
                }
            }

            // Extract Dosage
            if (dosage == null) {
                val dosageMatch = dosageRegex.find(line)
                if (dosageMatch != null) {
                    dosage = "${dosageMatch.groups[1]?.value?.trim()} ${dosageMatch.groups[3]?.value?.trim()}"
                    Log.d("ExtractMedicationInfo", "Found Dosage: $dosage")
                    continue
                }
            }

            // Extract Instructions
            if (instructions == null) {
                val instructionsMatch = instructionsRegex.find(line)
                if (instructionsMatch != null) {
                    val parenthetical = instructionsMatch.groups[1]?.value?.trim()
                    val verbInstruction = instructionsMatch.groups[2]?.value?.trim()
                    instructions = parenthetical ?: verbInstruction
                    Log.d("ExtractMedicationInfo", "Found Instructions: ${instructions ?: "null"}")
                    continue
                }
            }

            // Extract Stock Level
            if (stockLevel == null) {
                val stockLevelMatch = stockLevelRegex.find(line)
                if (stockLevelMatch != null) {
                    val qtyString = stockLevelMatch.groups[1]?.value
                    val qty = qtyString?.toIntOrNull()
                    if (qty != null) {
                        stockLevel = qty
                        Log.d("ExtractMedicationInfo", "Found Stock Level: $stockLevel")
                    }
                    continue
                }
            }
        }

        // Final Logging
        Log.d("ExtractMedicationInfo", "Final Extracted Information:")
        Log.d("ExtractMedicationInfo", "Medication Name: $medicationName")
        Log.d("ExtractMedicationInfo", "Dosage: $dosage")
        Log.d("ExtractMedicationInfo", "Expiration Date: $expirationDate")
        Log.d("ExtractMedicationInfo", "Instructions: $instructions")
        Log.d("ExtractMedicationInfo", "Stock Level: $stockLevel")

        // Update UI with extracted information
        requireActivity().runOnUiThread {
            medicationName?.let {
                binding.txtMedicationName.setText(it)
                binding.txtMedicationName.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.auto_filled_background)
            }
            dosage?.let {
                binding.txtDosage.setText(it)
                binding.txtDosage.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.auto_filled_background)
            }
            expirationDate?.let {
                binding.txtExpirationDate.setText(it)
                binding.txtExpirationDate.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.auto_filled_background)
            }
            instructions?.let {
                binding.txtInstructions.setText(it)
                binding.txtInstructions.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.auto_filled_background)
            }
            stockLevel?.let {
                binding.txtStockLevel.setText(it.toString())
                binding.txtStockLevel.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.auto_filled_background)
            }
        }
    }


    private fun formatCompleteDate(day: String?, month: String?, year: String?): String? {
        return if (day != null && month != null && year != null) {
            val formattedYear = formatYear(year)
            if (formattedYear != null) {
                "$day/$month/$formattedYear"
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun formatYear(year: String?): String? {
        return year?.let {
            when (it.length) {
                2 -> {
                    val num = it.toIntOrNull()
                    if (num != null) {
                        "20${it.padStart(2, '0')}"
                    } else {
                        null
                    }
                }
                4 -> it
                else -> null
            }
        }
    }

}
