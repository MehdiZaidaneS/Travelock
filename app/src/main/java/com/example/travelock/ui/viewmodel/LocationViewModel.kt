package com.example.travelock.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelock.data.location.DeviceLocationProvider
import com.example.travelock.data.location.DeviceLocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val locationProvider =
        DeviceLocationProvider(application)

    private val _currentLocation =
        MutableStateFlow<DeviceLocationResult?>(null)

    val currentLocation: StateFlow<DeviceLocationResult?> =
        _currentLocation.asStateFlow()

    fun refreshLocation() {
        viewModelScope.launch {
            _currentLocation.value =
                locationProvider.getCurrentLocation()
        }
    }
}