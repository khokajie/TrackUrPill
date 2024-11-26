package com.example.trackurpill.data

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.sql.Time
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
    open var isCaregiver: Boolean = false // Differentiates Patient and Caregiver
)

// Patient Data Model
data class Patient(
    @DocumentId
    override var userId: String = "",
    override var userName: String = "",
    override var userEmail: String = "",
    override var userAge: Int = 0,
    override var userPhoto: Blob? = null,
    override var isCaregiver: Boolean = false
) : User(userId, userName, userEmail,  userAge, userPhoto, isCaregiver)

// Caregiver Data Model
data class Caregiver(
    @DocumentId
    override var userId: String = "",
    override var userName: String = "",
    override var userEmail: String = "",
    override var userAge: Int = 0,
    override var userPhoto: Blob? = null,
    override var isCaregiver: Boolean = true,
    var patientList: List<String> = emptyList() // IDs of assigned patients
) : User(userId, userName, userEmail, userAge, userPhoto, isCaregiver)


// Medication Data Model
data class Medication(
    @DocumentId
    var medicationId: String = "",
    var medicationName: String = "",
    var dosage: String = "",
    var expirationDate: Date? = null,
    var stockLevel: Int = 0,
    var instruction: String = "",
    var medicationPhoto: Blob? = null, // Use Blob for binary data
    var medicationStatus: String = "",
    var userId: String = "" // Link to User (Patient)
)


data class Reminder(
    @DocumentId
    var reminderId: String = "",
    var date: String? = null, // Stores the date for "Once" reminders (format: MM/dd/yyyy)
    var hour: Int = 0,       // Stores the hour of the reminder
    var minute: Int = 0,     // Stores the minute of the reminder
    var frequency: String = "", // e.g., Daily, Weekly, Once
    var day: String? = null,    // Optional: Stores the day for weekly reminders
    var medicationId: String = "" // Link to Medication
)



// Health Record Data Model
data class HealthRecord(
    @DocumentId
    var recordId: String = "",
    var weight: Double = 0.0,
    var height: Double = 0.0,
    var bloodPressure: Int = 0,
    var heartRate: Int = 0,
    var bloodSugarLevels: Double = 0.0,
    var cholesterolLevels: Double = 0.0,
    var bmi: Double = 0.0,
    var temperature: Double = 0.0,
    var recordDateTime: Date? = null,
    var userId: String = "" // Link to Patient
)

data class MedicationLog(
    var logId: String = "",
    var medicationId: String = "",
    var medicationName: String = "",
    var dosage: String = "",
    var takenDate: Date = Date(),
    var userId: String = ""
)

data class Notification(
    var notificationId: String = "",
    var message: String = "",
    var receiveTime: Date = Date(),
    var type: String = "", // "reminder" or "invitation"
    var userId: String = ""
)

data class LoggedInUser(
    var userType: String = "",
    var userId: String = ""
)