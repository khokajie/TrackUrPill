package com.example.trackurpill.healthTrackingManagement.ui

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.R
import com.example.trackurpill.databinding.FragmentHealthHistoryBinding
import com.example.trackurpill.healthTrackingManagement.data.HealthHistoryViewModel
import com.example.trackurpill.healthTrackingManagement.util.HealthRecordAdapter
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import android.graphics.Rect
import java.util.Locale

class HealthHistoryFragment : Fragment() {

    private lateinit var binding: FragmentHealthHistoryBinding
    private val healthHistoryVM: HealthHistoryViewModel by activityViewModels()
    private lateinit var adapter: HealthRecordAdapter
    private val nav by lazy { findNavController() }
    private var patientId: String? = null

    // We'll store the user's selected date string here to reapply it after data refresh
    private var searchQuery: String? = null

    // Define the format your recordDateTime is stored in (e.g., "dd MMM yyyy HH:mm")
    private val originalFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

    // Date only format for filtering
    private val dateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHealthHistoryBinding.inflate(inflater, container, false)

        // Retrieve optional patientId
        patientId = arguments?.getString("patientId")

        // Initialize the adapter
        adapter = HealthRecordAdapter()

        // Set up RecyclerView
        binding.recyclerViewHealthRecords.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HealthHistoryFragment.adapter
        }

        binding.searchViewHealthRecords.apply {
            // Make it clickable but not focusable or editable
            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            inputType = 0
            isIconified = false

            // Prevent text changes
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = true
                override fun onQueryTextChange(newText: String?): Boolean = true
            })

            // Find and handle the close button
            val closeButton = findViewById<View>(androidx.appcompat.R.id.search_close_btn)
            closeButton?.setOnClickListener {
                searchQuery = null
                setQuery("", false)
                adapter.filter.filter("")
                isIconified = false

                // Dismiss the keyboard
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
            }

            // Main click handler
            findViewById<View>(androidx.appcompat.R.id.search_src_text)?.setOnClickListener {
                showDatePickerDialog()
            }

            // Also set on the main view
            setOnClickListener {
                showDatePickerDialog()
            }
        }
        // -------------------------------------------------------------------------------

        // Determine whose health records to load
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val targetUserId = patientId ?: currentUserId

        if (targetUserId != null) {
            // Observe LiveData
            healthHistoryVM.getResultLD().observe(viewLifecycleOwner) { healthRecords ->
                val filteredHealthRecords = healthRecords?.filter { it.userId == targetUserId } ?: emptyList()

                // Show "No records" if empty
                binding.noRecordText.visibility =
                    if (filteredHealthRecords.isEmpty()) View.VISIBLE else View.GONE

                // Show the latest valid record data
                val latestValidRecord = filteredHealthRecords
                    .filter { it.height > 0 && it.weight > 0 && it.bmi > 0 && it.recordDateTime != null }
                    .maxWithOrNull(compareBy { parseDate(it.recordDateTime.toString()) })

                if (latestValidRecord != null) {
                    binding.bmiValue.text = String.format("BMI\n%.1f", latestValidRecord.bmi)
                    binding.heightValue.text = "${latestValidRecord.height} cm"
                    binding.weightValue.text = "${latestValidRecord.weight} kg"
                } else {
                    binding.bmiValue.text = "N/A"
                    binding.heightValue.text = "N/A"
                    binding.weightValue.text = "N/A"
                }

                // Submit the full list to the adapter
                adapter.submitFullList(filteredHealthRecords)

                // If there's an existing searchQuery (user previously selected a date),
                // reapply it here
                if (!searchQuery.isNullOrEmpty()) {
                    adapter.filter.filter(searchQuery)
                }
            }
        } else {
            // If userId is null, show no records
            binding.noRecordText.visibility = View.VISIBLE
            adapter.submitFullList(emptyList())
        }

        // Floating Action Button to add a new health record
        binding.fabAddHealthRecord.setOnClickListener {
            nav.navigate(
                R.id.addHealthHistoryFragment,
                Bundle().apply { putString("patientId", patientId) }
            )
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restore search query if available
        if (savedInstanceState != null) {
            searchQuery = savedInstanceState.getString("SEARCH_QUERY")
            searchQuery?.let {
                binding.searchViewHealthRecords.setQuery(it, false)
                binding.searchViewHealthRecords.isIconified = false
                adapter.filter.filter(it)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("SEARCH_QUERY", searchQuery)
    }

    /**
     * Show a DatePickerDialog for the user to pick a date.
     * Then format it to "dd MMM yyyy" to filter records.
     */
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                // User selected a date
                val selectedDate = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                }

                // Format the date portion to "dd MMM yyyy"
                val dateString = dateOnlyFormat.format(selectedDate.time)

                Log.d("HealthHistoryFragment", "Selected Date: $dateString")

                // Set it into the SearchView's text
                binding.searchViewHealthRecords.setQuery(dateString, false)

                // Ensure the SearchView is expanded to show the query
                binding.searchViewHealthRecords.isIconified = false

                // Save the query to reapply later if needed
                searchQuery = dateString

                // Call the adapter's filter to only show records for that date
                adapter.filter.filter(dateString)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    /**
     * Helper function to parse recordDateTime string.
     * Your recordDateTime is stored in "dd MMM yyyy HH:mm" format, for example "28 Dec 2024 16:19".
     */
    private fun parseDate(dateString: String?): Date {
        return try {
            if (dateString.isNullOrEmpty()) Date(0)
            else originalFormat.parse(dateString) ?: Date(0)
        } catch (e: Exception) {
            Log.e("HealthHistoryFragment", "Date parsing error: ${e.message}")
            Date(0)
        }
    }
}
