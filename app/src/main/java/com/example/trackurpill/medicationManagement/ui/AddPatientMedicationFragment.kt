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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.trackurpill.R
import com.example.trackurpill.data.Medication
import com.example.trackurpill.databinding.FragmentAddPatientMedicationBinding
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class AddPatientMedicationFragment : Fragment() {

    private lateinit var binding: FragmentAddPatientMedicationBinding
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private val nav by lazy { findNavController() }
    private var medicationPhotoBlob: Blob? = null // Blob for storing the image
    private var patientId: String? = null // To distinguish between caregiver and patient views
    private lateinit var photoURI: Uri
    private lateinit var ocrImageUri: Uri
    private val REQUEST_OCR_PERMISSIONS = 2001

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
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

        // Handle photo upload
        binding.clickToAdd.setOnClickListener { showImagePickerOptions() }
        binding.photoContainer.setOnClickListener { showImagePickerOptions() }

        // Handle expiration date picker
        binding.txtExpirationDate.setOnClickListener { showDatePicker() }

        // Handle Add Medication button click
        binding.btnAddMedication.setOnClickListener {
            val medicationName = binding.txtMedicationName.text.toString().trim()
            val dosage = binding.txtDosage.text.toString().trim()
            val expirationDateString = binding.txtExpirationDate.text.toString().trim()
            val stockLevel = binding.txtStockLevel.text.toString().trim()

            if (!validateInputs(medicationName, dosage, expirationDateString, stockLevel)) return@setOnClickListener

            // Create Medication object
            val medication = Medication(
                medicationId = UUID.randomUUID().toString(),
                medicationName = medicationName,
                dosage = dosage,
                expirationDate = expirationDateString, // Store the string directly
                stockLevel = stockLevel.toInt(),
                medicationPhoto = medicationPhotoBlob,
                medicationStatus = "Active",
                userId = targetUserId.toString()
            )

            // Save medication
            medicationVM.setMedication(medication)
            Toast.makeText(requireContext(), "Medication Added", Toast.LENGTH_SHORT).show()
            nav.navigateUp()
        }


        return binding.root
    }

    private fun requestPermissionsIfNecessary() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
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

    private fun showImagePickerOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery")

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Select Option")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> captureImageFromCamera()
                1 -> pickImageFromGallery()
            }
        }
        builder.show()
    }

    private fun captureImageFromCamera() {
        val photoFile = createImageFile()
        photoURI = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            photoFile
        )
        captureImageLauncher.launch(photoURI)
    }

    private val captureImageLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                handleCapturedImage()
            } else {
                Toast.makeText(requireContext(), "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
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

    private fun pickImageFromGallery() {
        getMedicationImage.launch("image/*")
    }

    private val getMedicationImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                processSelectedImage(it)
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
        expirationDateString: String,
        stockLevel: String
    ): Boolean {
        var isValid = true

        if (medicationName.isEmpty()) {
            binding.txtLayoutMedicationName.error = "Medication name cannot be empty"
            isValid = false
        } else {
            binding.txtLayoutMedicationName.error = null
        }

        if (dosage.isEmpty()) {
            binding.txtLayoutDosage.error = "Dosage cannot be empty"
            isValid = false
        } else {
            binding.txtLayoutDosage.error = null
        }

        if (expirationDateString.isEmpty()) {
            binding.txtLayoutExpirationDate.error = "Expiration date cannot be empty"
            isValid = false
        } else {
            try {
                val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(expirationDateString)
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
        }

        if (stockLevel.isEmpty() || stockLevel.toIntOrNull() == null || stockLevel.toInt() < 0) {
            binding.txtLayoutStockLevel.error = "Stock level must be a positive number"
            isValid = false
        } else {
            binding.txtLayoutStockLevel.error = null
        }

        if (medicationPhotoBlob == null) {
            Toast.makeText(requireContext(), "Please upload a photo", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }


    private fun showOCRImagePickerOptions() {
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
        val photoFile = createImageFileForOCR()
        ocrImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            photoFile
        )
        captureOCRImageLauncher.launch(ocrImageUri)
    }

    private val captureOCRImageLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                handleCapturedImageForOCR()
            } else {
                Toast.makeText(requireContext(), "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }

    @Throws(IOException::class)
    private fun createImageFileForOCR(): File {
        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? =
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "OCR_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun pickImageFromGalleryForOCR() {
        getOCRImage.launch("image/*")
    }

    private val getOCRImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                processOCRImage(it)
            }
        }

    private fun handleCapturedImageForOCR() {
        try {
            processOCRImage(ocrImageUri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun processOCRImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)

            // You may need to rotate the image if necessary
            //val fixedBitmap = rotateImageIfRequired(originalBitmap, uri)

            // Process the image with ML Kit
            processImageWithMLKit(originalBitmap)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun processImageWithMLKit(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Extract information from the recognized text
                extractMedicationInfo(visionText)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to recognize text", Toast.LENGTH_SHORT).show()
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
        // Medication Name: capture text up to the dosage number
        val medicationNameRegex = Regex(
            """^([A-Za-z\s\-]+?)(?:\s*[-]*)\d+""",
            RegexOption.IGNORE_CASE
        )
        // Dosage Extraction: numbers before "tablet" or "capsule", with optional space
        val dosageRegex = Regex(
            """\b(\d+(\.\d+)?)\s*(tablet|capsule)\b""",
            RegexOption.IGNORE_CASE
        )
        // Dosage Instruction Extraction: text within parentheses following dosage
        val dosageInstructionRegex = Regex(
            """\((\d+\s+TIMES\s+PER\s+DAY)\)""",
            RegexOption.IGNORE_CASE
        )
        // Instructions Extraction: text within parentheses OR text starting with instruction verbs anywhere in the line
        val instructionsRegex = Regex(
            """\(([^)]+)\)|\b(?:take|dose|apply|use|instill|inhale)\b\s+([^.\\n]+)""",
            RegexOption.IGNORE_CASE
        )
        // Expiration Date Extraction: prioritize "Expiry" over "Date", allow trailing punctuation
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
                } else {
                    // If no match, try to see if the entire line is a medication name
                    // If line does not contain numbers or dosage units
                    if (
                        !lowerLine.contains(Regex("""\d""")) &&
                        !lowerLine.contains(
                            Regex(
                                """\b(tab|tablet|capsule)\b""",
                                RegexOption.IGNORE_CASE
                            )
                        )
                    ) {
                        medicationName = trimmedLine
                        Log.d("ExtractMedicationInfo", "Assumed Medication Name: $medicationName")
                        continue
                    }
                }
            }

            // Extract Dosage Instruction (e.g., "(3 TIMES PER DAY)")
            if (instructions == null) {
                val dosageInstructionMatch = dosageInstructionRegex.find(line)
                if (dosageInstructionMatch != null) {
                    instructions = dosageInstructionMatch.groups[1]?.value?.trim()
                    Log.d("ExtractMedicationInfo", "Found Instructions from Dosage Instruction: $instructions")
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

            // Extract Instructions from any parentheses or starting with verbs
            if (instructions == null) {
                val instructionsMatch = instructionsRegex.find(line)
                if (instructionsMatch != null) {
                    val parenthetical = instructionsMatch.groups[1]?.value?.trim()
                    val verbInstruction = instructionsMatch.groups[2]?.value?.trim()
                    instructions = parenthetical ?: verbInstruction
                    Log.d(
                        "ExtractMedicationInfo",
                        "Found Instructions: ${instructions ?: "null"}"
                    )
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

        // Attempt to find stock level in the entire text if not found line-by-line
        if (stockLevel == null) {
            val stockLevelMatch = stockLevelRegex.find(fullText)
            if (stockLevelMatch != null) {
                val qtyString = stockLevelMatch.groups[1]?.value
                val qty = qtyString?.toIntOrNull()
                if (qty != null) {
                    stockLevel = qty
                    Log.d("ExtractMedicationInfo", "Found Stock Level from Full Text: $stockLevel")
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
                // Highlight the field to indicate it was auto-filled
                binding.txtMedicationName.background =
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.auto_filled_background
                    )
            }
            dosage?.let {
                binding.txtDosage.setText(it)
                binding.txtDosage.background =
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.auto_filled_background
                    )
            }
            expirationDate?.let {
                binding.txtExpirationDate.setText(it)
                binding.txtExpirationDate.background =
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.auto_filled_background
                    )
            }
            instructions?.let {
                binding.txtInstructions.setText(it)
                binding.txtInstructions.background =
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.auto_filled_background
                    )
            }
            stockLevel?.let {
                binding.txtStockLevel.setText(it.toString())
                binding.txtStockLevel.background =
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.auto_filled_background
                    )
            }
        }
    }


    /**
     * Formats the complete date by ensuring the year is in four-digit format.
     * If the year is two digits, it converts it to four digits by prefixing "20".
     * Assumes the date format is DD/MM/YYYY or DD/MM/YY.
     *
     * @param day The day part of the date.
     * @param month The month part of the date.
     * @param year The year part of the date (can be two or four digits).
     * @return The formatted date string in "dd/MM/yyyy" format or null if invalid.
     */
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

    /**
     * Formats the year to four digits based on its original length.
     * - If the year is two digits, it prefixes "20" to make it four digits (e.g., "20" -> "2020").
     * - If the year is already four digits, it remains unchanged.
     *
     * @param year The year string extracted from the date.
     * @return The formatted four-digit year string or null if invalid.
     */
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
