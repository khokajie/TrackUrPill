package com.example.trackurpill.medicationManagement.data

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.trackurpill.api.ApiResult
import com.example.trackurpill.api.DetailedHealthInfo
import com.example.trackurpill.api.HealthRepository

class AdverseEventViewModel : ViewModel() {

    private val repository = HealthRepository()

    /**
     * Fetches health information for the given drug name.
     * @param drugName The name of the drug to fetch health information for.
     * @return A list of DetailedHealthInfo objects.
     * @throws Exception if an error occurs during data fetching.
     */
    suspend fun getHealthInfo(drugName: String): List<DetailedHealthInfo> {
        Log.d("AdverseEventVM", "Fetching info for: $drugName")
        return try {
            val result = repository.getDetailedHealthInfo(drugName)
            when (result) {
                is ApiResult.Success -> {
                    Log.d("AdverseEventVM", "Success: ${result.data.size} events")
                    result.data
                }
                is ApiResult.Error -> {
                    Log.e("AdverseEventVM", "Error: ${result.exception.message}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("AdverseEventVM", "Exception during fetching: ${e.message}")
            emptyList()
        }
    }
}
