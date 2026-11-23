package com.example.homesphere.features.water

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.data.local.SettingsDataStore
import com.example.homesphere.data.repository.HardwareSwitchingHub
import com.example.homesphere.domain.model.HomeSphereSettings
import com.example.homesphere.domain.model.WaterLevelReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WaterUiState(
    val reading: WaterLevelReading = WaterLevelReading(),
    val history: List<WaterLevelReading> = emptyList(),
    val settings: HomeSphereSettings = HomeSphereSettings(),
    val isSyncing: Boolean = false,
    val feedbackMessage: String? = null
)

class WaterViewModel(
    private val hardwareHub: HardwareSwitchingHub,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val syncing = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WaterUiState> = combine(
        hardwareHub.waterReading,
        hardwareHub.waterHistory,
        hardwareHub.settings,
        syncing,
        feedback
    ) { reading, history, settings, isSync, msg ->
        WaterUiState(
            reading = reading,
            history = history,
            settings = settings,
            isSyncing = isSync,
            feedbackMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WaterUiState())

    fun refreshReading() {
        viewModelScope.launch {
            syncing.value = true
            val res = hardwareHub.refreshWaterReading()
            syncing.value = false
            feedback.value = if (res.isSuccess) {
                "Water level updated from ESP8266 sensor"
            } else {
                "Water sensor unreachable at http://${uiState.value.settings.esp8266Ip}/api/water"
            }
        }
    }

    fun updateTankConfiguration(capacityLitres: Int, lowThreshold: Int, highThreshold: Int) {
        viewModelScope.launch {
            settingsDataStore.updateWaterConfig(capacityLitres, lowThreshold, highThreshold)
            feedback.value = "Saved tank capacity (${capacityLitres}L) and alert thresholds"
        }
    }

    fun toggleLowWaterAlert(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateNotifications(lowWater = enabled)
        }
    }

    fun clearFeedback() {
        feedback.value = null
    }

    class Factory(
        private val hardwareHub: HardwareSwitchingHub,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WaterViewModel(hardwareHub, settingsDataStore) as T
    }
}
