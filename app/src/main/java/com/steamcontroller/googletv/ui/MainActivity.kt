@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.steamcontroller.googletv.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.steamcontroller.googletv.adb.AdbCrypto
import com.steamcontroller.googletv.adb.AdbPairingClient
import com.steamcontroller.googletv.driver.BleConnectionState
import com.steamcontroller.googletv.remapper.InputProfile
import com.steamcontroller.googletv.remapper.VirtualGamepadState
import com.steamcontroller.googletv.service.GamepadForegroundService
import com.steamcontroller.googletv.ui.screens.AdbPairingScreen
import com.steamcontroller.googletv.ui.screens.HomeScreen
import com.steamcontroller.googletv.ui.screens.ProfileEditorScreen
import com.steamcontroller.googletv.ui.theme.SteamControllerTvTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen {
    HOME,
    PAIRING,
    PROFILES
}

class MainActivity : ComponentActivity() {

    private var gamepadService: GamepadForegroundService? = null
    private var isServiceBound = false

    private val serviceRunning = MutableStateFlow(false)
    private val bleState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    private val gamepadState = MutableStateFlow(VirtualGamepadState())
    private val currentProfile = MutableStateFlow(InputProfile.DEFAULT)

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                gamepadService?.connectController()
            } else {
                Toast.makeText(this, "Bluetooth permissions required for Steam Controller", Toast.LENGTH_LONG).show()
            }
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as GamepadForegroundService.LocalBinder
            val svc = binder.getService()
            gamepadService = svc
            isServiceBound = true

            val btManager = svc.getBtManager()
            lifecycleScopeLaunch {
                svc.serviceRunning.collect { serviceRunning.value = it }
            }
            lifecycleScopeLaunch {
                btManager.connectionState.collect { bleState.value = it }
            }
            // Sample UI visualizer at 20fps to completely eliminate TV UI thread overhead
            lifecycleScopeLaunch {
                while (isActive) {
                    delay(50) // 20fps
                    gamepadState.value = svc.latestGamepadState.value
                }
            }

            gamepadService?.connectController()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            gamepadService = null
            isServiceBound = false
            serviceRunning.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        val serviceIntent = Intent(this, GamepadForegroundService::class.java)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            SteamControllerTvTheme {
                MainAppNavHost()
            }
        }
    }

    @Composable
    private fun MainAppNavHost() {
        var currentScreen by remember { mutableStateOf(Screen.HOME) }
        var pairingStatus by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        val running by serviceRunning.collectAsState()
        val ble by bleState.collectAsState()
        val gpState by gamepadState.collectAsState()
        val profile by currentProfile.collectAsState()

        when (currentScreen) {
            Screen.HOME -> {
                HomeScreen(
                    isServiceRunning = running,
                    bleState = ble,
                    gamepadState = gpState,
                    currentProfile = profile,
                    onToggleService = {
                        val intent = Intent(this, GamepadForegroundService::class.java).apply {
                            action = if (running) GamepadForegroundService.ACTION_STOP else GamepadForegroundService.ACTION_START
                            putExtra(GamepadForegroundService.EXTRA_ADB_PORT, 5555)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    },
                    onScanBle = {
                        gamepadService?.connectController()
                    },
                    onNavigatePairing = { currentScreen = Screen.PAIRING },
                    onNavigateProfiles = { currentScreen = Screen.PROFILES }
                )
            }
            Screen.PAIRING -> {
                AdbPairingScreen(
                    pairingStatus = pairingStatus,
                    onPair = { port, code ->
                        scope.launch {
                            pairingStatus = "Pairing with Google TV on port $port..."
                            val crypto = AdbCrypto.loadOrCreate(applicationContext)
                            val pairingClient = AdbPairingClient(applicationContext, crypto)
                            val result = pairingClient.pair("127.0.0.1", port, code)
                            pairingStatus = if (result.isSuccess) {
                                "Pairing Successful! Keys saved."
                            } else {
                                "Pairing Failed: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    onBack = { currentScreen = Screen.HOME }
                )
            }
            Screen.PROFILES -> {
                ProfileEditorScreen(
                    currentProfile = profile,
                    onSaveProfile = { updated ->
                        currentProfile.value = updated
                        gamepadService?.updateProfile(updated)
                    },
                    onBack = { currentScreen = Screen.HOME }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun lifecycleScopeLaunch(block: suspend CoroutineScope.() -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            block()
        }
    }

    override fun onDestroy() {
        if (isServiceBound) {
            unbindService(connection)
            isServiceBound = false
        }
        super.onDestroy()
    }
}
