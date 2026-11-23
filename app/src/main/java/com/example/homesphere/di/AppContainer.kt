package com.example.homesphere.di

import android.content.Context
import com.example.homesphere.core.database.HomeSphereDatabase
import com.example.homesphere.core.notifications.HomeSphereNotificationManager
import com.example.homesphere.data.local.SettingsDataStore
import com.example.homesphere.data.repository.ActivityRepository
import com.example.homesphere.data.repository.AutomationRepository
import com.example.homesphere.data.repository.ConnectedDevicesRepository
import com.example.homesphere.data.repository.HardwareSwitchingHub
import com.example.homesphere.data.repository.RealtimeNetworkMonitorRepository
import com.example.homesphere.data.repository.RealtimeWaterLevelRepository
import com.example.homesphere.hardware.bluetooth.AndroidBluetoothManager
import com.example.homesphere.hardware.esp8266.Esp8266HardwareGateway
import com.example.homesphere.hardware.esp8266.Esp8266WebSocketClient
import com.example.homesphere.hardware.router.AndroidNetworkInspector
import com.example.homesphere.hardware.router.RouterApiClient
import com.example.homesphere.hardware.router.StubRouterApiClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: HomeSphereDatabase = HomeSphereDatabase.getInstance(appContext)
    val settingsDataStore = SettingsDataStore(appContext)
    val notificationManager = HomeSphereNotificationManager(appContext)

    val activityRepository = ActivityRepository(database.activityEventDao())
    val automationRepository = AutomationRepository(database.automationRuleDao())
    val connectedDevicesRepository = ConnectedDevicesRepository(database.connectedDeviceDao())

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val androidNetworkInspector = AndroidNetworkInspector(appContext)
    val routerApiClient: RouterApiClient = StubRouterApiClient()
    val bluetoothManager = AndroidBluetoothManager(appContext)

    @Volatile
    private var latestRouterIp = "192.168.1.1"
    @Volatile
    private var latestEspIp = "192.168.1.50"
    @Volatile
    private var latestTankCapacity = 1000
    @Volatile
    private var latestLowThreshold = 20
    @Volatile
    private var latestHighThreshold = 90

    private val esp8266ApiService = Esp8266HardwareGateway.createApiService(okHttpClient, moshi)
    private val esp8266WebSocketClient = Esp8266WebSocketClient(okHttpClient)

    val esp8266HardwareGateway = Esp8266HardwareGateway(
        okHttpClient = okHttpClient,
        apiService = esp8266ApiService,
        webSocketClient = esp8266WebSocketClient,
        espIpProvider = { latestEspIp }
    )

    private val realtimeWater = RealtimeWaterLevelRepository(
        espGateway = esp8266HardwareGateway,
        waterHistoryDao = database.waterHistoryDao(),
        capacityProvider = { latestTankCapacity },
        lowThresholdProvider = { latestLowThreshold },
        highThresholdProvider = { latestHighThreshold }
    )

    private val realtimeNetwork = RealtimeNetworkMonitorRepository(
        inspector = androidNetworkInspector,
        espGateway = esp8266HardwareGateway,
        routerIpProvider = { latestRouterIp },
        espIpProvider = { latestEspIp },
        scope = applicationScope
    )

    val hardwareHub = HardwareSwitchingHub(
        settingsDataStore = settingsDataStore,
        realEspGateway = esp8266HardwareGateway,
        realtimeWater = realtimeWater,
        realtimeNetwork = realtimeNetwork,
        bluetoothManager = bluetoothManager,
        connectedDevicesRepository = connectedDevicesRepository,
        activityRepository = activityRepository,
        notificationManager = notificationManager,
        onEspIpChangedImmediate = { newIp -> latestEspIp = newIp },
        scope = applicationScope
    )

    init {
        applicationScope.launch {
            settingsDataStore.settingsFlow.collect { cfg ->
                latestRouterIp = cfg.routerIp
                latestEspIp = cfg.esp8266Ip
                latestTankCapacity = cfg.tankCapacityLitres
                latestLowThreshold = cfg.lowWaterThresholdPercent
                latestHighThreshold = cfg.highWaterThresholdPercent
            }
        }
        applicationScope.launch {
            activityRepository.recordStartupIfEmpty()
            automationRepository.seedDefaultRulesIfEmpty()
            connectedDevicesRepository.syncRealLocalEndpointsIfEmpty(androidNetworkInspector, latestEspIp)
        }
    }
}
