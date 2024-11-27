import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.trackurpill.healthTrackingManagement.ui.HealthHistoryFragment
import com.example.trackurpill.medicationManagement.ui.MedicationLogFragment
import com.example.trackurpill.medicationManagement.ui.PatientMedicationFragment

class TabsPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val patientId: String
) : FragmentStateAdapter(fragmentActivity) {

    private val fragmentTitles = listOf(
        "Medication",
        "Health History",
        "Medication Log"
    )

    override fun getItemCount(): Int = fragmentTitles.size

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> PatientMedicationFragment()
            1 -> HealthHistoryFragment()
            2 -> MedicationLogFragment()
            else -> throw IllegalArgumentException("Invalid tab position")
        }

        // Pass patientId to each fragment
        fragment.arguments = Bundle().apply {
            putString("patientId", patientId)
        }
        return fragment
    }

    fun getPageTitle(position: Int): String = fragmentTitles[position]
}