package com.example.trackurpill.notification.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.NOTIFICATION
import com.example.trackurpill.data.Notification
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationViewModel(app: Application) : AndroidViewModel(app) {

    private val notificationsLD = MutableLiveData<List<Notification>>(emptyList())
    private var listener: ListenerRegistration? = null

    // Filters and Sorting
    private val resultLD = MutableLiveData<List<Notification>>()
    private var userIdFilter = ""
    private var field = ""
    private var reverse = false

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
    fun getResultLD() = resultLD

    // Get all notifications
    fun getAllNotifications() = notificationsLD.value ?: emptyList()

    // Get notifications for a specific user
    fun getNotificationsByUser(userId: String) = getAllNotifications().filter { it.userId == userId }

    // Add a notification
    fun setNotification(notification: Notification) {
        NOTIFICATION.document(notification.notificationId).set(notification)
    }

    // Delete a notification
    fun deleteNotification(notificationId: String) {
        NOTIFICATION.document(notificationId).delete()
    }

    // Filter and Sort Operations
    fun filterByUser(userId: String) {
        this.userIdFilter = userId
        updateResult()
    }

    fun sort(field: String, reverse: Boolean) {
        this.field = field
        this.reverse = reverse
        updateResult()
    }

    private fun updateResult() {
        var list = getAllNotifications()

        // Filter by userId
        list = list.filter {
            it.userId == userIdFilter || userIdFilter.isEmpty()
        }

        // Sort by field
        list = when (field) {
            "message" -> list.sortedBy { it.message }
            else -> list
        }

        // Reverse the list if needed
        if (reverse) list = list.reversed()

        resultLD.value = list
    }

    /**
     * Dismisses a reminder by updating the Notification's status to 'dismissed'.
     */
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
                    status = "Dismiss",
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
}
