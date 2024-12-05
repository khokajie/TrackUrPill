package com.example.trackurpill.medicationManagement.util

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
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
    private val onItemClick: (Medication) -> Unit = {}
) : ListAdapter<Medication, MedicationAdapter.ViewHolder>(Diff), Filterable {

    private var allMedications: List<Medication> = emptyList()

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

            // Load medication photo
            if (medication.medicationPhoto != null) {
                val photoBytes = medication.medicationPhoto!!.toBytes() // Convert Blob to ByteArray
                Glide.with(medicationPhoto.context)
                    .load(photoBytes)
                    .placeholder(R.drawable.ic_medication_placeholder)
                    .into(medicationPhoto)
            } else {
                medicationPhoto.setImageResource(R.drawable.ic_medication_placeholder)
            }

            // Set click listener
            root.setOnClickListener { onItemClick(medication) }
        }
    }

    fun submitFullList(list: List<Medication>) {
        allMedications = list
        submitList(list)
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filteredList = if (constraint.isNullOrEmpty()) {
                    allMedications
                } else {
                    val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
                    allMedications.filter {
                        it.medicationName.lowercase(Locale.getDefault()).contains(filterPattern)
                    }
                }
                return FilterResults().apply { values = filteredList }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                submitList(results?.values as? List<Medication>)
            }
        }
    }
}
