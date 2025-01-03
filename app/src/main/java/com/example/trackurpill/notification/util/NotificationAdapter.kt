// File: app/src/main/java/com/example/trackurpill/notification/util/NotificationAdapter.kt

package com.example.trackurpill.notification.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.R
import com.example.trackurpill.data.Notification
import com.example.trackurpill.databinding.NotificationItemBinding
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private val onItemClick: (Notification) -> Unit = {},
    private val onTakeClick: (Notification) -> Unit,
    private val onDismissClick: (Notification) -> Unit,
    private val onAcceptInvitation: (Notification) -> Unit,
    private val onRejectInvitation: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Notification>() {
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
                .format(notification.timestamp!!)

            statusTextView.text = notification.status

            // Set the icon based on notification type
            when (notification.type) {
                "reminder" -> {
                    iconImageView.setImageResource(R.drawable.ic_notification)

                    if (notification.status == "Sent") {
                        acceptButton.visibility = View.VISIBLE
                        rejectButton.visibility = View.VISIBLE
                        acceptButton.text = "Taken"
                        rejectButton.text = "Dismiss"
                    } else {
                        acceptButton.visibility = View.GONE
                        rejectButton.visibility = View.GONE
                    }

                    // Add onClickListeners for Accept and Reject buttons
                    acceptButton.setOnClickListener {
                        onTakeClick(notification)
                        acceptButton.visibility = View.GONE
                        rejectButton.visibility = View.GONE
                    }
                    rejectButton.setOnClickListener {
                        onDismissClick(notification)
                        acceptButton.visibility = View.GONE
                        rejectButton.visibility = View.GONE
                    }
                }
                "invitation" -> {
                    iconImageView.setImageResource(R.drawable.ic_caregiver)

                    if (notification.status == "Sent") {
                        acceptButton.visibility = View.VISIBLE
                        rejectButton.visibility = View.VISIBLE
                    } else {
                        acceptButton.visibility = View.GONE
                        rejectButton.visibility = View.GONE
                    }

                    // Add onClickListeners for Accept and Reject buttons
                    acceptButton.setOnClickListener {
                        onAcceptInvitation(notification)
                        acceptButton.visibility = View.GONE
                        rejectButton.visibility = View.GONE
                    }
                    rejectButton.setOnClickListener {
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

    fun submitFullList(list: List<Notification>) {
        submitList(list)
    }
}
