package com.example.homesphere.features.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.data.local.SettingsDataStore
import com.example.homesphere.data.repository.ConnectedDevicesRepository
import com.example.homesphere.data.repository.HardwareSwitchingHub
import com.example.homesphere.domain.model.ConnectedDevice
import com.example.homesphere.domain.model.NetworkMetricPoint
import com.example.homesphere.domain.model.NetworkStatus
import com.example.homesphere.hardware.esp8266.WebSocketChannelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NetworkGraphTab(val label: String, val unit: String) {
    RSSI("Signal (RSSI)", "dBm"),
    LATENCY("Ping Latency", "ms"),
    DOWNLOAD("Download", "Mbps"),
    UPLOAD("Upload", "Mbps")
}

data class NetworkUiState(
    val status: NetworkStatus = NetworkStatus(),
    val metricHistory: List<NetworkMetricPoint> = emptyList(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val selectedGraphTab: NetworkGraphTab = NetworkGraphTab.RSSI,
    val espPollingStatus: String = "Live Android network telemetry active",
    val webSocketState: WebSocketChannelState = WebSocketChannelState.IDLE,
    val isPollingEsp: Boolean = false,
    val feedbackMessage: String? = null
)

class NetworkViewModel(
    private val hardwareHub: HardwareSwitchingHub,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val selectedTab = MutableStateFlow(NetworkGraphTab.RSSI)
    private val pollingBusy = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NetworkUiState> = combine(
        hardwareHub.networkStatus,
        hardwareHub.networkMetricHistory,
        connectedDevicesRepository.devices,
        selectedTab,
        hardwareHub.espLastDiagnosticsMessage,
        hardwareHub.realEspGateway.webSocketClient.state,
        pollingBusy,
        feedback
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        NetworkUiState(
            status = values[0] as NetworkStatus,
            metricHistory = values[1] as List<NetworkMetricPoint>,
            connectedDevices = values[2] as List<ConnectedDevice>,
            selectedGraphTab = values[3] as NetworkGraphTab,
            espPollingStatus = values[4] as String,
            webSocketState = values[5] as WebSocketChannelState,
            isPollingEsp = values[6] as Boolean,
            feedbackMessage = values[7] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkUiState())

    fun selectGraphTab(tab: NetworkGraphTab) {
        selectedTab.value = tab
    }

    fun pollEsp8266Now() {
        viewModelScope.launch {
            pollingBusy.value = true
            val ok = hardwareHub.pollEsp8266Diagnostics()
            pollingBusy.value = false
            feedback.value = if (ok) {
                "ESP8266 & live network diagnostics synced"
            } else {
                "Live Android telemetry refreshed • ESP8266 unreachable at ${uiState.value.status.espIp}"
            }
        }
    }

    fun toggleWebSocketProbe() {
        val ws = hardwareHub.realEspGateway.webSocketClient
        if (ws.state.value == WebSocketChannelState.CONNECTED || ws.state.value == WebSocketChannelState.CONNECTING) {
            ws.disconnect()
            feedback.value = "ESP8266 WebSocket channel closed"
        } else {
            val ip = uiState.value.status.espIp
            ws.connect(ip)
            feedback.value = "Connecting live to ws://$ip:81/ws"
        }
    }

    fun updateEspIp(ip: String) {
        viewModelScope.launch {
            pollingBusy.value = true
            val health = hardwareHub.saveAndCheckEsp8266Ip(ip)
            pollingBusy.value = false
            feedback.value = if (health.isLive) {
                "ESP8266 at ${health.ip} is LIVE (HTTP 200 • ${health.latencyMs ?: "--"} ms)"
            } else {
                "Saved ${health.ip} — ${health.message}"
            }
        }
    }

    fun clearFeedback() {
        feedback.value = null
    }

    class Factory(
        private val hardwareHub: HardwareSwitchingHub,
        private val connectedDevicesRepository: ConnectedDevicesRepository,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NetworkViewModel(hardwareHub, connectedDevicesRepository, settingsDataStore) as T
    }
}
