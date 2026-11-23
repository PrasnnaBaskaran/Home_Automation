# HomeSphere – Smart Home Automation & Monitoring

> **Your Home, Connected.**

HomeSphere is a modern, hardware-independent Android application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room**, **DataStore**, **Retrofit/OkHttp**, **WebSockets**, and **Android Bluetooth & Network APIs**.

It provides a complete smart home control plane out of the box—honestly displaying `DISCONNECTED` / `OFFLINE` hardware states when physical ESP8266 modules are unavailable, while offering clearly labeled **DEMO MODE** simulation repositories for interactive evaluation and development.

---

## Features

- **Main Dashboard**: Dynamic greeting, live date/time, configurable home name, summary KPIs (Total / Online / Offline / Active Rules), global Demo Mode toggle, and 6 interactive device cards.
- **Smart Fan Control**: Animated Compose Canvas spinning 3-blade fan, power toggle, 0–100% speed slider, Low/Medium/High presets, Normal/Sleep/Turbo modes, and activity log.
- **Smart Door Security**: Door sensor & actuator status, hardware confirmation safeguards (never claims physical lock/unlock without hardware confirmation), simulated reed sensor alerts, and access history.
- **Water Level Monitor**: Animated circular liquid wave gauge, current litres & remaining tank capacity, configurable tank capacity & low/high alert thresholds, and historical Room-backed trend graph.
- **Room Light Control**: Ambient glowing fixture visual, brightness dimmer, Warm/Cool/Reading/Night color presets, energy usage placeholder, and lighting history.
- **Wi-Fi Router & Network Analyzer**: Live RSSI, ping latency, download/upload speed graphs, SSID/router/gateway/subnet/ESP8266 IP overview, approximate distance estimation, ESP8266 HTTP polling & WebSocket probe, and router disconnect alert simulation.
- **Connected Device Management**: Category filters (Mobile Phones, Laptops, ESP8266, Routers, Other IoT), Android mDNS (`NsdManager`) discovery, and manual LAN device registration.
- **Bluetooth Device Manager**: Real Android Bluetooth adapter monitoring, API-versioned runtime permissions (`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` on Android 12+, `ACCESS_FINE_LOCATION` on Android ≤11), nearby discovery, paired devices, and direct launch to Android Bluetooth Settings.
- **Automations**: Create, edit, toggle, delete, and simulate local automation rules with Room execution history.
- **Activity & History**: Persistent Room event timeline with date filters, category filters, and clear history.
- **Settings**: DataStore-persisted Home Name, User Profile, Dark/Light/System theme, Temperature Unit, Water Tank defaults, Router & ESP8266 IPs, Global Demo Mode, and local notification preferences.

---

## Setup Instructions

### Prerequisites
- Android Studio (Ladybug / Meerkat or newer)
- JDK 17+
- Android SDK 36 (`minSdk = 26`, `targetSdk = 36`)

### Running the Project
1. Open the project folder in Android Studio.
2. Allow Gradle sync to resolve dependencies from `gradle/libs.versions.toml`.
3. Select an Android device or emulator (API 26+) and click **Run**.

---

## Debug APK Build Instructions

From the project root directory:

```bash
# Build Debug APK
gradle :app:assembleDebug
```

The generated Debug APK is output to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

To install onto a connected Android device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Connecting Real ESP8266 Hardware Later

1. Connect your Android phone and ESP8266 modules to the same local Wi-Fi network.
2. Open **Settings → Hardware, Water & Network → ESP8266 Local IP** and enter your module's IPv4 address (e.g., `192.168.1.50`).
3. Ensure **Global Demo Mode** is turned **OFF** so `HardwareSwitchingHub` routes commands directly to `Esp8266HardwareGateway`.
4. See `ARCHITECTURE.md` for the exact HTTP and WebSocket JSON endpoint contracts.
