package com.example.trackurpill.medicationManagement.util

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.data.Reminder
import com.example.trackurpill.databinding.ReminderItemBinding
import java.text.SimpleDateFormat
import java.util.*

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
        val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        holder.binding.reminderTime.text = dateFormat.format(reminder.reminderTime)
        holder.binding.reminderFrequency.text = reminder.frequency

        holder.binding.deleteReminderButton.setOnClickListener {
            onDelete(reminder)
        }
    }

}
