package com.example.homesphere.features.lighting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.data.repository.ActivityRepository
import com.example.homesphere.data.repository.HardwareSwitchingHub
import com.example.homesphere.domain.model.ActivityCategory
import com.example.homesphere.domain.model.ActivityEvent
import com.example.homesphere.domain.model.LightMode
import com.example.homesphere.domain.model.LightState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LightingUiState(
    val lightState: LightState = LightState(),
    val esp8266Ip: String = "192.168.1.50",
    val isSyncing: Boolean = false,
    val activityHistory: List<ActivityEvent> = emptyList(),
    val feedbackMessage: String? = null
)

class LightingViewModel(
    private val hardwareHub: HardwareSwitchingHub,
    activityRepository: ActivityRepository
) : ViewModel() {

    private val syncing = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LightingUiState> = combine(
        hardwareHub.lightState,
        hardwareHub.settings,
        syncing,
        activityRepository.observeByCategory(ActivityCategory.LIGHT, 12),
        feedback
    ) { light, settings, isSync, events, msg ->
        LightingUiState(
            lightState = light,
            esp8266Ip = settings.esp8266Ip,
            isSyncing = isSync,
            activityHistory = events,
            feedbackMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LightingUiState())

    fun syncHardware() {
        viewModelScope.launch {
            syncing.value = true
            val res = hardwareHub.syncLightStatus()
            syncing.value = false
            feedback.value = if (res.isSuccess) {
                "Room Light synced with ESP8266 (${uiState.value.esp8266Ip})"
            } else {
                "ESP8266 lighting controller unreachable at ${uiState.value.esp8266Ip}"
            }
        }
    }

    fun togglePower() {
        viewModelScope.launch {
            val res = hardwareHub.toggleLightPower()
            if (res.isFailure) {
                feedback.value = "Room Light relay unreachable at ${uiState.value.esp8266Ip}. Tap Sync when online."
            }
        }
    }

    fun setBrightness(brightness: Int) {
        viewModelScope.launch {
            val res = hardwareHub.setLightBrightness(brightness)
            if (res.isFailure) {
                feedback.value = "Dimmer hardware unreachable at ${uiState.value.esp8266Ip}."
            }
        }
    }

    fun setMode(mode: LightMode) {
        viewModelScope.launch {
            val res = hardwareHub.setLightMode(mode)
            if (res.isFailure) {
                feedback.value = "Lighting controller unreachable at ${uiState.value.esp8266Ip}."
            }
        }
    }

    fun clearFeedback() {
        feedback.value = null
    }

    class Factory(
        private val hardwareHub: HardwareSwitchingHub,
        private val activityRepository: ActivityRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LightingViewModel(hardwareHub, activityRepository) as T
    }
}
