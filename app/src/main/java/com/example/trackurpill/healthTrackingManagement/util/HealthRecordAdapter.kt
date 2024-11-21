package com.example.trackurpill.healthTrackingManagement.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.data.HealthRecord
import com.example.trackurpill.databinding.HealthRecordItemBinding
import java.text.SimpleDateFormat
import java.util.*

class HealthRecordAdapter(
    private val onDelete: (HealthRecord) -> Unit
) : ListAdapter<HealthRecord, HealthRecordAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<HealthRecord>() {
        override fun areItemsTheSame(oldItem: HealthRecord, newItem: HealthRecord) =
            oldItem.recordId == newItem.recordId

        override fun areContentsTheSame(oldItem: HealthRecord, newItem: HealthRecord) =
            oldItem == newItem
    }

    class ViewHolder(val binding: HealthRecordItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = HealthRecordItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val healthRecord = getItem(position)
        holder.binding.apply {
            // Date
            if (healthRecord.recordDateTime != null) {
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                txtRecordDateTime.text = "Date: ${formatter.format(healthRecord.recordDateTime!!)}"
                txtRecordDateTime.visibility = View.VISIBLE
            } else {
                txtRecordDateTime.visibility = View.GONE
            }

            // Weight
            if (healthRecord.weight > 0) {
                txtWeight.text = "Weight: ${healthRecord.weight} kg"
                containerWeight.visibility = View.VISIBLE
            } else {
                containerWeight.visibility = View.GONE
            }

            // Height
            if (healthRecord.height > 0) {
                txtHeight.text = "Height: ${healthRecord.height} cm"
                containerHeight.visibility = View.VISIBLE
            } else {
                containerHeight.visibility = View.GONE
            }

            // Blood Pressure
            if (healthRecord.bloodPressure > 0) {
                txtBloodPressure.text = "Blood Pressure: ${healthRecord.bloodPressure} mmHg"
                containerBloodPressure.visibility = View.VISIBLE
            } else {
                containerBloodPressure.visibility = View.GONE
            }

            // Heart Rate
            if (healthRecord.heartRate > 0) {
                txtHeartRate.text = "Heart Rate: ${healthRecord.heartRate} bpm"
                containerHeartRate.visibility = View.VISIBLE
            } else {
                containerHeartRate.visibility = View.GONE
            }

            // Blood Sugar Levels
            if (healthRecord.bloodSugarLevels > 0) {
                txtBloodSugarLevels.text = "Blood Sugar: ${healthRecord.bloodSugarLevels} mmol/L"
                containerBloodSugar.visibility = View.VISIBLE
            } else {
                containerBloodSugar.visibility = View.GONE
            }

            // Cholesterol Levels
            if (healthRecord.cholesterolLevels > 0) {
                txtCholesterolLevels.text = "Cholesterol: ${healthRecord.cholesterolLevels} mmol/L"
                containerCholesterol.visibility = View.VISIBLE
            } else {
                containerCholesterol.visibility = View.GONE
            }

            // BMI
            if (healthRecord.bmi > 0) {
                txtBmi.text = "BMI: ${healthRecord.bmi}"
                containerBmi.visibility = View.VISIBLE
            } else {
                containerBmi.visibility = View.GONE
            }

            // Temperature
            if (healthRecord.temperature > 0) {
                txtTemperature.text = "Temperature: ${healthRecord.temperature} °C"
                containerTemperature.visibility = View.VISIBLE
            } else {
                containerTemperature.visibility = View.GONE
            }

            // Delete Button
            /*deleteRecordButton.setOnClickListener {
                onDelete(healthRecord)
            }*/
        }
    }
}
