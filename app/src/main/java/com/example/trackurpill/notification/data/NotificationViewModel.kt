package com.example.trackurpill.notification.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.NOTIFICATION
import com.example.trackurpill.data.Notification
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObjects
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationViewModel(app: Application) : AndroidViewModel(app) {

    private val notificationsLD = MutableLiveData<List<Notification>>(emptyList())
    private var listener: ListenerRegistration? = null
    private val functions: FirebaseFunctions by lazy { Firebase.functions }

    // Filters and Sorting
    private val resultLD = MutableLiveData<List<Notification>>()
    private var field = "date"
    private var reverse = true

    init {
        listener = NOTIFICATION.addSnapshotListener { snap, _ ->
            notificationsLD.value = snap?.toObjects()
            updateResult()
        }
    }

    override fun onCleared() {
        listener?.remove()
    }

    fun init() = Unit

    // Access LiveData
    fun getNotificationsLD() = notificationsLD

    // Get all notifications
    fun getAllNotifications() = notificationsLD.value ?: emptyList()

    fun getSortedNotificationsLD() = resultLD


    // Add a notification
    fun setNotification(notification: Notification) {
        NOTIFICATION.document(notification.notificationId).set(notification)
    }

    fun sort(field: String, reverse: Boolean) {
        this.field = field
        this.reverse = reverse
        updateResult()
    }

    private fun updateResult() {
        var list = getAllNotifications()

        // Sort by field
        list = when (field) {
            "date" -> list.sortedBy { it.timestamp }
            else -> list
        }

        // Reverse the list if needed
        if (reverse) list = list.reversed()

        resultLD.value = list
    }


    fun dismissReminder(notificationId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch the Notification document
                val notificationSnapshot = NOTIFICATION.document(notificationId).get().await()
                if (!notificationSnapshot.exists()) {
                    throw FirebaseFirestoreException("Notification not found.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                val notification = notificationSnapshot.toObject(Notification::class.java)
                    ?: throw FirebaseFirestoreException("Failed to parse Notification.", FirebaseFirestoreException.Code.UNKNOWN)

                // Update status to 'dismissed'
                val updatedNotification = notification.copy(
                    status = "Dismissed",
                )

                // Update the Notification document
                setNotification(updatedNotification)

            } catch (e: FirebaseFirestoreException) {
                Log.e("NotificationVM", "Error dismissing reminder: ", e)
            } catch (e: Exception) {
                Log.e("NotificationVM", "Unexpected error: ", e)
            }
        }
    }

    fun takenReminder(notificationId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch the Notification document
                val notificationSnapshot = NOTIFICATION.document(notificationId).get().await()
                if (!notificationSnapshot.exists()) {
                    throw FirebaseFirestoreException("Notification not found.", FirebaseFirestoreException.Code.NOT_FOUND)
                }

                val notification = notificationSnapshot.toObject(Notification::class.java)
                    ?: throw FirebaseFirestoreException("Failed to parse Notification.", FirebaseFirestoreException.Code.UNKNOWN)

                // Update status to 'dismissed'
                val updatedNotification = notification.copy(
                    status = "Taken",
                )

                // Update the Notification document
                setNotification(updatedNotification)

            } catch (e: FirebaseFirestoreException) {
                Log.e("NotificationVM", "Error dismissing reminder: ", e)
            } catch (e: Exception) {
                Log.e("NotificationVM", "Unexpected error: ", e)
            }
        }
    }

    fun respondToInvitation(notificationId: String, caregiverId: String, response: String, callback: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Prepare data to send to the Cloud Function
                val data = hashMapOf(
                    "caregiverId" to caregiverId,
                    "response" to response,
                    "notificationId" to notificationId
                )

                // Call the 'responseInvitation' Cloud Function
                val result = functions
                    .getHttpsCallable("responseInvitation")
                    .call(data)
                    .await()

                // Parse the response
                val resultData = result.data as? Map<*, *> ?: emptyMap<Any, Any>()
                val success = resultData["success"] as? Boolean ?: false
                val message = resultData["message"] as? String ?: "No message provided."

                // Invoke callback with the result
                callback(success, message)

            } catch (e: Exception) {
                Log.e("NotificationViewModel", "Error responding to invitation:", e)
                callback(false, e.message ?: "An unknown error occurred.")
            }
        }
    }

    fun acceptInvitation(notificationId: String, caregiverId: String, callback: (Boolean, String) -> Unit) {
        respondToInvitation(notificationId, caregiverId, "accept", callback)
    }

    fun declineInvitation(notificationId: String, caregiverId: String, callback: (Boolean, String) -> Unit) {
        respondToInvitation(notificationId, caregiverId, "decline", callback)
    }
}
