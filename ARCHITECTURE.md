# HomeSphere Architecture Documentation

## 1. Architectural Pattern

HomeSphere follows **Clean Architecture + MVVM** with reactive unidirectional data flow (`StateFlow` / `Flow` + `collectAsStateWithLifecycle`) and **Constructor Injection** managed via `AppContainer` (`HomeSphereApplication`).

```text
com.example.homesphere/
├── core/
│   ├── design/          # Material 3 Dark/Light Theme, Typography, Reusable Glass Cards & Canvas Charts
│   ├── navigation/      # Adaptive Navigation Compose (Bottom NavigationBar / Tablet NavigationRail)
│   ├── database/        # Room Database (Entities, DAOs, HomeSphereDatabase)
│   ├── notifications/   # Local Android NotificationChannel & POST_NOTIFICATIONS manager
│   └── utils/           # Formatting, relative timestamps, uptime, RSSI path-loss distance
├── domain/
│   └── model/           # Immutable domain models (Device, FanState, DoorState, WaterLevelReading, NetworkStatus, etc.)
├── hardware/
│   ├── abstraction/     # SmartDeviceController, FanController, DoorController, LightController, WaterLevelRepository, NetworkMonitorRepository
│   ├── esp8266/         # Retrofit HTTP polling API, Moshi DTOs, OkHttp WebSocket client, Esp8266HardwareGateway
│   ├── router/          # RouterApiClient interface + AndroidNetworkInspector (ConnectivityManager & NsdManager mDNS)
│   └── bluetooth/       # AndroidBluetoothManager (Adapter observer, BroadcastReceiver discovery, API-level permissions)
├── data/
│   ├── local/           # SettingsDataStore (Jetpack DataStore Preferences)
│   └── repository/      # Room repositories, isolated Simulated* repositories, HardwareSwitchingHub
├── di/                  # AppContainer manual constructor-injection graph
└── features/            # 11 self-contained feature modules (each with its own ViewModel + Screen)
    ├── dashboard/
    ├── fan/
    ├── door/
    ├── water/
    ├── lighting/
    ├── network/
    ├── devices/
    ├── bluetooth/
    ├── automation/
    ├── activity/
    └── settings/
```

---

## 2. Hardware Abstraction Layer (HAL) & Strict Isolation

Real hardware adapters and simulated/demo implementations **never share code or state**:

| Interface | Real Hardware Implementation | Isolated Demo Implementation |
|---|---|---|
| `FanController` | `Esp8266HardwareGateway.fanController` | `SimulatedFanController` |
| `DoorController` | `Esp8266HardwareGateway.doorController` | `SimulatedDoorController` |
| `LightController` | `Esp8266HardwareGateway.lightController` | `SimulatedLightController` |
| `WaterLevelRepository` | `Esp8266HardwareGateway.pollWaterSensor()` | `SimulatedWaterLevelRepository` |
| `NetworkMonitorRepository` | `AndroidNetworkInspector` + `RouterApiClient` + ESP8266 diagnostics | `SimulatedNetworkMonitorRepository` |

`HardwareSwitchingHub` observes `SettingsDataStore.demoModeEnabled` (and per-screen demo overrides) and dynamically switches `Flow` streams via `flatMapLatest`.

- When hardware is absent and Demo Mode is **OFF**, devices strictly report `DISCONNECTED` / `OFFLINE` / `SENSOR DISCONNECTED`.
- Whenever simulated values are shown, the UI renders explicit `DEMO MODE` badges and banners.

---

## 3. ESP8266 Integration Contract

### HTTP Polling Endpoints (`Esp8266ApiService`)
Configured base IP is editable at runtime in Settings (`http://<espIp>/...`):

- `GET /api/network/diagnostics`
  ```json
  {
    "ssid": "HomeSphere_5G",
    "rssi": -52,
    "estimated_distance_m": 3.8,
    "gateway_reachable": true,
    "gateway_ip": "192.168.1.1",
    "latency_ms": 14,
    "uptime_sec": 86400,
    "firmware_version": "1.2.0",
    "free_heap": 32768
  }
  ```
- `GET /api/water` → `{ "percentage": 72, "litres": 720, "sensor_ok": true }`
- `POST /api/fan?power=1&speed=65&mode=normal` → `{ "ok": true }`
- `POST /api/light?power=1&brightness=80&mode=warm` → `{ "ok": true }`
- `POST /api/door?action=lock|unlock` → `{ "ok": true }`

### WebSocket Real-Time Stream (`Esp8266WebSocketClient`)
- Connects to `ws://<espIp>:81/ws` via `OkHttpClient.newWebSocket`.
- Exposes reactive `StateFlow<WebSocketChannelState>` and `StateFlow<String?>` for future zero-latency sensor push updates without touching any Compose UI.

---

## 4. Persistence & Permissions

- **Room Database (`homesphere_local.db`)**: Stores `activity_events`, `automation_rules`, `water_history`, and `connected_devices`.
- **DataStore (`homesphere_settings`)**: Persists Home Name, User Profile, Theme Mode, Temperature Unit, Tank Capacity, Low/High Water Thresholds, Router IP, ESP8266 IP, Demo Mode, and Notification toggles.
- **Permissions**:
  - Bluetooth: Requests `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` on Android 12+ (`neverForLocation`) and `ACCESS_FINE_LOCATION` on Android ≤11.
  - Notifications: Requests `POST_NOTIFICATIONS` on Android 13+ before posting alerts.
