package com.example.trackurpill.medicationManagement.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.data.Reminder
import com.example.trackurpill.databinding.ReminderItemBinding

class ReminderAdapter(val onDelete: (Reminder) -> Unit) :
    ListAdapter<Reminder, ReminderAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Reminder>() {
        override fun areItemsTheSame(oldItem: Reminder, newItem: Reminder) =
            oldItem.reminderId == newItem.reminderId

        override fun areContentsTheSame(oldItem: Reminder, newItem: Reminder) =
            oldItem == newItem
    }

    class ViewHolder(val binding: ReminderItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ReminderItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reminder = getItem(position)

        val timeFormat = String.format("%02d:%02d", reminder.hour, reminder.minute)
        val dateFormat = reminder.date ?: "" // Show date only if available

        holder.binding.reminderTime.text = if (reminder.frequency == "Once") {
            "$dateFormat at $timeFormat"
        } else {
            timeFormat
        }

        holder.binding.reminderFrequency.text = when (reminder.frequency) {
            "Weekly" -> "Weekly on ${reminder.day}"
            else -> reminder.frequency
        }

        holder.binding.deleteReminderButton.setOnClickListener {
            onDelete(reminder)
        }
    }

}
