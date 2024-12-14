package com.example.trackurpill.medicationManagement.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.R
import com.example.trackurpill.data.MedicationInteraction

class InteractionsAdapter(
    private val interactions: List<MedicationInteraction>
) : RecyclerView.Adapter<InteractionsAdapter.InteractionViewHolder>() {

    inner class InteractionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtMedicationPair: TextView = itemView.findViewById(R.id.txtMedicationPair)
        val txtInteractionDetails: TextView = itemView.findViewById(R.id.txtInteractionDetails)
        val txtSuggestion: TextView = itemView.findViewById(R.id.txtSuggestion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InteractionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.interaction_item, parent, false)
        return InteractionViewHolder(view)
    }

    override fun onBindViewHolder(holder: InteractionViewHolder, position: Int) {
        val interaction = interactions[position]
        holder.txtMedicationPair.text = "${interaction.medicationPair}:"
        holder.txtInteractionDetails.text = "- ${interaction.interactionDetail}"
        holder.txtSuggestion.text = "- ${interaction.suggestion}"
    }

    override fun getItemCount(): Int = interactions.size
}
