package com.example.homesphere.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.core.notifications.AlertChannelType
import com.example.homesphere.core.notifications.HomeSphereNotificationManager
import com.example.homesphere.data.local.SettingsDataStore
import com.example.homesphere.data.repository.HardwareSwitchingHub
import com.example.homesphere.domain.model.AppThemeMode
import com.example.homesphere.domain.model.HomeSphereSettings
import com.example.homesphere.domain.model.TemperatureUnit
import com.example.homesphere.hardware.esp8266.Esp8266HealthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: HomeSphereSettings = HomeSphereSettings(),
    val espHealth: Esp8266HealthResult = Esp8266HealthResult(),
    val lastConnectionCheckAt: Long = System.currentTimeMillis(),
    val isCheckingEsp: Boolean = false,
    val notificationPermissionGranted: Boolean = true,
    val feedbackMessage: String? = null
)

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val hardwareHub: HardwareSwitchingHub,
    private val notificationManager: HomeSphereNotificationManager
) : ViewModel() {

    private val permState = MutableStateFlow(notificationManager.hasNotificationPermission())
    private val feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsDataStore.settingsFlow,
        hardwareHub.espHealthStatus,
        hardwareHub.lastConnectionCheckAt,
        hardwareHub.isCheckingConnections,
        permState,
        feedback
    ) { values ->
        SettingsUiState(
            settings = values[0] as HomeSphereSettings,
            espHealth = values[1] as Esp8266HealthResult,
            lastConnectionCheckAt = values[2] as Long,
            isCheckingEsp = values[3] as Boolean,
            notificationPermissionGranted = values[4] as Boolean,
            feedbackMessage = values[5] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun refreshNotificationPermission() {
        permState.value = notificationManager.hasNotificationPermission()
    }

    fun updateHomeName(name: String) {
        viewModelScope.launch {
            settingsDataStore.updateHomeName(name)
            feedback.value = "Home name saved"
        }
    }

    fun updateProfile(name: String, role: String) {
        viewModelScope.launch {
            settingsDataStore.updateUserProfile(name, role)
            feedback.value = "User profile updated"
        }
    }

    fun updateTheme(mode: AppThemeMode) {
        viewModelScope.launch { settingsDataStore.updateThemeMode(mode) }
    }

    fun updateTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { settingsDataStore.updateTemperatureUnit(unit) }
    }

    fun updateWaterConfig(capacity: Int, lowThreshold: Int, highThreshold: Int) {
        viewModelScope.launch {
            settingsDataStore.updateWaterConfig(capacity, lowThreshold, highThreshold)
            feedback.value = "Water tank settings saved"
        }
    }

    fun updateRouterIp(ip: String) {
        viewModelScope.launch {
            settingsDataStore.updateRouterIp(ip)
            hardwareHub.checkAllConnectionsNow(logTransitions = false)
            feedback.value = "Router IP updated & verified"
        }
    }

    fun updateEspIp(ip: String) {
        viewModelScope.launch {
            val health = hardwareHub.saveAndCheckEsp8266Ip(ip)
            feedback.value = if (health.isLive) {
                "ESP8266 at ${health.ip} is LIVE (HTTP 200 • ${health.latencyMs ?: "--"} ms)"
            } else {
                "Saved ${health.ip} — ${health.message}"
            }
        }
    }

    fun checkEsp8266Now() {
        viewModelScope.launch {
            val health = hardwareHub.checkAllConnectionsNow(logTransitions = true)
            feedback.value = if (health.isLive) {
                "All connections verified • ESP8266 LIVE (HTTP 200)"
            } else {
                "All connections checked • ESP8266 ${health.message}"
            }
        }
    }

    fun updateNotifications(
        master: Boolean? = null,
        lowWater: Boolean? = null,
        door: Boolean? = null,
        router: Boolean? = null,
        automation: Boolean? = null
    ) {
        viewModelScope.launch {
            settingsDataStore.updateNotifications(master, lowWater, door, router, automation)
        }
    }

    fun sendTestNotification() {
        refreshNotificationPermission()
        val sent = notificationManager.sendAlert(
            type = AlertChannelType.AUTOMATION_EVENT,
            title = "HomeSphere Alert Verification",
            message = "Local notifications are properly configured for ${uiState.value.settings.homeName}."
        )
        feedback.value = if (sent) {
            "Test notification dispatched"
        } else {
            "Grant notification permission to receive alerts"
        }
    }

    fun clearFeedback() {
        feedback.value = null
    }

    class Factory(
        private val settingsDataStore: SettingsDataStore,
        private val hardwareHub: HardwareSwitchingHub,
        private val notificationManager: HomeSphereNotificationManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settingsDataStore, hardwareHub, notificationManager) as T
    }
}
