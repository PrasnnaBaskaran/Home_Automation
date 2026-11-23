package com.example.homesphere.hardware.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.homesphere.domain.model.BluetoothDeviceCategory
import com.example.homesphere.domain.model.BluetoothDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BluetoothAdapterStatus(val label: String) {
    UNAVAILABLE("UNAVAILABLE"),
    OFF("DISABLED"),
    ON("ENABLED"),
    PERMISSION_REQUIRED("PERMISSION NEEDED")
}

data class BluetoothHardwareSnapshot(
    val adapterStatus: BluetoothAdapterStatus = BluetoothAdapterStatus.OFF,
    val isScanning: Boolean = false,
    val pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val discoveredDevices: List<BluetoothDeviceInfo> = emptyList(),
    val statusMessage: String = "Ready to inspect Bluetooth adapter",
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Real Android Bluetooth Manager adhering to Android 12+ (BLUETOOTH_SCAN/CONNECT) and
 * Android <=11 (LOCATION) permission rules. Never auto-pairs or auto-connects without user approval.
 */
class AndroidBluetoothManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _snapshot = MutableStateFlow(BluetoothHardwareSnapshot())
    val snapshot: StateFlow<BluetoothHardwareSnapshot> = _snapshot.asStateFlow()

    private var receiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> refreshState()
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _snapshot.update { it.copy(isScanning = true, statusMessage = "Scanning for nearby Bluetooth devices…") }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _snapshot.update {
                        it.copy(
                            isScanning = false,
                            statusMessage = if (it.discoveredDevices.isEmpty()) {
                                "Scan complete • No nearby discoverable devices found"
                            } else {
                                "Scan complete • ${it.discoveredDevices.size} device(s) discovered"
                            }
                        )
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    if (!hasConnectPermission()) return
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        .takeIf { it != Short.MIN_VALUE }?.toInt()
                    device?.let { found ->
                        val info = mapDevice(found, rssi = rssi)
                        _snapshot.update { current ->
                            val existing = current.discoveredDevices.filterNot { it.address == info.address }
                            current.copy(discoveredDevices = existing + info)
                        }
                    }
                }
            }
        }
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasAllPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(bluetoothReceiver, filter)
        }
        receiverRegistered = true
        refreshState()
    }

    fun unregisterReceiver() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(bluetoothReceiver) }
        receiverRegistered = false
    }

    @SuppressLint("MissingPermission")
    fun refreshState() {
        val now = System.currentTimeMillis()
        val btAdapter = adapter
        if (btAdapter == null) {
            _snapshot.update {
                it.copy(
                    adapterStatus = BluetoothAdapterStatus.UNAVAILABLE,
                    isScanning = false,
                    pairedDevices = emptyList(),
                    statusMessage = "Bluetooth hardware adapter is unavailable on this device",
                    lastUpdated = now
                )
            }
            return
        }

        if (!hasAllPermissions()) {
            _snapshot.update {
                it.copy(
                    adapterStatus = BluetoothAdapterStatus.PERMISSION_REQUIRED,
                    isScanning = false,
                    statusMessage = "Grant Bluetooth permissions to scan and view paired devices",
                    lastUpdated = now
                )
            }
            return
        }

        val isEnabled = runCatching { btAdapter.isEnabled }.getOrDefault(false)
        if (!isEnabled) {
            _snapshot.update {
                it.copy(
                    adapterStatus = BluetoothAdapterStatus.OFF,
                    isScanning = false,
                    pairedDevices = emptyList(),
                    statusMessage = "Bluetooth is turned OFF. Open Bluetooth Settings to enable.",
                    lastUpdated = now
                )
            }
            return
        }

        val paired = runCatching {
            btAdapter.bondedDevices?.map { mapDevice(it, rssi = null) } ?: emptyList()
        }.getOrDefault(emptyList())

        _snapshot.update {
            it.copy(
                adapterStatus = BluetoothAdapterStatus.ON,
                pairedDevices = paired,
                statusMessage = if (it.isScanning) {
                    "Scanning for nearby Bluetooth devices…"
                } else {
                    "Bluetooth ready • ${paired.size} paired device(s)"
                },
                lastUpdated = now
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        val btAdapter = adapter ?: return false
        if (!hasAllPermissions() || !btAdapter.isEnabled) {
            refreshState()
            return false
        }
        return runCatching {
            if (btAdapter.isDiscovering) btAdapter.cancelDiscovery()
            _snapshot.update { it.copy(discoveredDevices = emptyList()) }
            val started = btAdapter.startDiscovery()
            if (started) {
                _snapshot.update {
                    it.copy(isScanning = true, statusMessage = "Scanning for nearby Bluetooth devices…")
                }
            }
            started
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val btAdapter = adapter ?: return
        if (!hasAllPermissions()) return
        runCatching {
            if (btAdapter.isDiscovering) btAdapter.cancelDiscovery()
        }
        _snapshot.update { it.copy(isScanning = false) }
    }

    fun openBluetoothSettingsIntent(): Intent {
        return Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @SuppressLint("MissingPermission")
    private fun mapDevice(device: BluetoothDevice, rssi: Int?): BluetoothDeviceInfo {
        val majorClass = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
        val category = when (majorClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> BluetoothDeviceCategory.AUDIO
            BluetoothClass.Device.Major.WEARABLE -> BluetoothDeviceCategory.WEARABLE
            BluetoothClass.Device.Major.COMPUTER -> BluetoothDeviceCategory.COMPUTER
            BluetoothClass.Device.Major.PHONE -> BluetoothDeviceCategory.PHONE
            BluetoothClass.Device.Major.PERIPHERAL -> BluetoothDeviceCategory.PERIPHERAL
            BluetoothClass.Device.Major.HEALTH,
            BluetoothClass.Device.Major.TOY -> BluetoothDeviceCategory.SMART_HOME
            else -> BluetoothDeviceCategory.UNKNOWN
        }
        val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Unnamed Device"
        val isBonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
        return BluetoothDeviceInfo(
            address = device.address ?: "Unknown",
            name = name,
            category = category,
            rssi = rssi,
            isPaired = isBonded,
            isConnected = false
        )
    }
}
