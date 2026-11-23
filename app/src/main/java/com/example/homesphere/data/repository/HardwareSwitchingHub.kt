package com.example.homesphere.data.repository

import com.example.homesphere.core.notifications.AlertChannelType
import com.example.homesphere.core.notifications.HomeSphereNotificationManager
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.data.local.SettingsDataStore
import com.example.homesphere.domain.model.ActivityCategory
import com.example.homesphere.domain.model.ConnectionStatus
import com.example.homesphere.domain.model.DoorSensorState
import com.example.homesphere.domain.model.DoorState
import com.example.homesphere.domain.model.EventSeverity
import com.example.homesphere.domain.model.FanMode
import com.example.homesphere.domain.model.FanState
import com.example.homesphere.domain.model.HomeSphereSettings
import com.example.homesphere.domain.model.LightMode
import com.example.homesphere.domain.model.LightState
import com.example.homesphere.domain.model.NetworkMetricPoint
import com.example.homesphere.domain.model.NetworkStatus
import com.example.homesphere.domain.model.WaterLevelReading
import com.example.homesphere.hardware.bluetooth.AndroidBluetoothManager
import com.example.homesphere.hardware.esp8266.Esp8266HardwareGateway
import com.example.homesphere.hardware.esp8266.Esp8266HealthResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real-Time Hardware Hub.
 * - Verifies ESP8266 via HTTP 200 on save and marks it LIVE immediately when HTTP 200 succeeds.
 * - Automatically checks ALL connection statuses (ESP8266 HTTP 200, Fan, Door, Light, Water,
 *   Wi-Fi/Router, Bluetooth) every 30 seconds.
 * - Tracks exact `lastConnectionCheckAt` for live relative timestamps ("5 seconds ago").
 */
class HardwareSwitchingHub(
    private val settingsDataStore: SettingsDataStore,
    val realEspGateway: Esp8266HardwareGateway,
    private val realtimeWater: RealtimeWaterLevelRepository,
    private val realtimeNetwork: RealtimeNetworkMonitorRepository,
    private val bluetoothManager: AndroidBluetoothManager,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val activityRepository: ActivityRepository,
    private val notificationManager: HomeSphereNotificationManager,
    private val onEspIpChangedImmediate: (String) -> Unit,
    scope: CoroutineScope
) {
    companion object {
        const val AUTO_CHECK_INTERVAL_MS = 30_000L
    }

    val settings: StateFlow<HomeSphereSettings> = settingsDataStore.settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = HomeSphereSettings()
    )

    val fanState: StateFlow<FanState> = realEspGateway.fanController.observeState()
        .stateIn(scope, SharingStarted.Eagerly, FanState())

    val doorState: StateFlow<DoorState> = realEspGateway.doorController.observeState()
        .stateIn(scope, SharingStarted.Eagerly, DoorState())

    val lightState: StateFlow<LightState> = realEspGateway.lightController.observeState()
        .stateIn(scope, SharingStarted.Eagerly, LightState())

    val waterReading: StateFlow<WaterLevelReading> = realtimeWater.observeWaterLevel()
        .stateIn(scope, SharingStarted.Eagerly, WaterLevelReading())

    val waterHistory: Flow<List<WaterLevelReading>> = realtimeWater.observeHistory()

    val networkStatus: StateFlow<NetworkStatus> = realtimeNetwork.observeNetworkStatus()
        .stateIn(scope, SharingStarted.Eagerly, NetworkStatus())

    val networkMetricHistory: StateFlow<List<NetworkMetricPoint>> = realtimeNetwork.observeMetricHistory()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _espHealthStatus = MutableStateFlow(
        Esp8266HealthResult(
            ip = settings.value.esp8266Ip,
            isLive = false,
            checkedAt = System.currentTimeMillis(),
            message = "Checking HTTP 200 status…"
        )
    )
    val espHealthStatus: StateFlow<Esp8266HealthResult> = _espHealthStatus.asStateFlow()

    private val _lastConnectionCheckAt = MutableStateFlow(System.currentTimeMillis())
    val lastConnectionCheckAt: StateFlow<Long> = _lastConnectionCheckAt.asStateFlow()

    private val _isCheckingConnections = MutableStateFlow(false)
    val isCheckingConnections: StateFlow<Boolean> = _isCheckingConnections.asStateFlow()

    private val _espLastDiagnosticsMessage = MutableStateFlow("Auto-checking ESP8266 HTTP 200 every 30s")
    val espLastDiagnosticsMessage: StateFlow<String> = _espLastDiagnosticsMessage.asStateFlow()

    private val checkMutex = Mutex()
    private var lastNetworkOnlineState: Boolean? = null
    private var lastEspLiveState: Boolean? = null

    init {
        scope.launch {
            settings.collect {
                realtimeWater.syncWithSettings()
            }
        }

        // Periodic 30-second health & connection status monitor for ALL devices & radios
        scope.launch {
            // Brief settle delay so DataStore emits persisted ESP8266 IP on cold start
            delay(400L)
            while (isActive) {
                checkAllConnectionsNow(logTransitions = true)
                delay(AUTO_CHECK_INTERVAL_MS)
            }
        }

        // Automatically detect real-time router/network disconnection transitions
        scope.launch {
            networkStatus.collect { status ->
                val isOnline = status.connectionStatus == ConnectionStatus.CONNECTED
                val prev = lastNetworkOnlineState
                lastNetworkOnlineState = isOnline
                if (prev == true && !isOnline) {
                    activityRepository.logEvent(
                        title = "Network Disconnected",
                        details = "Active gateway (${status.routerIp}) became unreachable.",
                        category = ActivityCategory.NETWORK,
                        severity = EventSeverity.ALERT
                    )
                    val cfg = settings.value
                    if (cfg.notificationsEnabled && cfg.routerDisconnectAlertEnabled) {
                        notificationManager.sendAlert(
                            type = AlertChannelType.ROUTER_DISCONNECT,
                            title = "Network Disconnected",
                            message = "Your Android device lost connection to ${status.routerIp}."
                        )
                    }
                }
            }
        }
    }

    /**
     * Saves a newly edited ESP8266 IP address to DataStore and immediately checks whether
     * it responds HTTP 200 OK, transitioning the hub and all attached modules LIVE if 200.
     */
    suspend fun saveAndCheckEsp8266Ip(rawIp: String): Esp8266HealthResult {
        val cleanIp = FormatUtils.normalizeHostInput(rawIp)
        if (cleanIp.isEmpty()) {
            return Esp8266HealthResult(
                ip = rawIp,
                isLive = false,
                httpStatusCode = null,
                latencyMs = null,
                checkedAt = System.currentTimeMillis(),
                message = "Please enter a valid IPv4 address (e.g. 192.168.1.50)"
            )
        }
        onEspIpChangedImmediate(cleanIp)
        settingsDataStore.updateEsp8266Ip(cleanIp)
        return checkAllConnectionsNow(overrideEspIp = cleanIp, logTransitions = true)
    }

    /**
     * Checks ALL connection statuses in one coordinated cycle:
     * 1. ESP8266 HTTP 200 probe
     * 2. Smart Fan, Smart Door, Room Light, Water Level endpoints
     * 3. Android Wi-Fi / Router / WAN diagnostics
     * 4. Android Bluetooth adapter & paired devices
     * 5. Connected LAN Devices status
     */
    suspend fun checkAllConnectionsNow(
        overrideEspIp: String? = null,
        logTransitions: Boolean = true
    ): Esp8266HealthResult = checkMutex.withLock {
        _isCheckingConnections.value = true
        try {
            val targetIp = overrideEspIp?.let { FormatUtils.normalizeHostInput(it) }?.ifEmpty { null }
                ?: realEspGateway.currentIp()

            // 1. Refresh Bluetooth adapter status
            bluetoothManager.refreshState()

            // 2. Probe ESP8266 for strict HTTP 200 OK
            val health = realEspGateway.probeHttp200(targetIp)
            _espHealthStatus.value = health
            val now = health.checkedAt

            // 3. Apply live/offline state to Fan, Door, Light & Water
            realEspGateway.applyHubConnectivityState(isLive = health.isLive, now = now)
            if (health.isLive) {
                realEspGateway.fanController.syncStatus()
                realEspGateway.doorController.syncStatus()
                realEspGateway.lightController.syncStatus()
                realtimeWater.refreshWithHubStatus(espHubIsLive = true, hostOverride = targetIp)
            } else {
                realtimeWater.refreshWithHubStatus(espHubIsLive = false, hostOverride = targetIp)
            }

            // 4. Refresh live Android network & ESP8266 diagnostics
            val refreshedNet = realtimeNetwork.refreshDiagnosticsWithEspHealth(
                espIsLive = health.isLive,
                espHostOverride = targetIp
            ).getOrNull()

            // 5. Update diagnostics summary message
            _espLastDiagnosticsMessage.value = if (health.isLive) {
                "LIVE • HTTP 200 OK at http://$targetIp (${health.latencyMs ?: "--"} ms)"
            } else {
                "${health.message} at http://$targetIp"
            }

            // 6. Keep Room connected devices table in sync
            connectedDevicesRepository.updateEspEndpointStatus(targetIp, health.isLive, now)

            // 7. Log ESP8266 live/offline transitions cleanly
            val prevLive = lastEspLiveState
            lastEspLiveState = health.isLive
            if (logTransitions && prevLive != health.isLive) {
                if (health.isLive) {
                    activityRepository.logEvent(
                        title = "ESP8266 Online (HTTP 200)",
                        details = "Verified live HTTP 200 response from http://$targetIp (${health.latencyMs ?: "--"} ms).",
                        category = ActivityCategory.NETWORK,
                        severity = EventSeverity.INFO
                    )
                } else if (prevLive == true) {
                    activityRepository.logEvent(
                        title = "ESP8266 Went Offline",
                        details = "30s health check failed for http://$targetIp (${health.message}).",
                        category = ActivityCategory.NETWORK,
                        severity = EventSeverity.WARNING
                    )
                }
            }

            // Ensure refreshedNet is referenced cleanly
            refreshedNet?.lastUpdated
            _lastConnectionCheckAt.value = System.currentTimeMillis()
            health
        } finally {
            _isCheckingConnections.value = false
        }
    }

    suspend fun syncAllEsp8266Hardware(): Int {
        val health = checkAllConnectionsNow(logTransitions = true)
        return if (health.isLive) 4 else 0
    }

    // ---- FAN ACTIONS ----
    suspend fun syncFanStatus(): Result<FanState> {
        val health = checkAllConnectionsNow(logTransitions = true)
        return if (health.isLive) Result.success(fanState.value)
        else Result.failure(IllegalStateException(health.message))
    }

    suspend fun toggleFanPower(): Result<Unit> {
        val turnOn = !fanState.value.isOn
        val result = if (turnOn) realEspGateway.fanController.turnOn() else realEspGateway.fanController.turnOff()
        if (result.isSuccess) {
            activityRepository.logEvent(
                title = if (turnOn) "Smart Fan Turned ON" else "Smart Fan Turned OFF",
                details = "Confirmed by ESP8266 at ${realEspGateway.currentIp()} (${fanState.value.speedPercent}%).",
                category = ActivityCategory.FAN
            )
        }
        return result
    }

    suspend fun setFanSpeed(speed: Int): Result<Unit> {
        val result = realEspGateway.fanController.setSpeed(speed)
        if (result.isSuccess) {
            activityRepository.logEvent(
                title = "Smart Fan Speed Set to $speed%",
                details = "ESP8266 PWM duty confirmed.",
                category = ActivityCategory.FAN
            )
        }
        return result
    }

    suspend fun setFanMode(mode: FanMode): Result<Unit> {
        val result = realEspGateway.fanController.setMode(mode)
        if (result.isSuccess) {
            activityRepository.logEvent(
                title = "Smart Fan Mode: ${mode.label}",
                details = mode.description,
                category = ActivityCategory.FAN
            )
        }
        return result
    }

    // ---- DOOR ACTIONS ----
    suspend fun syncDoorStatus(): Result<DoorState> {
        val health = checkAllConnectionsNow(logTransitions = true)
        if (health.isLive) {
            val st = doorState.value
            if (st.sensorState == DoorSensorState.OPEN) {
                activityRepository.logEvent(
                    title = "Front Door Sensor: OPEN",
                    details = "Hardware reed sensor reported door open.",
                    category = ActivityCategory.DOOR,
                    severity = EventSeverity.ALERT
                )
                val cfg = settings.value
                if (cfg.notificationsEnabled && cfg.doorAlertEnabled) {
                    notificationManager.sendAlert(
                        type = AlertChannelType.DOOR_ACTIVITY,
                        title = "Door Security Alert",
                        message = "Front door sensor detected an OPEN state."
                    )
                }
            }
            return Result.success(st)
        }
        return Result.failure(IllegalStateException(health.message))
    }

    suspend fun lockDoor(): Result<Unit> {
        val result = realEspGateway.doorController.lock()
        if (result.isSuccess) {
            activityRepository.logEvent(
                title = "Front Door Locked",
                details = "Hardware actuator confirmed deadbolt locked.",
                category = ActivityCategory.DOOR,
                severity = EventSeverity.INFO
            )
            val cfg = settings.value
            if (cfg.notificationsEnabled && cfg.doorAlertEnabled) {
                notificationManager.sendAlert(
                    type = AlertChannelType.DOOR_ACTIVITY,
                    title = "Smart Door Locked",
                    message = "Front door deadbolt confirmed locked."
                )
            }
        }
        return result
    }

    suspend fun unlockDoor(): Result<Unit> {
        val result = realEspGateway.doorController.unlock()
        if (result.isSuccess) {
            activityRepository.logEvent(
                title = "Front Door Unlocked",
                details = "Hardware actuator confirmed door unlocked.",
                category = ActivityCategory.DOOR,
                severity = EventSeverity.WARNING
            )
            val cfg = settings.value
            if (cfg.notificationsEnabled && cfg.doorAlertEnabled) {
                notificationManager.sendAlert(
                    type = AlertChannelType.DOOR_ACTIVITY,
                    title = "Smart Door Unlocked",
                    message = "Front door was unlocked."
                )
            }
        }
        return result
    }

    // ---- LIGHT ACTIONS ----
    suspend fun syncLightStatus(): Result<LightState> {
        val health = checkAllConnectionsNow(logTransitions = true)
        return if (health.isLive) Result.success(lightState.value)
        else Result.failure(IllegalStateException(health.message))
    }

    suspend fun toggleLightPower(): Result<Unit> {
        val turnOn = !lightState.value.isOn
        val result = if (turnOn) realEspGateway.lightController.turnOn() else realEspGateway.lightController.turnOff()
        if (result.isSuccess) {
            activityRepository.logEvent(
                title = if (turnOn) "Room Light Turned ON" else "Room Light Turned OFF",
                details = "Confirmed by ESP8266 (${lightState.value.brightnessPercent}% • ${lightState.value.mode.label}).",
                category = ActivityCategory.LIGHT
            )
        }
        return result
    }

    suspend fun setLightBrightness(brightness: Int): Result<Unit> {
        val result = realEspGateway.lightController.setBrightness(brightness)
        if (result.isSuccess) {
            activityRepository.logEvent(
                title = "Room Light Dimmed to $brightness%",
                details = "ESP8266 dimmer confirmed.",
                category = ActivityCategory.LIGHT
            )
        }
        return result
    }

    suspend fun setLightMode(mode: LightMode): Result<Unit> {
        val result = realEspGateway.lightController.setMode(mode)
        if (result.isSuccess) {
            activityRepository.logEvent(
                title = "Room Light Preset: ${mode.label} (${mode.kelvin})",
                details = "Color temperature applied on ESP8266.",
                category = ActivityCategory.LIGHT
            )
        }
        return result
    }

    // ---- WATER ACTIONS ----
    suspend fun refreshWaterReading(): Result<WaterLevelReading> {
        val health = checkAllConnectionsNow(logTransitions = true)
        val cfg = settings.value
        val reading = waterReading.value
        return if (health.isLive && reading.sensorConnected) {
            if (reading.percentage <= cfg.lowWaterThresholdPercent && reading.currentLitres > 0) {
                activityRepository.logEvent(
                    title = "Low Water Tank Alert (${reading.percentage}%)",
                    details = "${reading.currentLitres}L of ${reading.tankCapacity}L remaining.",
                    category = ActivityCategory.WATER,
                    severity = EventSeverity.ALERT
                )
                if (cfg.notificationsEnabled && cfg.lowWaterAlertEnabled) {
                    notificationManager.sendAlert(
                        type = AlertChannelType.LOW_WATER,
                        title = "Low Water Tank Level (${reading.percentage}%)",
                        message = "Tank level dropped below your ${cfg.lowWaterThresholdPercent}% threshold."
                    )
                }
            }
            Result.success(reading)
        } else {
            Result.failure(IllegalStateException(health.message))
        }
    }

    // ---- NETWORK & ESP8266 ACTIONS ----
    suspend fun pollEsp8266Diagnostics(): Boolean {
        val health = checkAllConnectionsNow(logTransitions = true)
        return health.isLive
    }
}
