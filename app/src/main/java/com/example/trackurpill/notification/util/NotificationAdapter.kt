package com.example.trackurpill.notification.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.R
import com.example.trackurpill.data.CAREGIVER
import com.example.trackurpill.data.NOTIFICATION
import com.example.trackurpill.data.Notification
import com.example.trackurpill.databinding.NotificationItemBinding
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private val onItemClick: (Notification) -> Unit = {}
) : ListAdapter<Notification, NotificationAdapter.ViewHolder>(Diff){

    private var allNotifications: List<Notification> = emptyList()

    companion object Diff : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(a: Notification, b: Notification) =
            a.notificationId == b.notificationId

        override fun areContentsTheSame(a: Notification, b: Notification) = a == b
    }

    class ViewHolder(val binding: NotificationItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            NotificationItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = getItem(position)
        holder.binding.apply {
            messageTextView.text = notification.message

            dateTextView.text = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                .format(notification.receiveTime)

            // Set the icon based on notification type
            when (notification.type) {
                "reminder" -> {
                    iconImageView.setImageResource(R.drawable.ic_notification)
                }
                "invitation" -> {
                    iconImageView.setImageResource(R.drawable.ic_caregiver)

                    if (notification.status == "Pending") {
                        acceptButton.visibility = View.VISIBLE
                        rejectButton.visibility = View.VISIBLE
                    }else{
                        acceptButton.visibility = View.GONE
                        rejectButton.visibility = View.GONE
                    }

                    // Add onClickListeners for Accept and Reject buttons
                    acceptButton.setOnClickListener {
                        // Handle accept logic
                        onAcceptInvitation(notification)
                        acceptButton.visibility = View.GONE
                        rejectButton.visibility = View.GONE
                    }
                    rejectButton.setOnClickListener {
                        // Handle reject logic
                        onRejectInvitation(notification)
                        acceptButton.visibility = View.GONE
                        rejectButton.visibility = View.GONE
                    }


                }
            }

            // Set click listener
            root.setOnClickListener { onItemClick(notification) }
        }
    }

    private fun onAcceptInvitation(notification: Notification) {
        val caregiverId = notification.senderId
        val patientId = notification.userId

        val caregiverRef = CAREGIVER.document(caregiverId)
        caregiverRef.get()
            .addOnSuccessListener { document ->
                val patientList =
                    document.get("patientList") as? MutableList<String> ?: mutableListOf()
                patientList.add(patientId)
                caregiverRef.update("patientList", patientList)
                NOTIFICATION.document(notification.notificationId).update("status", "Accepted")
            }
    }

    private fun onRejectInvitation(notification: Notification) {
        // Update the notification status to "Rejected"
        NOTIFICATION.document(notification.notificationId).update("status", "Rejected")
            .addOnSuccessListener {
                println("Notification updated to Rejected.")
            }
            .addOnFailureListener { e ->
                println("Failed to update notification status: ${e.message}")
            }
    }

    fun submitFullList(list: List<Notification>) {
        allNotifications = list
        submitList(list)
    }

}
