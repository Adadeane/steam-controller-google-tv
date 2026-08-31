package com.steamcontroller.googletv.driver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class SteamControllerBleManager(private val context: Context) {

    private val tag = "SteamControllerBle"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appContext = context.applicationContext

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var activeGatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var inputChar: BluetoothGattCharacteristic? = null
    private var targetDevice: BluetoothDevice? = null

    private val nameHints = listOf("Steam", "Steam Ctrl", "Steam Controller", "SteamController", "Valve")

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _packetFlow = MutableSharedFlow<SteamControllerPacket>(extraBufferCapacity = 64)
    val packetFlow: SharedFlow<SteamControllerPacket> = _packetFlow.asSharedFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: ""
            Log.i(tag, "Scan found: ${device.address} ($name)")

            if (isSteamController(name, result)) {
                val current = _discoveredDevices.value.toMutableList()
                if (current.none { it.address == device.address }) {
                    current.add(device)
                    _discoveredDevices.value = current
                    Log.i(tag, "Discovered Steam Controller: '$name' (${device.address}), connecting...")
                    connect(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(tag, "BLE Scan failed: code $errorCode")
            _connectionState.value = BleConnectionState.Error("Scan failed: code $errorCode")
        }
    }

    private fun isSteamController(name: String, result: ScanResult? = null): Boolean {
        if (nameHints.any { hint -> name.contains(hint, ignoreCase = true) }) {
            return true
        }
        val uuids = result?.scanRecord?.serviceUuids
        if (uuids != null && uuids.contains(ParcelUuid(SteamControllerConstants.VALVE_SERVICE_UUID))) {
            return true
        }
        return false
    }

    fun startScan() {
        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = BleConnectionState.Error("Bluetooth is not available")
            return
        }

        // 1. Check all bonded Steam devices
        val bonded = adapter.bondedDevices
        val bondedSteam = bonded?.filter { dev ->
            val n = dev.name ?: ""
            nameHints.any { hint -> n.contains(hint, ignoreCase = true) }
        }

        if (!bondedSteam.isNullOrEmpty()) {
            Log.i(tag, "Found ${bondedSteam.size} bonded Steam Controller(s)")
            _discoveredDevices.value = bondedSteam
            connect(bondedSteam.last())
            return
        }

        // 2. Broad BLE scan fallback
        val scanner = adapter.bluetoothLeScanner ?: run {
            _connectionState.value = BleConnectionState.Error("BLE Scanner is not available")
            return
        }

        _discoveredDevices.value = emptyList()
        _connectionState.value = BleConnectionState.Scanning

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SteamControllerConstants.VALVE_SERVICE_UUID))
            .build()

        try {
            scanner.startScan(listOf(filter), scanSettings, scanCallback)
        } catch (e: Exception) {
            Log.w(tag, "Filtered scan failed, scanning unfiltered: ${e.message}")
            try {
                scanner.startScan(scanCallback)
            } catch (e2: Exception) {
                _connectionState.value = BleConnectionState.Error("Scanner failed: ${e2.message}")
            }
        }
    }

    fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(tag, "Error stopping scan", e)
        }
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        targetDevice = device
        val deviceName = device.name ?: device.address
        _connectionState.value = BleConnectionState.Connecting(deviceName)
        Log.i(tag, "Connecting to GATT on device $deviceName (${device.address})")

        activeGatt?.disconnect()
        activeGatt?.close()

        activeGatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        stopScan()
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
        commandChar = null
        inputChar = null
        targetDevice = null
        _connectionState.value = BleConnectionState.Disconnected
    }

    fun disableLizardMode() {
        val gatt = activeGatt ?: return
        val cmd = commandChar ?: run {
            Log.w(tag, "Command characteristic not available for lizard mode")
            return
        }

        Log.i(tag, "Sending disable lizard mode command (0x85)...")
        @Suppress("DEPRECATION")
        cmd.value = byteArrayOf(0x85.toByte())
        @Suppress("DEPRECATION")
        gatt.writeCharacteristic(cmd)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val name = gatt.device.name ?: gatt.device.address
            Log.i(tag, "onConnectionStateChange: status=$status newState=$newState ($name)")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(tag, "GATT connection failure: status=$status, closing")
                gatt.close()
                activeGatt = null
                _connectionState.value = BleConnectionState.Disconnected
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(tag, "Connected to $name, discovering services...")
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(tag, "Disconnected from $name")
                    gatt.close()
                    activeGatt = null
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val name = gatt.device.name ?: gatt.device.address
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(tag, "Service discovery failed with status $status")
                _connectionState.value = BleConnectionState.Error("Service discovery failed: $status")
                return
            }

            Log.i(tag, "Services discovered for $name. Looking for Valve service...")
            val valveService = gatt.getService(SteamControllerConstants.VALVE_SERVICE_UUID)
            if (valveService == null) {
                Log.e(tag, "Valve Service not found on $name")
                _connectionState.value = BleConnectionState.Error("Valve GATT Service not found")
                return
            }

            inputChar = valveService.getCharacteristic(SteamControllerConstants.INPUT_REPORT_CHAR_UUID)
            commandChar = valveService.getCharacteristic(SteamControllerConstants.COMMAND_CHAR_UUID)

            if (inputChar != null) {
                enableNotifications(gatt, inputChar!!)
                _connectionState.value = BleConnectionState.Connected(name, isUnlocked = false)
                disableLizardMode()
            } else {
                Log.e(tag, "Valve Input characteristic not found")
                _connectionState.value = BleConnectionState.Error("Valve Input characteristic missing")
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == SteamControllerConstants.INPUT_REPORT_CHAR_UUID) {
                val data = characteristic.value ?: return
                val packet = SteamControllerPacket.parse(data)
                if (packet != null) {
                    scope.launch {
                        _packetFlow.emit(packet)
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == SteamControllerConstants.COMMAND_CHAR_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(tag, "Lizard mode disabled successfully!")
                    val name = gatt.device.name ?: gatt.device.address
                    _connectionState.value = BleConnectionState.Connected(name, isUnlocked = true)
                } else {
                    Log.w(tag, "Failed to disable lizard mode: status $status")
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (descriptor != null) {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }
}
