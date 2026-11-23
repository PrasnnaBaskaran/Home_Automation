package com.example.homesphere.domain.model

data class NetworkStatus(
    val ssid: String = "Unavailable",
    val routerName: String = "Local Gateway",
    val routerIp: String = "192.168.1.1",
    val gatewayIp: String = "Unavailable",
    val localIp: String = "Unavailable",
    val espIp: String = "192.168.1.50",
    val subnetMask: String = "255.255.255.0",
    val rssi: Int? = null,
    val estimatedDistanceMeters: Float? = null,
    val latencyMs: Int? = null,
    val downloadSpeedMbps: Float = 0f,
    val uploadSpeedMbps: Float = 0f,
    val internetAvailable: Boolean = false,
    val isWifiConnected: Boolean = false,
    val uptimeSeconds: Long = 0L,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val espReachable: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class NetworkMetricPoint(
    val timestamp: Long,
    val rssi: Int?,
    val latencyMs: Int?,
    val downloadSpeedMbps: Float,
    val uploadSpeedMbps: Float
)

enum class DiscoveredDeviceCategory(val label: String) {
    MOBILE_PHONE("Mobile Phones"),
    LAPTOP("Laptops"),
    ESP8266("ESP8266 Devices"),
    ROUTER("Routers"),
    OTHER_IOT("Other IoT")
}

data class ConnectedDevice(
    val id: Long = 0L,
    val name: String,
    val ipAddress: String,
    val macAddress: String?,
    val category: DiscoveredDeviceCategory,
    val isOnline: Boolean,
    val lastSeen: Long,
    val connectionDetails: String,
    val isManual: Boolean = false
)

enum class BluetoothDeviceCategory(val label: String) {
    AUDIO("Audio"),
    WEARABLE("Wearable"),
    SMART_HOME("Smart Home"),
    COMPUTER("Computer"),
    PHONE("Phone"),
    PERIPHERAL("Peripheral"),
    UNKNOWN("Bluetooth Device")
}

data class BluetoothDeviceInfo(
    val address: String,
    val name: String,
    val category: BluetoothDeviceCategory,
    val rssi: Int?,
    val isPaired: Boolean,
    val isConnected: Boolean
)

enum class AutomationTriggerType(val label: String) {
    SCHEDULE_TIME("Scheduled Time"),
    WATER_LOW("Water Level Below Threshold"),
    ROUTER_DISCONNECTED("Router Disconnected"),
    DOOR_ACTIVITY("Door Security Event")
}

enum class AutomationActionType(val label: String) {
    TURN_ON_LIGHT("Turn On Room Light"),
    SET_FAN_SPEED("Set Smart Fan Speed"),
    SEND_NOTIFICATION("Send Local Notification"),
    SECURITY_ALERT("Trigger Door Security Alert")
}

data class AutomationRule(
    val id: Long = 0L,
    val name: String,
    val trigger: String,
    val action: String,
    val triggerType: AutomationTriggerType,
    val actionType: AutomationActionType,
    val enabled: Boolean = true,
    val lastExecutedAt: Long? = null,
    val executionCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ActivityCategory(val label: String) {
    FAN("Fan"),
    DOOR("Door"),
    LIGHT("Light"),
    WATER("Water"),
    NETWORK("Network"),
    BLUETOOTH("Bluetooth"),
    AUTOMATION("Automation"),
    SYSTEM("System")
}

enum class EventSeverity { INFO, WARNING, ALERT }

data class ActivityEvent(
    val id: Long = 0L,
    val title: String,
    val details: String,
    val category: ActivityCategory,
    val timestamp: Long = System.currentTimeMillis(),
    val severity: EventSeverity = EventSeverity.INFO
)

enum class AppThemeMode(val label: String) {
    DARK("Dark (Default)"),
    LIGHT("Light"),
    SYSTEM("System")
}

enum class TemperatureUnit(val label: String, val symbol: String) {
    CELSIUS("Celsius (°C)", "°C"),
    FAHRENHEIT("Fahrenheit (°F)", "°F")
}

data class HomeSphereSettings(
    val homeName: String = "Skyline Residence",
    val userName: String = "Alex Morgan",
    val userEmail: String = "Home Administrator",
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val tankCapacityLitres: Int = 1000,
    val lowWaterThresholdPercent: Int = 20,
    val highWaterThresholdPercent: Int = 90,
    val routerIp: String = "192.168.1.1",
    val esp8266Ip: String = "192.168.1.50",
    val notificationsEnabled: Boolean = true,
    val lowWaterAlertEnabled: Boolean = true,
    val doorAlertEnabled: Boolean = true,
    val routerDisconnectAlertEnabled: Boolean = true,
    val automationAlertEnabled: Boolean = true
)
