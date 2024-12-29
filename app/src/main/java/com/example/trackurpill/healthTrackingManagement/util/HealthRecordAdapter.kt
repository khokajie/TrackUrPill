package com.example.trackurpill.healthTrackingManagement.util

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.data.HealthRecord
import com.example.trackurpill.databinding.HealthRecordItemBinding
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.firebase.Timestamp
import java.util.Date

class HealthRecordAdapter :
    ListAdapter<HealthRecord, HealthRecordAdapter.ViewHolder>(DiffCallback),
    Filterable {

    // This is the full list of all records (unfiltered).
    private var allHealthRecords: List<HealthRecord> = emptyList()

    // Date formats for displaying and filtering
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    private val filterDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    companion object DiffCallback : DiffUtil.ItemCallback<HealthRecord>() {
        override fun areItemsTheSame(oldItem: HealthRecord, newItem: HealthRecord) =
            oldItem.recordId == newItem.recordId

        override fun areContentsTheSame(oldItem: HealthRecord, newItem: HealthRecord) =
            oldItem == newItem
    }

    class ViewHolder(val binding: HealthRecordItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = HealthRecordItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val healthRecord = getItem(position)
        holder.binding.apply {
            // Display Date
            if (healthRecord.recordDateTime != null) {
                val date = healthRecord.recordDateTime.toDate()
                txtRecordDateTime.text = "Date: ${displayDateFormat.format(date)}"
                txtRecordDateTime.visibility = View.VISIBLE
            } else {
                txtRecordDateTime.visibility = View.GONE
            }

            // Weight
            if (healthRecord.weight > 0) {
                txtWeight.text = "${healthRecord.weight} kg"
                containerWeight.visibility = View.VISIBLE
            } else {
                containerWeight.visibility = View.GONE
            }

            // Height
            if (healthRecord.height > 0) {
                txtHeight.text = "${healthRecord.height} cm"
                containerHeight.visibility = View.VISIBLE
            } else {
                containerHeight.visibility = View.GONE
            }

            // Blood Pressure (Systolic / Diastolic)
            if (healthRecord.systolic > 0 && healthRecord.diastolic > 0) {
                txtBloodPressure.text = "${healthRecord.systolic}/${healthRecord.diastolic} mmHg"
                containerBloodPressure.visibility = View.VISIBLE
            } else {
                containerBloodPressure.visibility = View.GONE
            }

            // Heart Rate
            if (healthRecord.heartRate > 0) {
                txtHeartRate.text = "${healthRecord.heartRate} bpm"
                containerHeartRate.visibility = View.VISIBLE
            } else {
                containerHeartRate.visibility = View.GONE
            }

            // Blood Sugar Levels
            if (healthRecord.bloodSugarLevels > 0) {
                txtBloodSugarLevels.text = "${healthRecord.bloodSugarLevels} mmol/L"
                containerBloodSugar.visibility = View.VISIBLE
            } else {
                containerBloodSugar.visibility = View.GONE
            }

            // Cholesterol Levels
            if (healthRecord.cholesterolLevels > 0) {
                txtCholesterolLevels.text = "${healthRecord.cholesterolLevels} mmol/L"
                containerCholesterol.visibility = View.VISIBLE
            } else {
                containerCholesterol.visibility = View.GONE
            }

            // BMI
            if (healthRecord.bmi > 0) {
                txtBmi.text = String.format("%.1f", healthRecord.bmi)
                containerBmi.visibility = View.VISIBLE
            } else {
                containerBmi.visibility = View.GONE
            }

            // Temperature
            if (healthRecord.temperature > 0) {
                txtTemperature.text = "${healthRecord.temperature} °C"
                containerTemperature.visibility = View.VISIBLE
            } else {
                containerTemperature.visibility = View.GONE
            }
        }
    }

    /**
     * Store the unfiltered list of HealthRecords and set it to the RecyclerView.
     * Use this function in your fragment or activity whenever you get new data.
     */
    fun submitFullList(list: List<HealthRecord>) {
        allHealthRecords = list
        submitList(list)
    }

    /**
     * Implement Filterable to let the user filter HealthRecords by date.
     */
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.trim().orEmpty()
                Log.d("HealthRecordAdapter", "Filtering with query: '$query'")

                // If query is empty, show the entire list
                val filteredList = if (query.isEmpty()) {
                    allHealthRecords
                } else {
                    // Filter by matching the date portion (dd MMM yyyy)
                    allHealthRecords.filter { record ->
                        try {
                            val date = record.recordDateTime.toDate()
                            val onlyDateString = filterDateFormat.format(date)
                            onlyDateString == query
                        } catch (ex: Exception) {
                            Log.e("HealthRecordAdapter", "Date parsing error: ${ex.message}")
                            false
                        }
                    }
                }

                Log.d("HealthRecordAdapter", "Filtered list size: ${filteredList.size}")

                return FilterResults().apply {
                    values = filteredList
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                val newList = results?.values as? List<HealthRecord> ?: emptyList()
                Log.d("HealthRecordAdapter", "Publishing filtered list of size: ${newList.size}")
                submitList(newList)
            }
        }
    }
}
