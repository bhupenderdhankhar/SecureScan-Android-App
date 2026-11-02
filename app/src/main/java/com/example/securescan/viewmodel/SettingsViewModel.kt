package com.example.securescan.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securescan.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val darkMode = repository.darkMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val autoEnhance = repository.autoEnhance.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val defaultPassword = repository.defaultPassword.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val defaultPasswordEnabled = repository.defaultPasswordEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val alwaysPassword = repository.alwaysPassword.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val highQuality = repository.highQuality.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val showImages = repository.showImages.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkMode(enabled) }
    }

    fun setAutoEnhance(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoEnhance(enabled) }
    }

    fun setDefaultPassword(password: String) {
        viewModelScope.launch { repository.setDefaultPassword(password) }
    }

    fun setDefaultPasswordEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setDefaultPasswordEnabled(enabled) }
    }

    fun setAlwaysPassword(enabled: Boolean) {
        viewModelScope.launch { repository.setAlwaysPassword(enabled) }
    }

    fun setHighQuality(enabled: Boolean) {
        viewModelScope.launch { repository.setHighQuality(enabled) }
    }

    fun setShowImages(enabled: Boolean) {
        viewModelScope.launch { repository.setShowImages(enabled) }
    }
}
