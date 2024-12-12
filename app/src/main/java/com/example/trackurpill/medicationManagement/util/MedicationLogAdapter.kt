package com.example.trackurpill.medicationManagement.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.R
import com.example.trackurpill.data.MedicationLog
import java.text.SimpleDateFormat
import java.util.Locale

class MedicationLogAdapter :
    ListAdapter<MedicationLog, MedicationLogAdapter.MedicationLogViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicationLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.medication_log_item, parent, false)
        return MedicationLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicationLogViewHolder, position: Int) {
        val log = getItem(position)
        holder.bind(log)
    }

    class MedicationLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val medicationName: TextView = itemView.findViewById(R.id.medicationName)
        private val medicationDosage: TextView = itemView.findViewById(R.id.medicationDosage)
        private val medicationDateTime: TextView = itemView.findViewById(R.id.medicationDateTime)

        fun bind(log: MedicationLog) {
            medicationName.text = log.medicationName
            medicationDosage.text = "Dosage: ${log.dosage}"
            medicationDateTime.text = "Date: ${
                SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                .format(log.takenDate!!)}"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MedicationLog>() {
        override fun areItemsTheSame(oldItem: MedicationLog, newItem: MedicationLog) =
            oldItem.logId == newItem.logId

        override fun areContentsTheSame(oldItem: MedicationLog, newItem: MedicationLog) =
            oldItem == newItem
    }
}
