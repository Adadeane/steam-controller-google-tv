package com.steamcontroller.googletv.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.steamcontroller.googletv.R
import com.steamcontroller.googletv.SteamControllerApplication
import com.steamcontroller.googletv.adb.AdbConnectionManager
import com.steamcontroller.googletv.adb.AdbCrypto
import com.steamcontroller.googletv.adb.AdbShellStream
import com.steamcontroller.googletv.driver.BluetoothHidManager
import com.steamcontroller.googletv.driver.SteamControllerState
import com.steamcontroller.googletv.driver.SteamReportParser
import com.steamcontroller.googletv.injector.UinputGamepadManager
import com.steamcontroller.googletv.remapper.InputProfile
import com.steamcontroller.googletv.remapper.SteamInputEngine
import com.steamcontroller.googletv.remapper.VirtualGamepadState
import com.steamcontroller.googletv.ui.MainActivity
import com.steamcontroller.googletv.web.WebButtonTesterServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GamepadForegroundService : Service() {

    private val tag = "GamepadService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var btManager: BluetoothHidManager
    private val steamInputEngine = SteamInputEngine()
    @Volatile private var uinputManager: UinputGamepadManager? = null
    private var shellStream: AdbShellStream? = null
    private var heartbeatJob: Job? = null
    private val webServer = WebButtonTesterServer(port = 8080)

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _latestGamepadState = MutableStateFlow(VirtualGamepadState())
    val latestGamepadState: StateFlow<VirtualGamepadState> = _latestGamepadState.asStateFlow()

    private val _latestRawState = MutableStateFlow<SteamControllerState?>(null)
    val latestRawState: StateFlow<SteamControllerState?> = _latestRawState.asStateFlow()

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): GamepadForegroundService = this@GamepadForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        btManager = BluetoothHidManager(applicationContext)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SteamController:ServiceWakeLock")

        // Start Web Button Tester HTTP server on port 8080
        webServer.start(_latestGamepadState, _latestRawState)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val adbPort = intent.getIntExtra(EXTRA_ADB_PORT, 5555)
                startGamepadPipeline(adbPort)
            }
            ACTION_STOP -> stopGamepadPipeline()
        }
        return START_STICKY
    }

    fun getBtManager(): BluetoothHidManager = btManager

    fun updateProfile(profile: InputProfile) {
        steamInputEngine.profile = profile
    }

    fun connectController() {
        Log.i(tag, "Initiating discovery and connection to any Steam Controller...")
        btManager.startDiscoveryAndConnect(
            onReport = { raw -> handleRawReport(raw) },
            onConnectionChange = { connected ->
                Log.i(tag, "Steam Controller BT connected: $connected")
                if (connected) {
                    startHeartbeat()
                } else {
                    heartbeatJob?.cancel()
                }
            }
        )
    }

    private fun handleRawReport(raw: ByteArray) {
        try {
            if (raw.isNotEmpty() && (raw[0].toInt() and 0xFF) == 0x45) {
                val state = SteamReportParser.parse(raw) ?: return
                _latestRawState.value = state
                val virtualState = steamInputEngine.process(state)
                _latestGamepadState.value = virtualState
                uinputManager?.dispatchState(virtualState)
            }
        } catch (t: Throwable) {
            Log.w(tag, "Exception during handleRawReport: ${t.message}")
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(800)
                btManager.sendHeartbeat()
            }
        }
        Log.i(tag, "Started 800ms disable-lizard heartbeat loop")
    }

    fun startGamepadPipeline(adbPort: Int) {
        if (_serviceRunning.value) return

        wakeLock?.acquire(12 * 60 * 60 * 1000L)
        startForeground(NOTIFICATION_ID, buildNotification())
        _serviceRunning.value = true

        scope.launch(Dispatchers.IO) {
            Log.d(tag, "Connecting to ADB on localhost:$adbPort for uinput injection...")
            val crypto = AdbCrypto.loadOrCreate(applicationContext)
            val connectionManager = AdbConnectionManager(crypto)

            val adbResult = connectionManager.connect("127.0.0.1", adbPort)
            if (adbResult.isSuccess) {
                shellStream = adbResult.getOrNull()
                shellStream?.let { stream ->
                    val manager = UinputGamepadManager(applicationContext, stream)
                    manager.initializeVirtualGamepad()
                    uinputManager = manager
                    Log.d(tag, "Native uinput manager initialized!")
                }
            } else {
                Log.e(tag, "Failed to connect to local ADB", adbResult.exceptionOrNull())
            }

            connectController()
        }
    }

    fun stopGamepadPipeline() {
        if (!_serviceRunning.value) return

        Log.d(tag, "Stopping Gamepad service...")
        heartbeatJob?.cancel()
        heartbeatJob = null

        scope.launch(Dispatchers.IO) {
            try {
                uinputManager?.close()
                shellStream?.close()
            } catch (e: Exception) {
                Log.w(tag, "Error closing uinput: ${e.message}")
            } finally {
                uinputManager = null
                shellStream = null
            }
        }

        btManager.disconnect()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        _serviceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SteamControllerApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_running))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        webServer.stop()
        stopGamepadPipeline()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.steamcontroller.googletv.action.START"
        const val ACTION_STOP = "com.steamcontroller.googletv.action.STOP"
        const val EXTRA_ADB_PORT = "extra_adb_port"
        private const val NOTIFICATION_ID = 1001
    }
}
