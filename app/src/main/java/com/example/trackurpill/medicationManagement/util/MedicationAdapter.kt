package com.example.trackurpill.medicationManagement.util

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.trackurpill.R
import com.example.trackurpill.data.Medication
import com.example.trackurpill.databinding.MedicationItemBinding
import java.text.SimpleDateFormat
import java.util.*

class MedicationAdapter(
    val onItemClick: (ViewHolder, Medication) -> Unit = { _, _ -> }
) : ListAdapter<Medication, MedicationAdapter.ViewHolder>(Diff) {

    companion object Diff : DiffUtil.ItemCallback<Medication>() {
        override fun areItemsTheSame(a: Medication, b: Medication) =
            a.medicationId == b.medicationId

        override fun areContentsTheSame(a: Medication, b: Medication) = a == b
    }

    class ViewHolder(val binding: MedicationItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            MedicationItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val medication = getItem(position)
        holder.binding.apply {
            title.text = medication.medicationName
            dosage.text = "Dosage: ${medication.dosage}"
            stock.text = "Stock: ${medication.stockLevel}"
            expiration.text = "Expires: ${
                medication.expirationDate?.let {
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it)
                } ?: "N/A"
            }"

            // Load medication photo
            if (medication.medicationPhoto != null) {
                val photoBytes = medication.medicationPhoto!!.toBytes() // Convert Blob to ByteArray
                Glide.with(medicationPhoto.context)
                    .load(photoBytes)
                    .placeholder(R.drawable.ic_medication_placeholder) // Replace with your placeholder image
                    .into(medicationPhoto)
            } else {
                medicationPhoto.setImageResource(R.drawable.ic_medication_placeholder) // Replace with your placeholder image
            }

            // Set onClick action
            onItemClick(holder, medication)
        }
    }
}



