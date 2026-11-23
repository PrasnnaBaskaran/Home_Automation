package com.example.homesphere.features.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.data.repository.ActivityRepository
import com.example.homesphere.data.repository.ConnectedDevicesRepository
import com.example.homesphere.domain.model.ActivityCategory
import com.example.homesphere.domain.model.ConnectedDevice
import com.example.homesphere.domain.model.DiscoveredDeviceCategory
import com.example.homesphere.hardware.router.AndroidNetworkInspector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectedDevicesUiState(
    val devices: List<ConnectedDevice> = emptyList(),
    val selectedCategory: DiscoveredDeviceCategory? = null,
    val isScanningMdns: Boolean = false,
    val feedbackMessage: String? = null
)

class ConnectedDevicesViewModel(
    private val repository: ConnectedDevicesRepository,
    private val networkInspector: AndroidNetworkInspector,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<DiscoveredDeviceCategory?>(null)
    private val isScanning = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)
    private var scanJob: Job? = null

    val uiState: StateFlow<ConnectedDevicesUiState> = combine(
        repository.devices,
        selectedCategory,
        isScanning,
        feedback
    ) { all, category, scanning, msg ->
        val filtered = if (category == null) all else all.filter { it.category == category }
        ConnectedDevicesUiState(
            devices = filtered,
            selectedCategory = category,
            isScanningMdns = scanning,
            feedbackMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectedDevicesUiState())

    fun selectCategory(category: DiscoveredDeviceCategory?) {
        selectedCategory.value = category
    }

    fun registerManualDevice(
        name: String,
        ipAddress: String,
        macAddress: String?,
        category: DiscoveredDeviceCategory,
        details: String
    ) {
        viewModelScope.launch {
            val cleanIp = ipAddress.trim()
            val reachable = networkInspector.isHostReachable(cleanIp)
            val device = ConnectedDevice(
                name = name.trim(),
                ipAddress = cleanIp,
                macAddress = macAddress?.trim()?.takeIf { it.isNotEmpty() },
                category = category,
                isOnline = reachable,
                lastSeen = System.currentTimeMillis(),
                connectionDetails = details.trim().ifEmpty { "Manually registered LAN endpoint" },
                isManual = true
            )
            repository.addDevice(device)
            activityRepository.logEvent(
                title = "Registered Device: ${device.name}",
                details = "${device.ipAddress} (${device.category.label}) • ${if (reachable) "Reachable" else "Offline"}",
                category = ActivityCategory.NETWORK
            )
            feedback.value = "Registered ${device.name} (${if (reachable) "Online" else "Offline"})"
        }
    }

    fun deleteDevice(id: Long) {
        viewModelScope.launch {
            repository.removeDevice(id)
            feedback.value = "Device removed"
        }
    }

    fun startMdnsDiscovery() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            isScanning.value = true
            feedback.value = "Scanning local LAN via Android NsdManager (mDNS)…"
            var discoveredCount = 0
            val collector = launch {
                networkInspector.discoverLocalServices().collect { found ->
                    discoveredCount++
                    repository.addDevice(found)
                }
            }
            delay(5000L)
            collector.cancel()
            isScanning.value = false
            feedback.value = if (discoveredCount > 0) {
                "Discovered $discoveredCount mDNS service(s) on LAN"
            } else {
                "mDNS scan complete • Showing live detected & registered LAN devices"
            }
        }
    }

    fun clearFeedback() {
        feedback.value = null
    }

    class Factory(
        private val repository: ConnectedDevicesRepository,
        private val networkInspector: AndroidNetworkInspector,
        private val activityRepository: ActivityRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConnectedDevicesViewModel(repository, networkInspector, activityRepository) as T
    }
}
