package com.example.homesphere.features.fan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.data.repository.ActivityRepository
import com.example.homesphere.data.repository.HardwareSwitchingHub
import com.example.homesphere.domain.model.ActivityCategory
import com.example.homesphere.domain.model.ActivityEvent
import com.example.homesphere.domain.model.FanMode
import com.example.homesphere.domain.model.FanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FanUiState(
    val fanState: FanState = FanState(),
    val esp8266Ip: String = "192.168.1.50",
    val isSyncing: Boolean = false,
    val recentEvents: List<ActivityEvent> = emptyList(),
    val feedbackMessage: String? = null
)

class FanViewModel(
    private val hardwareHub: HardwareSwitchingHub,
    activityRepository: ActivityRepository
) : ViewModel() {

    private val syncing = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FanUiState> = combine(
        hardwareHub.fanState,
        hardwareHub.settings,
        syncing,
        activityRepository.observeByCategory(ActivityCategory.FAN, 10),
        feedback
    ) { state, settings, isSync, events, msg ->
        FanUiState(
            fanState = state,
            esp8266Ip = settings.esp8266Ip,
            isSyncing = isSync,
            recentEvents = events,
            feedbackMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FanUiState())

    fun syncHardware() {
        viewModelScope.launch {
            syncing.value = true
            val res = hardwareHub.syncFanStatus()
            syncing.value = false
            feedback.value = if (res.isSuccess) {
                "Smart Fan synced from ESP8266 (${uiState.value.esp8266Ip})"
            } else {
                "ESP8266 fan node unreachable at http://${uiState.value.esp8266Ip}/api/fan"
            }
        }
    }

    fun togglePower() {
        viewModelScope.launch {
            val res = hardwareHub.toggleFanPower()
            if (res.isFailure) {
                feedback.value = "Hardware offline at http://${uiState.value.esp8266Ip}/api/fan"
            }
        }
    }

    fun setSpeed(speed: Int) {
        viewModelScope.launch {
            val res = hardwareHub.setFanSpeed(speed)
            if (res.isFailure) {
                feedback.value = "Fan relay unreachable at ${uiState.value.esp8266Ip}"
            }
        }
    }

    fun setMode(mode: FanMode) {
        viewModelScope.launch {
            val res = hardwareHub.setFanMode(mode)
            if (res.isFailure) {
                feedback.value = "Fan relay unreachable at ${uiState.value.esp8266Ip}"
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
            FanViewModel(hardwareHub, activityRepository) as T
    }
}
