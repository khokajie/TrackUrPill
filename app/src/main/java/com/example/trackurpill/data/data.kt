package com.example.trackurpill.data

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp
import java.util.Date


val USER = Firebase.firestore.collection("User")
val PATIENT = Firebase.firestore.collection("Patient")
val CAREGIVER = Firebase.firestore.collection("Caregiver")
val MEDICATION = Firebase.firestore.collection("Medication")
val REMINDER = Firebase.firestore.collection("Reminder")
val HEALTH_RECORD = Firebase.firestore.collection("HealthRecord")
val MEDICATION_LOG = Firebase.firestore.collection("MedicationLog")
val NOTIFICATION = Firebase.firestore.collection("Notification")


// Base User Class
open class User(
    @DocumentId
    open var userId: String = "",
    open var userName: String = "",
    open var userEmail: String = "",
    open var userAge: Int = 0,
    open var userPhoto: Blob? = null, // Use Blob for binary data
    open var isCaregiver: Boolean = false, // Differentiates Patient and Caregiver
    open var fcmToken: String? = null // Add an fcmToken field
)

// Patient Data Model
data class Patient(
    @DocumentId
    override var userId: String = "",
    override var userName: String = "",
    override var userEmail: String = "",
    override var userAge: Int = 0,
    override var userPhoto: Blob? = null,
    override var isCaregiver: Boolean = false,
    override var fcmToken: String? = null // Include fcmToken
) : User(userId, userName, userEmail, userAge, userPhoto, isCaregiver, fcmToken)

// Caregiver Data Model
data class Caregiver(
    @DocumentId
    override var userId: String = "",
    override var userName: String = "",
    override var userEmail: String = "",
    override var userAge: Int = 0,
    override var userPhoto: Blob? = null,
    override var isCaregiver: Boolean = true,
    override var fcmToken: String? = null, // Include fcmToken
    var patientList: List<String> = emptyList() // IDs of assigned patients
) : User(userId, userName, userEmail, userAge, userPhoto, isCaregiver, fcmToken)



// Add this data class if not already present
data class MedicationInteraction(
    val medicationPair: String = "",
    val interactionDetail: String = "",
    val suggestion: String = ""
)

// Update the Medication data class
data class Medication(
    val medicationId: String = "",
    val medicationName: String = "",
    val dosage: String = "",
    val expirationDate: String = "",
    val stockLevel: Int = 0,
    val instruction: String = "",
    val medicationPhoto: Blob? = null,
    val medicationStatus: String = "Active",
    val userId: String = "",
    val interactions: List<MedicationInteraction> = emptyList() // New field
)

data class Reminder(
    @DocumentId
    val reminderId: String = "",
    val date: String? = "",        // Required for "Once" frequency
    val hour: Int = 0,
    val minute: Int = 0,
    val frequency: String = "",  // "Once", "Daily", "Weekly"
    val day: String? = "",       // Required for "Weekly" frequency
    val medicationId: String = ""
)

// Health Record Data Model
data class HealthRecord(
    @DocumentId
    var recordId: String = "",
    var weight: Double = 0.0, // in kilograms (kg) or pounds (lbs)
    var height: Double = 0.0, // in centimeters (cm) or inches (in)
    var systolic: Int = 0,     // systolic blood pressure (mmHg)
    var diastolic: Int = 0,    // diastolic blood pressure (mmHg)
    var heartRate: Int = 0,    // beats per minute (bpm)
    var bloodSugarLevels: Double = 0.0, // in mg/dL or mmol/L
    var cholesterolLevels: Double = 0.0, // in mg/dL or mmol/L
    var bmi: Double = 0.0,     // Body Mass Index
    var temperature: Double = 0.0, // in Celsius (°C) or Fahrenheit (°F)
    var recordDateTime: Timestamp = Timestamp.now(), // Firestore Timestamp
    var userId: String = ""    // Link to Patient/User ID
)

data class MedicationLog(
    @DocumentId
    var logId: String = "",
    var medicationId: String = "",
    var medicationName: String = "",
    var dosage: String = "",
    var takenDate: Date = Date(),
    var userId: String = ""
)

data class Notification(
    @DocumentId
    var notificationId: String = "",
    var reminderId: String = "",
    var userId: String = "",
    var message: String = "",
    var status: String = "",
    var timestamp: Date? = null,
    var createdAt: Date? = null,
    var type: String = "", // Optional: Include if managed elsewhere
    var senderId: String = "" // Optional: Include if managed elsewhere
)



data class LoggedInUser(
    var userType: String = "",
    var userId: String = ""
)