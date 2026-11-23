package com.example.homesphere.domain.model

enum class DeviceType {
    FAN,
    DOOR,
    WATER_MONITOR,
    ROOM_LIGHT,
    WIFI_ROUTER,
    BLUETOOTH
}

enum class ConnectionStatus(val label: String) {
    CONNECTED("CONNECTED"),
    CONNECTING("CONNECTING"),
    DISCONNECTED("DISCONNECTED")
}

data class Device(
    val id: String,
    val name: String,
    val deviceType: DeviceType,
    val connectionStatus: ConnectionStatus,
    val operatingStatus: String,
    val lastUpdated: Long,
    val quickActionEnabled: Boolean = false,
    val isPoweredOn: Boolean = false
)

enum class FanMode(val label: String, val description: String) {
    NORMAL("Normal", "Steady airflow curve"),
    SLEEP("Sleep", "Quiet low-RPM night breeze"),
    TURBO("Turbo", "Maximum cooling circulation")
}

enum class FanSpeedPreset(val label: String, val speedPercent: Int) {
    LOW("Low", 30),
    MEDIUM("Medium", 65),
    HIGH("High", 100)
}

data class FanState(
    val isOn: Boolean = false,
    val speedPercent: Int = 0,
    val mode: FanMode = FanMode.NORMAL,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val operatingStatus: String
        get() = when {
            connectionStatus != ConnectionStatus.CONNECTED -> "OFF"
            !isOn -> "OFF"
            else -> "ON • $speedPercent% (${mode.label})"
        }
}

enum class DoorSensorState(val label: String) {
    CLOSED("CLOSED"),
    OPEN("OPEN"),
    UNKNOWN("OFFLINE")
}

enum class DoorLockState(val label: String) {
    LOCKED("LOCKED"),
    UNLOCKED("UNLOCKED"),
    UNKNOWN("UNCONFIRMED")
}

data class DoorState(
    val sensorState: DoorSensorState = DoorSensorState.UNKNOWN,
    val lockState: DoorLockState = DoorLockState.UNKNOWN,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val operatingStatus: String
        get() = if (connectionStatus != ConnectionStatus.CONNECTED) {
            "OFFLINE"
        } else {
            "${lockState.label} • ${sensorState.label}"
        }
}

enum class LightMode(val label: String, val kelvin: String) {
    WARM("Warm", "2700K"),
    COOL("Cool", "6000K"),
    READING("Reading", "4500K"),
    NIGHT("Night", "2000K")
}

data class LightState(
    val isOn: Boolean = false,
    val brightnessPercent: Int = 0,
    val mode: LightMode = LightMode.WARM,
    val measuredWatts: Float? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val operatingStatus: String
        get() = when {
            connectionStatus != ConnectionStatus.CONNECTED -> "OFF"
            !isOn -> "OFF"
            else -> "ON • $brightnessPercent% (${mode.label})"
        }
}

data class WaterLevelReading(
    val percentage: Int = 0,
    val currentLitres: Int = 0,
    val tankCapacity: Int = 1000,
    val timestamp: Long = System.currentTimeMillis(),
    val sensorConnected: Boolean = false,
    val lowThresholdPercent: Int = 20,
    val highThresholdPercent: Int = 90
) {
    val remainingCapacityLitres: Int
        get() = (tankCapacity - currentLitres).coerceAtLeast(0)

    val connectionStatus: ConnectionStatus
        get() = if (sensorConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED

    val operatingStatus: String
        get() = when {
            !sensorConnected -> "OFFLINE"
            percentage <= lowThresholdPercent -> "$percentage% • LOW ALERT"
            percentage >= highThresholdPercent -> "$percentage% • FULL"
            else -> "$percentage% • ${currentLitres}L"
        }
}
