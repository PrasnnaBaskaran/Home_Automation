package com.example.homesphere.hardware.abstraction

import com.example.homesphere.domain.model.ConnectionStatus
import com.example.homesphere.domain.model.DoorState
import com.example.homesphere.domain.model.FanMode
import com.example.homesphere.domain.model.FanState
import com.example.homesphere.domain.model.LightMode
import com.example.homesphere.domain.model.LightState
import com.example.homesphere.domain.model.NetworkMetricPoint
import com.example.homesphere.domain.model.NetworkStatus
import com.example.homesphere.domain.model.WaterLevelReading
import kotlinx.coroutines.flow.Flow

data class DeviceStatus(
    val connectionStatus: ConnectionStatus,
    val isPoweredOn: Boolean,
    val operatingSummary: String,
    val lastUpdated: Long
)

interface SmartDeviceController {
    suspend fun getStatus(): DeviceStatus
    suspend fun turnOn(): Result<Unit>
    suspend fun turnOff(): Result<Unit>
}

interface FanController : SmartDeviceController {
    fun observeState(): Flow<FanState>
    suspend fun setSpeed(speed: Int): Result<Unit>
    suspend fun setMode(mode: FanMode): Result<Unit>
    suspend fun syncStatus(): Result<FanState>
}

interface DoorController : SmartDeviceController {
    fun observeState(): Flow<DoorState>
    suspend fun lock(): Result<Unit>
    suspend fun unlock(): Result<Unit>
    suspend fun syncStatus(): Result<DoorState>
}

interface LightController : SmartDeviceController {
    fun observeState(): Flow<LightState>
    suspend fun setBrightness(brightness: Int): Result<Unit>
    suspend fun setMode(mode: LightMode): Result<Unit>
    suspend fun syncStatus(): Result<LightState>
}

interface WaterLevelRepository {
    fun observeWaterLevel(): Flow<WaterLevelReading>
    fun observeHistory(): Flow<List<WaterLevelReading>>
    suspend fun refresh(): Result<WaterLevelReading>
}

interface NetworkMonitorRepository {
    fun observeNetworkStatus(): Flow<NetworkStatus>
    fun observeMetricHistory(): Flow<List<NetworkMetricPoint>>
    suspend fun refreshDiagnostics(): Result<NetworkStatus>
}
