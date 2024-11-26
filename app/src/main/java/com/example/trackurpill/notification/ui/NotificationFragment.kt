package com.example.trackurpill.notification.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.R
import com.example.trackurpill.databinding.FragmentNotificationBinding
import com.example.trackurpill.notification.data.NotificationViewModel
import com.example.trackurpill.notification.util.NotificationAdapter
import com.google.firebase.auth.FirebaseAuth

class NotificationFragment : Fragment() {

    private lateinit var binding: FragmentNotificationBinding
    private val nav by lazy { findNavController() }
    private val notificationVM: NotificationViewModel by activityViewModels()
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentNotificationBinding.inflate(inflater, container, false)

        adapter = NotificationAdapter()

        binding.recyclerViewNotifications.adapter = adapter
        binding.recyclerViewNotifications.layoutManager = LinearLayoutManager(requireContext())

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserId != null) {
            notificationVM.getNotificationsLD().observe(viewLifecycleOwner) { notifications ->
                val filteredNotifications = notifications?.filter { it.userId == currentUserId } ?: emptyList()

                // Submit the filtered list to the adapter
                adapter.submitFullList(filteredNotifications)

            }
        } else {
            adapter.submitFullList(emptyList())
        }


        return binding.root
    }
}
