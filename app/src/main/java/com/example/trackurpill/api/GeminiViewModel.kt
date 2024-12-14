package com.example.trackurpill.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import com.example.trackurpill.api.data.GeminiRequest
import com.example.trackurpill.api.data.GeminiResponse
import retrofit2.Response

class GeminiViewModel : ViewModel() {

    private val _geminiResponse = MutableLiveData<GeminiResponse?>()
    val geminiResponse: LiveData<GeminiResponse?> = _geminiResponse

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Initiates the Gemini API call to generate content.
     */
    fun generateContent(request: GeminiRequest, apiKey: String) {
        viewModelScope.launch {
            try {
                val response: Response<GeminiResponse> =
                    RetrofitInstance.api.generateContent(request, apiKey)
                if (response.isSuccessful) {
                    _geminiResponse.postValue(response.body())
                } else {
                    _error.postValue("Error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                _error.postValue("Exception: ${e.localizedMessage}")
            }
        }
    }
}
