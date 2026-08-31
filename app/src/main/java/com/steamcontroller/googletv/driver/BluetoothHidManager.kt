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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    data class Connecting(val deviceName: String) : BleConnectionState()
    data class Connected(val deviceName: String, val isUnlocked: Boolean) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

@SuppressLint("MissingPermission")
class BluetoothHidManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothHidManager"

        val VALVE_SERVICE_UUID: UUID = UUID.fromString("100f6c32-1735-4313-b402-38567131e5f3")
        private const val VALVE_NOTIFY_LOW: Long  = 0x100f6c75L
        private const val VALVE_NOTIFY_HIGH: Long = 0x100f6c7aL
        private const val VALVE_WRITE_LOW: Long   = 0x100f6cb5L
        private const val VALVE_WRITE_HIGH: Long  = 0x100f6cbeL
        private const val BATTERY_CHAR_SHORT: Long = 0x100f6c78L

        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val NAME_HINTS = listOf("Steam", "Steam Ctrl", "Steam Controller", "SteamController", "Valve", "Controller")

        private val DISABLE_LIZARD = byteArrayOf(0x85.toByte())
        private const val DESIRED_MTU = 100
    }

    private enum class State { IDLE, SCANNING, CONNECTING, MTU_REQUESTED, DISCOVERING, SUBSCRIBING, READY }

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = btManager.adapter
    private val scope = CoroutineScope(Dispatchers.IO)

    private var gatt: BluetoothGatt? = null
    private var featureWriteChar: BluetoothGattCharacteristic? = null
    private var batteryChar: BluetoothGattCharacteristic? = null
    private var pendingBatteryRead = false
    private var lastConnectedDevice: BluetoothDevice? = null
    private var reconnectJob: Job? = null
    private var isScanning = false

    private val pendingSubs = mutableListOf<BluetoothGattCharacteristic>()
    private var subsIndex = 0

    // Multi-device fallback candidate queue
    private val bondedCandidates = mutableListOf<BluetoothDevice>()
    private var bondedCandidateIndex = 0

    @Volatile private var state: State = State.IDLE
    private val heartbeatBusy = AtomicBoolean(false)

    private var onReportCallback: ((ByteArray) -> Unit)? = null
    private var onConnectionChangeCallback: ((Boolean) -> Unit)? = null

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _batteryFlow = MutableStateFlow<Int?>(null)
    val batteryFlow: StateFlow<Int?> = _batteryFlow.asStateFlow()

    val isBluetoothAvailable: Boolean get() = adapter != null && adapter.isEnabled

    fun listPairedSteamControllers(): List<BluetoothDevice> {
        val a = adapter ?: return emptyList()
        return try {
            a.bondedDevices.orEmpty().filter { dev ->
                val n = dev.name ?: ""
                NAME_HINTS.any { hint -> n.contains(hint, ignoreCase = true) }
            }.reversed() // Put most recent pairings first
        } catch (t: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT: ${t.message}")
            emptyList()
        }
    }

    fun startDiscoveryAndConnect(
        onReport: (ByteArray) -> Unit,
        onConnectionChange: (Boolean) -> Unit
    ) {
        this.onReportCallback = onReport
        this.onConnectionChangeCallback = onConnectionChange

        bondedCandidates.clear()
        bondedCandidates.addAll(listPairedSteamControllers())
        bondedCandidateIndex = 0

        if (bondedCandidates.isNotEmpty()) {
            val candidate = bondedCandidates[bondedCandidateIndex]
            Log.i(TAG, "Attempting connection to bonded Steam Controller: ${candidate.name} (${candidate.address}) [1 of ${bondedCandidates.size}]")
            connect(candidate, onReport, onConnectionChange)
            return
        }

        startBleScan(onReport, onConnectionChange)
    }

    private fun tryNextBondedOrScan() {
        bondedCandidateIndex++
        if (bondedCandidateIndex < bondedCandidates.size) {
            val candidate = bondedCandidates[bondedCandidateIndex]
            Log.i(TAG, "Trying next bonded Steam Controller candidate: ${candidate.name} (${candidate.address}) [${bondedCandidateIndex + 1} of ${bondedCandidates.size}]")
            onReportCallback?.let { reportCb ->
                onConnectionChangeCallback?.let { connCb ->
                    connect(candidate, reportCb, connCb)
                }
            }
        } else {
            Log.i(TAG, "All bonded Steam Controller candidates exhausted, starting BLE scan...")
            onReportCallback?.let { reportCb ->
                onConnectionChangeCallback?.let { connCb ->
                    startBleScan(reportCb, connCb)
                }
            }
        }
    }

    fun startBleScan(
        onReport: (ByteArray) -> Unit,
        onConnectionChange: (Boolean) -> Unit
    ) {
        this.onReportCallback = onReport
        this.onConnectionChangeCallback = onConnectionChange

        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner not available")
            _connectionState.value = BleConnectionState.Error("Bluetooth LE scanner unavailable")
            return
        }

        stopBleScan()
        isScanning = true
        state = State.SCANNING
        _connectionState.value = BleConnectionState.Scanning
        Log.i(TAG, "Starting Bluetooth LE scan for Steam Controllers...")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Unfiltered scan failed, trying with service filter: ${e.message}")
            try {
                val filters = listOf(
                    ScanFilter.Builder().setServiceUuid(ParcelUuid(VALVE_SERVICE_UUID)).build()
                )
                scanner.startScan(filters, settings, scanCallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Scan failed: ${e2.message}")
                _connectionState.value = BleConnectionState.Error("Scan failed: ${e2.message}")
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val dev = result?.device ?: return
            val name = dev.name ?: result.scanRecord?.deviceName ?: ""
            val serviceUuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
            val hasValveService = serviceUuids.contains(VALVE_SERVICE_UUID)
            val matchesName = NAME_HINTS.any { hint -> name.contains(hint, ignoreCase = true) }

            if (hasValveService || matchesName) {
                Log.i(TAG, "Discovered Steam Controller: '$name' (${dev.address})! Connecting...")
                stopBleScan()
                onReportCallback?.let { reportCb ->
                    onConnectionChangeCallback?.let { connCb ->
                        connect(dev, reportCb, connCb)
                    }
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            isScanning = false
            if (state == State.SCANNING) {
                state = State.IDLE
                _connectionState.value = BleConnectionState.Error("Scan error: $errorCode")
            }
        }
    }

    fun stopBleScan() {
        if (isScanning) {
            try {
                adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                // Ignored
            }
            isScanning = false
        }
    }

    fun connect(
        device: BluetoothDevice,
        onReport: (ByteArray) -> Unit,
        onConnectionChange: (Boolean) -> Unit
    ) {
        stopBleScan()
        reconnectJob?.cancel()
        this.onReportCallback = onReport
        this.onConnectionChangeCallback = onConnectionChange
        this.lastConnectedDevice = device
        val name = device.name ?: device.address
        Log.i(TAG, "Connecting GATT to $name (${device.address})")
        state = State.CONNECTING
        _connectionState.value = BleConnectionState.Connecting(name)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        stopBleScan()
        reconnectJob?.cancel()
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "disconnect: ${t.message}")
        } finally {
            gatt = null
            featureWriteChar = null
            batteryChar = null
            pendingBatteryRead = false
            pendingSubs.clear()
            subsIndex = 0
            state = State.IDLE
            _connectionState.value = BleConnectionState.Disconnected
            onConnectionChangeCallback?.invoke(false)
        }
    }

    fun sendHeartbeat() {
        if (state != State.READY) return
        val g = gatt ?: return
        val ch = featureWriteChar ?: return
        if (!heartbeatBusy.compareAndSet(false, true)) return
        try {
            @Suppress("DEPRECATION")
            ch.value = DISABLE_LIZARD
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            val ok = g.writeCharacteristic(ch)
            if (!ok) {
                heartbeatBusy.set(false)
                Log.v(TAG, "heartbeat skipped: writeCharacteristic returned false")
            }
        } catch (t: Throwable) {
            heartbeatBusy.set(false)
            Log.w(TAG, "heartbeat write failed: ${t.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "onConnectionStateChange status=$status newState=$newState (${g.device.address})")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT status=$status (error) on ${g.device.address}, falling back to next candidate...")
                try { g.close() } catch (_: Throwable) {}
                gatt = null
                state = State.IDLE
                lastConnectedDevice = null
                _connectionState.value = BleConnectionState.Connecting("Retrying...")
                onConnectionChangeCallback?.invoke(false)
                tryNextBondedOrScan()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    reconnectJob?.cancel()
                    onConnectionChangeCallback?.invoke(true)
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                    if (state == State.CONNECTING) {
                        state = State.MTU_REQUESTED
                        val ok = g.requestMtu(DESIRED_MTU)
                        if (!ok) {
                            Log.w(TAG, "requestMtu($DESIRED_MTU) returned false, discovering services...")
                            state = State.DISCOVERING
                            g.discoverServices()
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    onConnectionChangeCallback?.invoke(false)
                    try { g.close() } catch (_: Throwable) {}
                    gatt = null
                    featureWriteChar = null
                    pendingSubs.clear()
                    subsIndex = 0
                    state = State.IDLE
                    _connectionState.value = BleConnectionState.Disconnected

                    startAutoReconnect()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "onMtuChanged: mtu=$mtu status=$status (state=$state)")
            if (state != State.MTU_REQUESTED) return
            state = State.DISCOVERING
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.i(TAG, "onServicesDiscovered status=$status (state=$state)")
            if (state != State.DISCOVERING) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed")
                _connectionState.value = BleConnectionState.Error("Service discovery failed ($status)")
                return
            }

            val valve = g.getService(VALVE_SERVICE_UUID)
            if (valve == null) {
                Log.e(TAG, "Valve vendor service not found")
                _connectionState.value = BleConnectionState.Error("Valve GATT Service not found")
                return
            }

            pendingSubs.clear()
            featureWriteChar = null
            batteryChar = null

            for (ch in valve.characteristics) {
                val short = shortUuid(ch.uuid) ?: continue
                val canNotify = (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                val canWrite = (ch.properties and (
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0

                if (canNotify && short in VALVE_NOTIFY_LOW..VALVE_NOTIFY_HIGH) {
                    pendingSubs.add(ch)
                }
                if (short == BATTERY_CHAR_SHORT) {
                    batteryChar = ch
                }
                if (canWrite && short in VALVE_WRITE_LOW..VALVE_WRITE_HIGH && featureWriteChar == null) {
                    featureWriteChar = ch
                }
            }

            Log.i(TAG, "Found ${pendingSubs.size} notify chars; feature-write=${featureWriteChar?.uuid}")
            subsIndex = 0
            state = State.SUBSCRIBING
            subscribeNext(g)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.v(TAG, "onDescriptorWrite ${descriptor.uuid} status=$status (state=$state, idx=$subsIndex/${pendingSubs.size})")
            if (state == State.SUBSCRIBING) subscribeNext(g)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int
        ) {
            heartbeatBusy.set(false)
            if (status != 0) Log.w(TAG, "Write ${ch.uuid} failed: status=$status")
            if (pendingBatteryRead) {
                pendingBatteryRead = false
                @Suppress("DEPRECATION")
                batteryChar?.let { g.readCharacteristic(it) }
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val data = ch.value ?: return
            if (shortUuid(ch.uuid) == BATTERY_CHAR_SHORT && data.size == 14) {
                onReportCallback?.invoke(byteArrayOf(0x43.toByte(), data[1], 0x00))
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic
        ) {
            val data = ch.value ?: return
            val short = shortUuid(ch.uuid)

            val toForward: ByteArray = when {
                data.size >= 40 -> {
                    val withId = ByteArray(data.size + 1)
                    withId[0] = 0x45
                    System.arraycopy(data, 0, withId, 1, data.size)
                    withId
                }
                short == BATTERY_CHAR_SHORT && data.size == 14 -> byteArrayOf(0x43.toByte(), data[1], 0x00)
                else -> data
            }

            onReportCallback?.invoke(toForward)
        }
    }

    private fun startAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive && state == State.IDLE) {
                delay(3000)
                val dev = lastConnectedDevice ?: listPairedSteamControllers().firstOrNull()
                if (dev != null) {
                    Log.d(TAG, "Auto-reconnecting to ${dev.name} (${dev.address})...")
                    onReportCallback?.let { reportCb ->
                        onConnectionChangeCallback?.let { connCb ->
                            connect(dev, reportCb, connCb)
                        }
                    }
                } else {
                    onReportCallback?.let { reportCb ->
                        onConnectionChangeCallback?.let { connCb ->
                            startDiscoveryAndConnect(reportCb, connCb)
                        }
                    }
                }
            }
        }
    }

    private fun subscribeNext(g: BluetoothGatt) {
        if (subsIndex >= pendingSubs.size) {
            Log.i(TAG, "All ${pendingSubs.size} subscriptions complete, sending disable lizard (0x85)")
            val ch = featureWriteChar
            state = State.READY
            val name = g.device.name ?: g.device.address
            _connectionState.value = BleConnectionState.Connected(name, isUnlocked = true)

            if (ch == null) {
                Log.w(TAG, "No feature write char; skipping disable lizard")
                @Suppress("DEPRECATION")
                batteryChar?.let { g.readCharacteristic(it) }
                return
            }

            @Suppress("DEPRECATION")
            ch.value = DISABLE_LIZARD
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            pendingBatteryRead = batteryChar != null
            @Suppress("DEPRECATION")
            val ok = g.writeCharacteristic(ch)
            Log.i(TAG, "Disable lizard (0x85) write: $ok")
            return
        }

        val ch = pendingSubs[subsIndex++]
        g.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            Log.w(TAG, "No CCCD on ${ch.uuid}, skipping")
            subscribeNext(g)
            return
        }

        @Suppress("DEPRECATION")
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        @Suppress("DEPRECATION")
        val wOk = g.writeDescriptor(cccd)
        if (!wOk) {
            subscribeNext(g)
        }
    }

    private fun shortUuid(uuid: UUID): Long? {
        val s = uuid.toString()
        if (!s.endsWith("-1735-4313-b402-38567131e5f3")) return null
        return try { java.lang.Long.parseLong(s.substring(0, 8), 16) } catch (_: Throwable) { null }
    }
}
