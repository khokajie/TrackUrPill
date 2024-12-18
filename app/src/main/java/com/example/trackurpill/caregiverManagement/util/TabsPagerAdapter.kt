import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.trackurpill.caregiverManagement.ui.CaregiverMonitorHealthHistoryFragment
import com.example.trackurpill.caregiverManagement.ui.CaregiverMonitorMedicationFragment
import com.example.trackurpill.caregiverManagement.ui.CaregiverMonitorMedicationLogFragment


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
            0 -> CaregiverMonitorMedicationFragment()
            1 -> CaregiverMonitorHealthHistoryFragment()
            2 -> CaregiverMonitorMedicationLogFragment()
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