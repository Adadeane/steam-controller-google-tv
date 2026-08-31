@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.steamcontroller.googletv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.steamcontroller.googletv.driver.BleConnectionState
import com.steamcontroller.googletv.remapper.InputProfile
import com.steamcontroller.googletv.remapper.VirtualGamepadState
import com.steamcontroller.googletv.ui.theme.SteamAccentBlue
import com.steamcontroller.googletv.ui.theme.SteamAccentCyan
import com.steamcontroller.googletv.ui.theme.SteamError
import com.steamcontroller.googletv.ui.theme.SteamSuccess
import com.steamcontroller.googletv.ui.theme.SteamSurface
import com.steamcontroller.googletv.ui.theme.SteamSurfaceVariant
import com.steamcontroller.googletv.ui.theme.TextPrimary
import com.steamcontroller.googletv.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    isServiceRunning: Boolean,
    bleState: BleConnectionState,
    gamepadState: VirtualGamepadState,
    currentProfile: InputProfile,
    onToggleService: () -> Unit,
    onScanBle: () -> Unit,
    onNavigatePairing: () -> Unit,
    onNavigateProfiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Title Header with Version
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Steam Controller Gamepad",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .background(SteamAccentCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .border(1.5.dp, SteamAccentCyan, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "v2.4",
                            color = SteamAccentCyan,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Text(
                    text = "System-Wide Gamepad Bridge for Google TV Streamer",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Master Service Control Button
            Button(
                onClick = onToggleService,
                colors = ButtonDefaults.colors(
                    containerColor = if (isServiceRunning) SteamError else SteamAccentBlue,
                    focusedContainerColor = if (isServiceRunning) Color(0xFFFF5252) else SteamAccentCyan
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = if (isServiceRunning) "STOP SERVICE" else "START GAMEPAD SERVICE",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
        }

        // Status Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // BLE Status Card
            Card(
                onClick = onScanBle,
                modifier = Modifier.weight(1f),
                colors = CardDefaults.colors(containerColor = SteamSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Controller Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    when (bleState) {
                        is BleConnectionState.Connected -> {
                            Text(
                                "Connected: ${bleState.deviceName}",
                                color = SteamSuccess,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                if (bleState.isUnlocked) "Mode: Raw Gamepad (Lizard Disabled)" else "Mode: Initializing...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        is BleConnectionState.Connecting -> Text("Connecting to ${bleState.deviceName}...", color = SteamAccentCyan)
                        is BleConnectionState.Scanning -> {
                            Text("Scanning for controllers...", color = SteamAccentBlue)
                            Text("Hold Steam + Y on controller", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        is BleConnectionState.Disconnected -> Text("Disconnected (Click to scan)", color = TextSecondary)
                        is BleConnectionState.Error -> {
                            Text("Status: ${bleState.message}", color = SteamError, style = MaterialTheme.typography.bodyMedium)
                            Text("Click card to retry scan", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            // Wireless Debugging Card
            Card(
                onClick = onNavigatePairing,
                modifier = Modifier.weight(1f),
                colors = CardDefaults.colors(containerColor = SteamSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Wireless Debugging (ADB)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isServiceRunning) "Status: Connected to localhost" else "Ready to connect",
                        color = if (isServiceRunning) SteamSuccess else TextSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text("Click to pair or change port", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Active Profile Card
            Card(
                onClick = onNavigateProfiles,
                modifier = Modifier.weight(1f),
                colors = CardDefaults.colors(containerColor = SteamSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Active Profile", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(currentProfile.name, color = SteamAccentCyan, style = MaterialTheme.typography.bodyLarge)
                    Text("Click to customize remapping", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Live Gamepad Visualizer
        Card(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.colors(containerColor = SteamSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text("Live Gamepad State Monitor", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Stick visualizer
                    StickIndicator(
                        label = "Left Stick",
                        x = gamepadState.leftStickX,
                        y = gamepadState.leftStickY
                    )

                    // Buttons Grid
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ButtonBadge(label = "Y", active = gamepadState.btnY)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ButtonBadge(label = "X", active = gamepadState.btnX)
                            Spacer(Modifier.width(16.dp))
                            ButtonBadge(label = "B", active = gamepadState.btnB)
                        }
                        ButtonBadge(label = "A", active = gamepadState.btnA)
                    }

                    // Shoulder / Triggers
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ButtonBadge(label = "LB", active = gamepadState.btnLB)
                            ButtonBadge(label = "RB", active = gamepadState.btnRB)
                        }
                        Text(
                            text = "LT: ${(gamepadState.leftTrigger * 100).toInt()}%  RT: ${(gamepadState.rightTrigger * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    // Right Stick / Trackpad visualizer
                    StickIndicator(
                        label = "Right Pad / Stick",
                        x = gamepadState.rightStickX,
                        y = gamepadState.rightStickY
                    )
                }
            }
        }
    }
}

@Composable
private fun ButtonBadge(label: String, active: Boolean) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) SteamAccentCyan else SteamSurfaceVariant)
            .border(
                1.dp,
                if (active) Color.White else SteamAccentBlue.copy(alpha = 0.3f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.Black else TextPrimary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun StickIndicator(label: String, x: Float, y: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(SteamSurfaceVariant)
                .border(1.dp, SteamAccentBlue.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val offsetX = (x * 30).dp
            val offsetY = (y * 30).dp
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .offset(x = offsetX, y = offsetY)
                    .clip(CircleShape)
                    .background(SteamAccentCyan)
            )
        }
    }
}
