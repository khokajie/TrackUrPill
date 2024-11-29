package com.example.trackurpill.caregiverManagement.ui

import TabsPagerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.example.trackurpill.R
import com.example.trackurpill.caregiverManagement.data.CaregiverMonitorViewModel
import com.example.trackurpill.databinding.FragmentCaregiverPatientDetailsBinding
import com.google.android.material.tabs.TabLayoutMediator

class CaregiverPatientDetailsFragment : Fragment() {

    private lateinit var binding: FragmentCaregiverPatientDetailsBinding
    private val patientViewModel: CaregiverMonitorViewModel by activityViewModels()
    private lateinit var tabsPagerAdapter: TabsPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentCaregiverPatientDetailsBinding.inflate(inflater, container, false)

        val patientId = arguments?.getString("patientId") ?: ""

        // Observe patient details LiveData
        patientViewModel.fetchPatientDetails(patientId).observe(viewLifecycleOwner) { patient ->
            patient?.let {
                binding.patientNameTextView.text = patient.userName
                binding.patientImageView.setImageResource(R.drawable.ic_profile) // Default photo

                // Load patient photo (if available)
                patient.userPhoto?.let {
                    val photoBytes = it.toBytes()
                    Glide.with(this).load(photoBytes).circleCrop().into(binding.patientImageView)
                }
            }
        }

        tabsPagerAdapter = TabsPagerAdapter(requireActivity(), patientId)
        binding.viewPager.adapter = tabsPagerAdapter

        // Attach TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabsPagerAdapter.getPageTitle(position)
        }.attach()

        return binding.root
    }
}