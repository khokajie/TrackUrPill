import android.icu.text.SimpleDateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trackurpill.data.Medication
import com.example.trackurpill.databinding.MedicationHistoryItemBinding
import java.util.Locale

class MedicationHistoryAdapter(
    private val onRecoverClick: (Medication) -> Unit = {}
) : ListAdapter<Medication, MedicationHistoryAdapter.ViewHolder>(Diff) {

    companion object Diff : DiffUtil.ItemCallback<Medication>() {
        override fun areItemsTheSame(a: Medication, b: Medication) =
            a.medicationId == b.medicationId

        override fun areContentsTheSame(a: Medication, b: Medication) = a == b
    }

    class ViewHolder(val binding: MedicationHistoryItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MedicationHistoryItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val medication = getItem(position)
        holder.binding.apply {
            medicationName.text = medication.medicationName ?: "Unknown"
            medicationDosage.text = "Dosage: ${medication.dosage ?: "N/A"}"
            medicationInstruction.text = "Instruction: ${medication.instruction ?: "No instruction"}"
            medicationExpirationDate.text = "Expiration Date: ${
                medication.expirationDate?.let {
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it)
                } ?: "No date"
            }"

            // Set Recover Button Action
            recoverButton.setOnClickListener {
                onRecoverClick(medication) }
        }
    }
}
