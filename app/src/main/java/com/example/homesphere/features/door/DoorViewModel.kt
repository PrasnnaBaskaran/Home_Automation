package com.example.homesphere.features.door

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.data.repository.ActivityRepository
import com.example.homesphere.data.repository.HardwareSwitchingHub
import com.example.homesphere.domain.model.ActivityCategory
import com.example.homesphere.domain.model.ActivityEvent
import com.example.homesphere.domain.model.DoorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DoorUiState(
    val doorState: DoorState = DoorState(),
    val esp8266Ip: String = "192.168.1.50",
    val isSyncing: Boolean = false,
    val accessHistory: List<ActivityEvent> = emptyList(),
    val feedbackMessage: String? = null
)

class DoorViewModel(
    private val hardwareHub: HardwareSwitchingHub,
    activityRepository: ActivityRepository
) : ViewModel() {

    private val syncing = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DoorUiState> = combine(
        hardwareHub.doorState,
        hardwareHub.settings,
        syncing,
        activityRepository.observeByCategory(ActivityCategory.DOOR, 25),
        feedback
    ) { door, settings, isSync, events, msg ->
        DoorUiState(
            doorState = door,
            esp8266Ip = settings.esp8266Ip,
            isSyncing = isSync,
            accessHistory = events,
            feedbackMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DoorUiState())

    fun syncHardware() {
        viewModelScope.launch {
            syncing.value = true
            val res = hardwareHub.syncDoorStatus()
            syncing.value = false
            feedback.value = if (res.isSuccess) {
                "Smart Door sensor & lock synced from ESP8266"
            } else {
                "Smart Door hardware unreachable at http://${uiState.value.esp8266Ip}/api/door"
            }
        }
    }

    fun lockDoor() {
        viewModelScope.launch {
            val res = hardwareHub.lockDoor()
            if (res.isFailure) {
                feedback.value = "Cannot confirm physical lock: door actuator unreachable."
            }
        }
    }

    fun unlockDoor() {
        viewModelScope.launch {
            val res = hardwareHub.unlockDoor()
            if (res.isFailure) {
                feedback.value = "Cannot confirm physical unlock: door actuator unreachable."
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
            DoorViewModel(hardwareHub, activityRepository) as T
    }
}
