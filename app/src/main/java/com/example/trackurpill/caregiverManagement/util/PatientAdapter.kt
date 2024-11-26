package com.example.trackurpill.caregiverManagement.util

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.trackurpill.R
import com.example.trackurpill.data.Patient
import com.example.trackurpill.databinding.PatientItemBinding

class PatientAdapter(
    private val onItemClick: (Patient) -> Unit
) : ListAdapter<Patient, PatientAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Patient>() {
        override fun areItemsTheSame(oldItem: Patient, newItem: Patient) = oldItem.userId == newItem.userId
        override fun areContentsTheSame(oldItem: Patient, newItem: Patient) = oldItem == newItem
    }

    class ViewHolder(val binding: PatientItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = PatientItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val patient = getItem(position)
        holder.binding.apply {
            patientName.text = patient.userName
            patientEmail.text = patient.userEmail

            // Load patient photo (if available)
            patientPhoto.setImageResource(R.drawable.ic_profile) // Default photo
            patient.userPhoto?.let {
                val photoBytes = it.toBytes()
                Glide.with(patientPhoto.context).load(photoBytes).into(patientPhoto)
            }

            root.setOnClickListener { onItemClick(patient) }
        }
    }
}
