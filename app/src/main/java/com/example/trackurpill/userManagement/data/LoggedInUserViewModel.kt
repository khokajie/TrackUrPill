package com.example.trackurpill.userManagement.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.trackurpill.data.LoggedInUser

class LoggedInUserViewModel(app : Application) : AndroidViewModel(app) {

    private val _loggedInUserLD = MutableLiveData<LoggedInUser?>()
    val loggedInUserLD: LiveData<LoggedInUser?> get() = _loggedInUserLD

    fun init() = Unit

    fun setLoggedInUser(userType: String, userID: String) {
        _loggedInUserLD.value = LoggedInUser(userType, userID)
        println("Setting loggedInUserLD: UserType=$userType, UserId=$userID")
    }

    fun clearData() {
        _loggedInUserLD.value = null
    }
}