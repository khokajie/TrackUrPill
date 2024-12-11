package com.example.trackurpill.notification.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackurpill.data.MedicationLog
import com.example.trackurpill.data.Notification
import com.example.trackurpill.databinding.FragmentNotificationBinding
import com.example.trackurpill.medicationManagement.data.MedicationLogViewModel
import com.example.trackurpill.medicationManagement.data.PatientMedicationViewModel
import com.example.trackurpill.medicationManagement.data.ReminderViewModel
import com.example.trackurpill.notification.data.NotificationViewModel
import com.example.trackurpill.notification.util.NotificationAdapter
import com.google.firebase.auth.FirebaseAuth
import java.util.Date
import java.util.UUID

class NotificationFragment : Fragment() {

    private lateinit var binding: FragmentNotificationBinding
    private val notificationVM: NotificationViewModel by activityViewModels()
    private val logVM: MedicationLogViewModel by activityViewModels()
    private val medicationVM: PatientMedicationViewModel by activityViewModels()
    private val reminderVM: ReminderViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentNotificationBinding.inflate(inflater, container, false)

        val adapter = NotificationAdapter(
            onTakeClick = { notification ->
                val reminderId = notification.reminderId
                if (reminderId.isNullOrEmpty()) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Invalid reminder ID.", Toast.LENGTH_SHORT).show()
                    }
                    return@NotificationAdapter
                }

                // Fetch the reminder asynchronously
                reminderVM.fetchReminderById(reminderId) { reminder ->
                    if (reminder == null) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Reminder not found.", Toast.LENGTH_SHORT).show()
                        }
                        return@fetchReminderById
                    }

                    // Fetch the medication asynchronously
                    medicationVM.fetchMedicationById(reminder.medicationId) { medication ->
                        if (medication == null) {
                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(), "Medication not found.", Toast.LENGTH_SHORT).show()
                            }
                            return@fetchMedicationById
                        }

                        // Create MedicationLog
                        val medicationLog = MedicationLog(
                            logId = UUID.randomUUID().toString(),
                            medicationId = medication.medicationId,
                            medicationName = medication.medicationName,
                            dosage = medication.dosage,
                            takenDate = Date(),
                            userId = medication.userId
                        )

                        // Mark medication as taken
                        medicationVM.markMedicationAsTaken(medication.medicationId, medication.dosage)

                        // Update notification status
                        notificationVM.takenReminder(notification.notificationId)

                        // Set the MedicationLog
                        logVM.setLog(medicationLog)

                        // Cancel the system notification
                        cancelSystemNotification(notification.notificationId)

                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Stock updated and log recorded.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismissClick = { notification ->
                notificationVM.dismissReminder(notification.notificationId)
                // Cancel the system notification
                cancelSystemNotification(notification.notificationId)
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Reminder dismissed.", Toast.LENGTH_SHORT).show()
                }
            },
            onAcceptInvitation = { notification ->
                notificationVM.acceptInvitation(notification.notificationId)
                // Cancel the system notification
                cancelSystemNotification(notification.notificationId)
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Invitation Accepted.", Toast.LENGTH_SHORT).show()
                }
            },
            onRejectInvitation = { notification ->
                notificationVM.declineInvitation(notification.notificationId)
                // Cancel the system notification
                cancelSystemNotification(notification.notificationId)
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Invitation Rejected.", Toast.LENGTH_SHORT).show()
                }
            },
        )

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

    /**
     * Cancels the system notification based on notificationId.
     */
    private fun cancelSystemNotification(notificationId: String) {
        val notificationManager =
            requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Generate the same notification ID used when showing the notification
        val notificationIdInt = notificationId.hashCode()

        notificationManager.cancel(notificationIdInt)
    }
}


