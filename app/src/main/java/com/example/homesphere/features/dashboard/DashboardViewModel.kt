package com.example.homesphere.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.data.repository.ActivityRepository
import com.example.homesphere.data.repository.AutomationRepository
import com.example.homesphere.data.repository.HardwareSwitchingHub
import com.example.homesphere.domain.model.ConnectionStatus
import com.example.homesphere.domain.model.Device
import com.example.homesphere.domain.model.DeviceType
import com.example.homesphere.hardware.bluetooth.AndroidBluetoothManager
import com.example.homesphere.hardware.bluetooth.BluetoothAdapterStatus
import com.example.homesphere.hardware.esp8266.Esp8266HealthResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DashboardUiState(
    val greeting: String = FormatUtils.getGreeting(),
    val formattedDateTime: String = FormatUtils.formatFullDateTime(System.currentTimeMillis()),
    val nowMillis: Long = System.currentTimeMillis(),
    val homeName: String = "Skyline Residence",
    val userName: String = "Alex Morgan",
    val esp8266Ip: String = "192.168.1.50",
    val espHealth: Esp8266HealthResult = Esp8266HealthResult(),
    val lastConnectionCheckAt: Long = System.currentTimeMillis(),
    val isSyncingHardware: Boolean = false,
    val totalDevices: Int = 6,
    val onlineDevices: Int = 0,
    val offlineDevices: Int = 6,
    val activeAutomations: Int = 0,
    val recentAlertCount: Int = 0,
    val devices: List<Device> = emptyList(),
    val snackbarMessage: String? = null
)

class DashboardViewModel(
    private val hardwareHub: HardwareSwitchingHub,
    private val automationRepository: AutomationRepository,
    private val activityRepository: ActivityRepository,
    private val bluetoothManager: AndroidBluetoothManager
) : ViewModel() {

    private val clockFlow = MutableStateFlow(System.currentTimeMillis())
    private val snackbarFlow = MutableStateFlow<String?>(null)

    init {
        bluetoothManager.refreshState()
        viewModelScope.launch {
            while (isActive) {
                clockFlow.value = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    private val deviceListFlow = combine(
        hardwareHub.fanState,
        hardwareHub.doorState,
        hardwareHub.waterReading,
        hardwareHub.lightState,
        hardwareHub.networkStatus,
        bluetoothManager.snapshot
    ) { values ->
        val fan = values[0] as com.example.homesphere.domain.model.FanState
        val door = values[1] as com.example.homesphere.domain.model.DoorState
        val water = values[2] as com.example.homesphere.domain.model.WaterLevelReading
        val light = values[3] as com.example.homesphere.domain.model.LightState
        val network = values[4] as com.example.homesphere.domain.model.NetworkStatus
        val bt = values[5] as com.example.homesphere.hardware.bluetooth.BluetoothHardwareSnapshot

        val btConnStatus = when (bt.adapterStatus) {
            BluetoothAdapterStatus.ON -> ConnectionStatus.CONNECTED
            else -> ConnectionStatus.DISCONNECTED
        }
        val btOperating = when (bt.adapterStatus) {
            BluetoothAdapterStatus.ON -> "${bt.pairedDevices.size} Paired"
            BluetoothAdapterStatus.OFF -> "ADAPTER OFF"
            BluetoothAdapterStatus.PERMISSION_REQUIRED -> "PERMISSION NEEDED"
            BluetoothAdapterStatus.UNAVAILABLE -> "UNAVAILABLE"
        }

        val routerOperating = if (network.connectionStatus != ConnectionStatus.CONNECTED) {
            "OFFLINE"
        } else {
            val rssiPart = network.rssi?.let { " • $it dBm" } ?: network.latencyMs?.let { " • ${it}ms" } ?: ""
            "ONLINE$rssiPart"
        }

        listOf(
            Device(
                id = "smart_fan",
                name = "Smart Fan",
                deviceType = DeviceType.FAN,
                connectionStatus = fan.connectionStatus,
                operatingStatus = fan.operatingStatus,
                lastUpdated = fan.lastUpdated,
                quickActionEnabled = fan.connectionStatus == ConnectionStatus.CONNECTED,
                isPoweredOn = fan.isOn
            ),
            Device(
                id = "smart_door",
                name = "Smart Door",
                deviceType = DeviceType.DOOR,
                connectionStatus = door.connectionStatus,
                operatingStatus = door.operatingStatus,
                lastUpdated = door.lastUpdated,
                quickActionEnabled = false,
                isPoweredOn = false
            ),
            Device(
                id = "water_monitor",
                name = "Water Level Monitor",
                deviceType = DeviceType.WATER_MONITOR,
                connectionStatus = water.connectionStatus,
                operatingStatus = water.operatingStatus,
                lastUpdated = water.timestamp,
                quickActionEnabled = false,
                isPoweredOn = water.sensorConnected
            ),
            Device(
                id = "room_light",
                name = "Room Light",
                deviceType = DeviceType.ROOM_LIGHT,
                connectionStatus = light.connectionStatus,
                operatingStatus = light.operatingStatus,
                lastUpdated = light.lastUpdated,
                quickActionEnabled = light.connectionStatus == ConnectionStatus.CONNECTED,
                isPoweredOn = light.isOn
            ),
            Device(
                id = "wifi_router",
                name = "Wi-Fi Router",
                deviceType = DeviceType.WIFI_ROUTER,
                connectionStatus = network.connectionStatus,
                operatingStatus = routerOperating,
                lastUpdated = network.lastUpdated,
                quickActionEnabled = false,
                isPoweredOn = network.connectionStatus == ConnectionStatus.CONNECTED
            ),
            Device(
                id = "bluetooth_devices",
                name = "Bluetooth Devices",
                deviceType = DeviceType.BLUETOOTH,
                connectionStatus = btConnStatus,
                operatingStatus = btOperating,
                lastUpdated = bt.lastUpdated,
                quickActionEnabled = false,
                isPoweredOn = bt.adapterStatus == BluetoothAdapterStatus.ON
            )
        )
    }

    private val hubMetaFlow = combine(
        hardwareHub.espHealthStatus,
        hardwareHub.lastConnectionCheckAt,
        hardwareHub.isCheckingConnections
    ) { health, lastCheck, checking ->
        Triple(health, lastCheck, checking)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        hardwareHub.settings,
        deviceListFlow,
        automationRepository.allRules,
        activityRepository.allEvents,
        clockFlow,
        hubMetaFlow,
        snackbarFlow
    ) { values ->
        val settings = values[0] as com.example.homesphere.domain.model.HomeSphereSettings
        @Suppress("UNCHECKED_CAST")
        val devices = values[1] as List<Device>
        @Suppress("UNCHECKED_CAST")
        val rules = values[2] as List<com.example.homesphere.domain.model.AutomationRule>
        @Suppress("UNCHECKED_CAST")
        val events = values[3] as List<com.example.homesphere.domain.model.ActivityEvent>
        val now = values[4] as Long
        @Suppress("UNCHECKED_CAST")
        val hubMeta = values[5] as Triple<Esp8266HealthResult, Long, Boolean>
        val snackbar = values[6] as String?

        val onlineCount = devices.count { it.connectionStatus == ConnectionStatus.CONNECTED }
        val offlineCount = devices.size - onlineCount
        val activeAutomations = rules.count { it.enabled }

        DashboardUiState(
            greeting = FormatUtils.getGreeting(),
            formattedDateTime = FormatUtils.formatFullDateTime(now),
            nowMillis = now,
            homeName = settings.homeName,
            userName = settings.userName,
            esp8266Ip = settings.esp8266Ip,
            espHealth = hubMeta.first,
            lastConnectionCheckAt = hubMeta.second,
            isSyncingHardware = hubMeta.third,
            totalDevices = devices.size,
            onlineDevices = onlineCount,
            offlineDevices = offlineCount,
            activeAutomations = activeAutomations,
            recentAlertCount = events.take(10).count {
                it.severity != com.example.homesphere.domain.model.EventSeverity.INFO
            },
            devices = devices,
            snackbarMessage = snackbar
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun syncLiveHardware() {
        viewModelScope.launch {
            val health = hardwareHub.checkAllConnectionsNow(logTransitions = true)
            snackbarFlow.value = if (health.isLive) {
                "All connections synced • ESP8266 LIVE (HTTP 200 • ${health.latencyMs ?: "--"} ms)"
            } else {
                "All connections checked • ESP8266 (${health.ip}): ${health.message}"
            }
        }
    }

    fun updateEsp8266Ip(ip: String) {
        viewModelScope.launch {
            val health = hardwareHub.saveAndCheckEsp8266Ip(ip)
            snackbarFlow.value = if (health.isLive) {
                "ESP8266 at ${health.ip} is LIVE (HTTP 200 • ${health.latencyMs ?: "--"} ms)"
            } else {
                "Saved ${health.ip} — ${health.message}"
            }
        }
    }

    fun onQuickToggle(device: Device) {
        viewModelScope.launch {
            val result = when (device.deviceType) {
                DeviceType.FAN -> hardwareHub.toggleFanPower()
                DeviceType.ROOM_LIGHT -> hardwareHub.toggleLightPower()
                else -> Result.success(Unit)
            }
            if (result.isFailure) {
                snackbarFlow.value = "${device.name} is offline at ${uiState.value.esp8266Ip}"
            }
        }
    }

    fun clearSnackbar() {
        snackbarFlow.value = null
    }

    class Factory(
        private val hardwareHub: HardwareSwitchingHub,
        private val automationRepository: AutomationRepository,
        private val activityRepository: ActivityRepository,
        private val bluetoothManager: AndroidBluetoothManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                hardwareHub,
                automationRepository,
                activityRepository,
                bluetoothManager
            ) as T
        }
    }
}
