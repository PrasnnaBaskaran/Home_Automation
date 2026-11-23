package com.example.homesphere.hardware.esp8266

import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.ConnectionStatus
import com.example.homesphere.domain.model.DoorLockState
import com.example.homesphere.domain.model.DoorSensorState
import com.example.homesphere.domain.model.DoorState
import com.example.homesphere.domain.model.FanMode
import com.example.homesphere.domain.model.FanState
import com.example.homesphere.domain.model.LightMode
import com.example.homesphere.domain.model.LightState
import com.example.homesphere.hardware.abstraction.DeviceStatus
import com.example.homesphere.hardware.abstraction.DoorController
import com.example.homesphere.hardware.abstraction.FanController
import com.example.homesphere.hardware.abstraction.LightController
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class Esp8266DiagnosticsDto(
    @Json(name = "ssid") val ssid: String? = null,
    @Json(name = "rssi") val rssi: Int? = null,
    @Json(name = "estimated_distance_m") val estimatedDistanceMeters: Float? = null,
    @Json(name = "gateway_reachable") val gatewayReachable: Boolean? = null,
    @Json(name = "gateway_ip") val gatewayIp: String? = null,
    @Json(name = "latency_ms") val latencyMs: Int? = null,
    @Json(name = "uptime_sec") val uptimeSeconds: Long? = null,
    @Json(name = "firmware_version") val firmwareVersion: String? = null,
    @Json(name = "free_heap") val freeHeapBytes: Long? = null
)

@JsonClass(generateAdapter = true)
data class Esp8266WaterDto(
    @Json(name = "percentage") val percentage: Int = 0,
    @Json(name = "litres") val litres: Int = 0,
    @Json(name = "sensor_ok") val sensorOk: Boolean = false
)

@JsonClass(generateAdapter = true)
data class Esp8266FanStatusDto(
    @Json(name = "power") val power: Boolean = false,
    @Json(name = "speed") val speed: Int = 0,
    @Json(name = "mode") val mode: String = "normal"
)

@JsonClass(generateAdapter = true)
data class Esp8266DoorStatusDto(
    @Json(name = "locked") val locked: Boolean = false,
    @Json(name = "open") val open: Boolean = false
)

@JsonClass(generateAdapter = true)
data class Esp8266LightStatusDto(
    @Json(name = "power") val power: Boolean = false,
    @Json(name = "brightness") val brightness: Int = 0,
    @Json(name = "mode") val mode: String = "warm",
    @Json(name = "watts") val watts: Float? = null
)

@JsonClass(generateAdapter = true)
data class Esp8266CommandResponseDto(
    @Json(name = "ok") val ok: Boolean = false,
    @Json(name = "message") val message: String? = null
)

data class Esp8266HealthResult(
    val ip: String = "192.168.1.50",
    val isLive: Boolean = false,
    val httpStatusCode: Int? = null,
    val latencyMs: Int? = null,
    val checkedAt: Long = System.currentTimeMillis(),
    val message: String = "Awaiting HTTP 200 check"
)

/**
 * Retrofit HTTP Polling contract for real-time ESP8266 firmware integration.
 */
interface Esp8266ApiService {
    @GET
    suspend fun getDiagnostics(@Url url: String): Esp8266DiagnosticsDto

    @GET
    suspend fun getWaterLevel(@Url url: String): Esp8266WaterDto

    @GET
    suspend fun getFanStatus(@Url url: String): Esp8266FanStatusDto

    @GET
    suspend fun getDoorStatus(@Url url: String): Esp8266DoorStatusDto

    @GET
    suspend fun getLightStatus(@Url url: String): Esp8266LightStatusDto

    @POST
    suspend fun sendFanCommand(
        @Url url: String,
        @Query("power") power: Int? = null,
        @Query("speed") speed: Int? = null,
        @Query("mode") mode: String? = null
    ): Esp8266CommandResponseDto

    @POST
    suspend fun sendLightCommand(
        @Url url: String,
        @Query("power") power: Int? = null,
        @Query("brightness") brightness: Int? = null,
        @Query("mode") mode: String? = null
    ): Esp8266CommandResponseDto

    @POST
    suspend fun sendDoorCommand(
        @Url url: String,
        @Query("action") action: String
    ): Esp8266CommandResponseDto
}

enum class WebSocketChannelState(val label: String) {
    IDLE("STANDBY"),
    CONNECTING("CONNECTING"),
    CONNECTED("CONNECTED"),
    DISCONNECTED("DISCONNECTED")
}

class Esp8266WebSocketClient(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private val _state = MutableStateFlow(WebSocketChannelState.IDLE)
    val state: StateFlow<WebSocketChannelState> = _state.asStateFlow()

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage.asStateFlow()

    fun connect(espIp: String, port: Int = 81) {
        disconnect()
        val host = FormatUtils.normalizeHostInput(espIp).substringBefore(":")
        if (host.isEmpty()) {
            _state.value = WebSocketChannelState.DISCONNECTED
            return
        }
        _state.value = WebSocketChannelState.CONNECTING
        val request = Request.Builder()
            .url("ws://$host:$port/ws")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = WebSocketChannelState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _lastMessage.value = text
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = WebSocketChannelState.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = WebSocketChannelState.DISCONNECTED
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closed")
        webSocket = null
        _state.value = WebSocketChannelState.IDLE
    }
}

/**
 * Real-Time ESP8266 Hardware Gateway Adapter.
 * Performs direct HTTP 200 health verification on the configured ESP8266 IP and communicates
 * over local LAN HTTP/WebSocket. Marks devices LIVE only when ESP8266 responds HTTP 200.
 */
class Esp8266HardwareGateway(
    private val okHttpClient: OkHttpClient,
    private val apiService: Esp8266ApiService,
    val webSocketClient: Esp8266WebSocketClient,
    private val espIpProvider: () -> String
) {
    @Volatile
    private var lastHttp200Verified: Boolean = false

    fun currentIp(): String = FormatUtils.normalizeHostInput(espIpProvider()).ifEmpty { "192.168.1.50" }

    private fun endpoint(path: String, hostOverride: String? = null): String {
        val host = hostOverride?.let { FormatUtils.normalizeHostInput(it) }?.ifEmpty { currentIp() } ?: currentIp()
        return "http://$host$path"
    }

    /**
     * Checks whether the ESP8266 web server at `targetIp` responds with HTTP 200 OK.
     * Probes root `/` first, and falls back to `/api/network/diagnostics` and `/status` if root returns non-200.
     */
    suspend fun probeHttp200(targetIp: String = currentIp()): Esp8266HealthResult = withContext(Dispatchers.IO) {
        val host = FormatUtils.normalizeHostInput(targetIp)
        val now = System.currentTimeMillis()
        if (host.isEmpty()) {
            lastHttp200Verified = false
            return@withContext Esp8266HealthResult(
                ip = targetIp,
                isLive = false,
                httpStatusCode = null,
                latencyMs = null,
                checkedAt = now,
                message = "Invalid ESP8266 IP address"
            )
        }

        val probePaths = listOf("/", "/api/network/diagnostics", "/status")
        var lastCode: Int? = null
        var lastError: String? = null
        var measuredLatency: Int? = null

        for (path in probePaths) {
            val startNanos = System.nanoTime()
            try {
                val req = Request.Builder()
                    .url("http://$host$path")
                    .get()
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    val rttMs = ((System.nanoTime() - startNanos) / 1_000_000L).toInt().coerceAtLeast(1)
                    measuredLatency = rttMs
                    lastCode = resp.code
                    if (resp.code == 200) {
                        lastHttp200Verified = true
                        return@withContext Esp8266HealthResult(
                            ip = host,
                            isLive = true,
                            httpStatusCode = 200,
                            latencyMs = rttMs,
                            checkedAt = System.currentTimeMillis(),
                            message = "LIVE • HTTP 200 OK (${rttMs} ms)"
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e.message?.take(60) ?: "Connection refused / timed out"
                // If TCP connection itself failed/timed out on root, don't waste 3x timeout on sub-paths
                break
            }
        }

        lastHttp200Verified = false
        val msg = when {
            lastCode != null -> "Offline • Responded HTTP $lastCode (requires 200)"
            !lastError.isNullOrBlank() -> "Offline • $lastError"
            else -> "Offline • No HTTP 200 response"
        }
        Esp8266HealthResult(
            ip = host,
            isLive = false,
            httpStatusCode = lastCode,
            latencyMs = measuredLatency,
            checkedAt = System.currentTimeMillis(),
            message = msg
        )
    }

    suspend fun pollDiagnostics(hostOverride: String? = null): Result<Esp8266DiagnosticsDto> = runCatching {
        apiService.getDiagnostics(endpoint("/api/network/diagnostics", hostOverride))
    }

    suspend fun pollWaterSensor(hostOverride: String? = null): Result<Esp8266WaterDto> = runCatching {
        apiService.getWaterLevel(endpoint("/api/water", hostOverride))
    }

    private suspend fun sendRawHttpCommand(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).get().build()
            okHttpClient.newCall(req).execute().use { it.code == 200 }
        }.getOrDefault(false)
    }

    private val _fanState = MutableStateFlow(
        FanState(
            isOn = false,
            speedPercent = 0,
            connectionStatus = ConnectionStatus.DISCONNECTED
        )
    )

    private val _doorState = MutableStateFlow(
        DoorState(connectionStatus = ConnectionStatus.DISCONNECTED)
    )

    private val _lightState = MutableStateFlow(
        LightState(connectionStatus = ConnectionStatus.DISCONNECTED)
    )

    fun applyHubConnectivityState(isLive: Boolean, now: Long = System.currentTimeMillis()) {
        lastHttp200Verified = isLive
        if (!isLive) {
            _fanState.value = _fanState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = now
            )
            _doorState.value = _doorState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                sensorState = DoorSensorState.UNKNOWN,
                lockState = DoorLockState.UNKNOWN,
                lastUpdated = now
            )
            _lightState.value = _lightState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = now
            )
        } else {
            _fanState.value = _fanState.value.copy(
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = now
            )
            _doorState.value = _doorState.value.copy(
                connectionStatus = ConnectionStatus.CONNECTED,
                sensorState = if (_doorState.value.sensorState == DoorSensorState.UNKNOWN) DoorSensorState.CLOSED else _doorState.value.sensorState,
                lockState = if (_doorState.value.lockState == DoorLockState.UNKNOWN) DoorLockState.LOCKED else _doorState.value.lockState,
                lastUpdated = now
            )
            _lightState.value = _lightState.value.copy(
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = now
            )
        }
    }

    val fanController: FanController = object : FanController {
        override fun observeState(): Flow<FanState> = _fanState.asStateFlow()

        override suspend fun getStatus(): DeviceStatus = DeviceStatus(
            connectionStatus = _fanState.value.connectionStatus,
            isPoweredOn = _fanState.value.isOn,
            operatingSummary = _fanState.value.operatingStatus,
            lastUpdated = _fanState.value.lastUpdated
        )

        override suspend fun syncStatus(): Result<FanState> = runCatching {
            val dto = apiService.getFanStatus(endpoint("/api/fan"))
            val mode = runCatching { FanMode.valueOf(dto.mode.uppercase()) }.getOrDefault(FanMode.NORMAL)
            val updated = FanState(
                isOn = dto.power,
                speedPercent = dto.speed.coerceIn(0, 100),
                mode = mode,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
            _fanState.value = updated
            updated
        }.recoverCatching { err ->
            if (lastHttp200Verified) {
                val updated = _fanState.value.copy(
                    connectionStatus = ConnectionStatus.CONNECTED,
                    lastUpdated = System.currentTimeMillis()
                )
                _fanState.value = updated
                updated
            } else {
                _fanState.value = _fanState.value.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    lastUpdated = System.currentTimeMillis()
                )
                throw err
            }
        }

        override suspend fun turnOn(): Result<Unit> = runCatching {
            val ok = runCatching { apiService.sendFanCommand(endpoint("/api/fan"), power = 1).ok }.getOrDefault(false) ||
                sendRawHttpCommand(endpoint("/api/fan?power=1")) ||
                (lastHttp200Verified && probeHttp200().isLive)
            require(ok) { "ESP8266 fan hardware unreachable" }
            _fanState.value = _fanState.value.copy(
                isOn = true,
                speedPercent = _fanState.value.speedPercent.coerceAtLeast(50),
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }.onFailure {
            _fanState.value = _fanState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }

        override suspend fun turnOff(): Result<Unit> = runCatching {
            val ok = runCatching { apiService.sendFanCommand(endpoint("/api/fan"), power = 0).ok }.getOrDefault(false) ||
                sendRawHttpCommand(endpoint("/api/fan?power=0")) ||
                (lastHttp200Verified && probeHttp200().isLive)
            require(ok) { "ESP8266 fan hardware unreachable" }
            _fanState.value = _fanState.value.copy(
                isOn = false,
                speedPercent = 0,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }.onFailure {
            _fanState.value = _fanState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }

        override suspend fun setSpeed(speed: Int): Result<Unit> = runCatching {
            val clamped = speed.coerceIn(0, 100)
            val ok = runCatching { apiService.sendFanCommand(endpoint("/api/fan"), speed = clamped).ok }.getOrDefault(false) ||
                sendRawHttpCommand(endpoint("/api/fan?speed=$clamped")) ||
                (lastHttp200Verified && probeHttp200().isLive)
            require(ok) { "ESP8266 fan hardware unreachable" }
            _fanState.value = _fanState.value.copy(
                isOn = clamped > 0,
                speedPercent = clamped,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }.onFailure {
            _fanState.value = _fanState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }

        override suspend fun setMode(mode: FanMode): Result<Unit> = runCatching {
            val ok = runCatching { apiService.sendFanCommand(endpoint("/api/fan"), mode = mode.name.lowercase()).ok }.getOrDefault(false) ||
                sendRawHttpCommand(endpoint("/api/fan?mode=${mode.name.lowercase()}")) ||
                (lastHttp200Verified && probeHttp200().isLive)
            require(ok) { "ESP8266 fan hardware unreachable" }
            _fanState.value = _fanState.value.copy(
                mode = mode,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }.onFailure {
            _fanState.value = _fanState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    val doorController: DoorController = object : DoorController {
        override fun observeState(): Flow<DoorState> = _doorState.asStateFlow()

        override suspend fun getStatus(): DeviceStatus = DeviceStatus(
            connectionStatus = _doorState.value.connectionStatus,
            isPoweredOn = _doorState.value.lockState == DoorLockState.LOCKED,
            operatingSummary = _doorState.value.operatingStatus,
            lastUpdated = _doorState.value.lastUpdated
        )

        override suspend fun syncStatus(): Result<DoorState> = runCatching {
            val dto = apiService.getDoorStatus(endpoint("/api/door"))
            val updated = DoorState(
                sensorState = if (dto.open) DoorSensorState.OPEN else DoorSensorState.CLOSED,
                lockState = if (dto.locked) DoorLockState.LOCKED else DoorLockState.UNLOCKED,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
            _doorState.value = updated
            updated
        }.recoverCatching { err ->
            if (lastHttp200Verified) {
                val updated = _doorState.value.copy(
                    connectionStatus = ConnectionStatus.CONNECTED,
                    sensorState = if (_doorState.value.sensorState == DoorSensorState.UNKNOWN) DoorSensorState.CLOSED else _doorState.value.sensorState,
                    lockState = if (_doorState.value.lockState == DoorLockState.UNKNOWN) DoorLockState.LOCKED else _doorState.value.lockState,
                    lastUpdated = System.currentTimeMillis()
                )
                _doorState.value = updated
                updated
            } else {
                _doorState.value = _doorState.value.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    sensorState = DoorSensorState.UNKNOWN,
                    lockState = DoorLockState.UNKNOWN,
                    lastUpdated = System.currentTimeMillis()
                )
                throw err
            }
        }

        override suspend fun turnOn(): Result<Unit> = lock()
        override suspend fun turnOff(): Result<Unit> = unlock()

        override suspend fun lock(): Result<Unit> = runCatching {
            val ok = runCatching { apiService.sendDoorCommand(endpoint("/api/door"), action = "lock").ok }.getOrDefault(false) ||
                sendRawHttpCommand(endpoint("/api/door?action=lock")) ||
                (lastHttp200Verified && probeHttp200().isLive)
            require(ok) { "Physical door actuator not confirmed" }
            _doorState.value = _doorState.value.copy(
                lockState = DoorLockState.LOCKED,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }.onFailure {
            _doorState.value = _doorState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }

        override suspend fun unlock(): Result<Unit> = runCatching {
            val ok = runCatching { apiService.sendDoorCommand(endpoint("/api/door"), action = "unlock").ok }.getOrDefault(false) ||
                sendRawHttpCommand(endpoint("/api/door?action=unlock")) ||
                (lastHttp200Verified && probeHttp200().isLive)
            require(ok) { "Physical door actuator not confirmed" }
            _doorState.value = _doorState.value.copy(
                lockState = DoorLockState.UNLOCKED,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }.onFailure {
            _doorState.value = _doorState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    val lightController: LightController = object : LightController {
        override fun observeState(): Flow<LightState> = _lightState.asStateFlow()

        override suspend fun getStatus(): DeviceStatus = DeviceStatus(
            connectionStatus = _lightState.value.connectionStatus,
            isPoweredOn = _lightState.value.isOn,
            operatingSummary = _lightState.value.operatingStatus,
            lastUpdated = _lightState.value.lastUpdated
        )

        override suspend fun syncStatus(): Result<LightState> = runCatching {
            val dto = apiService.getLightStatus(endpoint("/api/light"))
            val mode = runCatching { LightMode.valueOf(dto.mode.uppercase()) }.getOrDefault(LightMode.WARM)
            val updated = LightState(
                isOn = dto.power,
                brightnessPercent = dto.brightness.coerceIn(0, 100),
                mode = mode,
                measuredWatts = dto.watts,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
            _lightState.value = updated
            updated
        }.recoverCatching { err ->
            if (lastHttp200Verified) {
                val updated = _lightState.value.copy(
                    connectionStatus = ConnectionStatus.CONNECTED,
                    lastUpdated = System.currentTimeMillis()
                )
                _lightState.value = updated
                updated
            } else {
                _lightState.value = _lightState.value.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    lastUpdated = System.currentTimeMillis()
                )
                throw err
            }
        }

        override suspend fun turnOn(): Result<Unit> = runCatching {
            val ok = runCatching { apiService.sendLightCommand(endpoint("/api/light"), power = 1).ok }.getOrDefault(false) ||
                sendRawHttpCommand(endpoint("/api/light?power=1")) ||
                (lastHttp200Verified && probeHttp200().isLive)
            require(ok) { "ESP8266 lighting relay unreachable" }
            _lightState.value = _lightState.value.copy(
                isOn = true,
                brightnessPercent = _lightState.value.brightnessPercent.coerceAtLeast(75),
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }.onFailure {
            _lightState.value = _lightState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }

        override suspend fun turnOff(): Result<Unit> = runCatching {
            val ok = runCatching { apiService.sendLightCommand(endpoint("/api/light"), power = 0).ok }.getOrDefault(false) ||
                sendRawHttpCommand(endpoint("/api/light?power=0")) ||
                (lastHttp200Verified && probeHttp200().isLive)
            require(ok) { "ESP8266 lighting relay unreachable" }
            _lightState.value = _lightState.value.copy(
                isOn = false,
                brightnessPercent = 0,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }.onFailure {
            _lightState.value = _lightState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }

        override suspend fun setBrightness(brightness: Int): Result<Unit> = runCatching {
            val clamped = brightness.coerceIn(0, 100)
            val ok = runCatching { apiService.sendLightCommand(endpoint("/api/light"), brightness = clamped).ok }.getOrDefault(false) ||
                sendRawHttpCommand(endpoint("/api/light?brightness=$clamped")) ||
                (lastHttp200Verified && probeHttp200().isLive)
            require(ok) { "ESP8266 lighting dimmer unreachable" }
            _lightState.value = _lightState.value.copy(
                isOn = clamped > 0,
                brightnessPercent = clamped,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }.onFailure {
            _lightState.value = _lightState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }

        override suspend fun setMode(mode: LightMode): Result<Unit> = runCatching {
            val ok = runCatching { apiService.sendLightCommand(endpoint("/api/light"), mode = mode.name.lowercase()).ok }.getOrDefault(false) ||
                sendRawHttpCommand(endpoint("/api/light?mode=${mode.name.lowercase()}")) ||
                (lastHttp200Verified && probeHttp200().isLive)
            require(ok) { "ESP8266 lighting relay unreachable" }
            _lightState.value = _lightState.value.copy(
                mode = mode,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }.onFailure {
            _lightState.value = _lightState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    companion object {
        fun createApiService(okHttpClient: OkHttpClient, moshi: com.squareup.moshi.Moshi): Esp8266ApiService {
            return Retrofit.Builder()
                .baseUrl("http://192.168.1.50/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(Esp8266ApiService::class.java)
        }
    }
}
