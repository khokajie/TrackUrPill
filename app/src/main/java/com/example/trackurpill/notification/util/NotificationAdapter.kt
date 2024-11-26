package com.example.trackurpill.notification.util

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.R
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
                }
            }

            // Set click listener
            root.setOnClickListener { onItemClick(notification) }
        }
    }


    fun submitFullList(list: List<Notification>) {
        allNotifications = list
        submitList(list)
    }

}
