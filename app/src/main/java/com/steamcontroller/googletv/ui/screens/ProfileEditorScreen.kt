@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.steamcontroller.googletv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.steamcontroller.googletv.remapper.ButtonMappingConfig
import com.steamcontroller.googletv.remapper.GamepadButton
import com.steamcontroller.googletv.remapper.InputProfile
import com.steamcontroller.googletv.remapper.TrackpadConfig
import com.steamcontroller.googletv.ui.theme.SteamAccentBlue
import com.steamcontroller.googletv.ui.theme.SteamAccentCyan
import com.steamcontroller.googletv.ui.theme.SteamSurface
import com.steamcontroller.googletv.ui.theme.SteamSurfaceVariant
import com.steamcontroller.googletv.ui.theme.TextPrimary
import com.steamcontroller.googletv.ui.theme.TextSecondary

@Composable
fun ProfileEditorScreen(
    currentProfile: InputProfile,
    onSaveProfile: (InputProfile) -> Unit,
    onBack: () -> Unit
) {
    var sensitivity by remember { mutableFloatStateOf(currentProfile.rightPadConfig.sensitivity) }
    var trackballEnabled by remember { mutableStateOf(currentProfile.rightPadConfig.trackballEnabled) }
    var gyroEnabled by remember { mutableStateOf(currentProfile.gyroAimingEnabled) }
    var leftGrip by remember { mutableStateOf(currentProfile.buttonMappings.leftGrip) }
    var rightGrip by remember { mutableStateOf(currentProfile.buttonMappings.rightGrip) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Steam Input Remapper",
                style = MaterialTheme.typography.displayMedium
            )

            Button(
                onClick = onBack,
                colors = ButtonDefaults.colors(
                    containerColor = SteamSurfaceVariant,
                    focusedContainerColor = SteamAccentCyan
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
            ) {
                Text("BACK TO HOME", color = TextPrimary)
            }
        }

        // Preset Quick Pickers
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val p = InputProfile.DEFAULT
                    sensitivity = p.rightPadConfig.sensitivity
                    trackballEnabled = p.rightPadConfig.trackballEnabled
                    gyroEnabled = p.gyroAimingEnabled
                    leftGrip = p.buttonMappings.leftGrip
                    rightGrip = p.buttonMappings.rightGrip
                },
                colors = ButtonDefaults.colors(containerColor = SteamSurfaceVariant, focusedContainerColor = SteamAccentBlue)
            ) {
                Text("Preset: Standard Xbox")
            }

            Button(
                onClick = {
                    val p = InputProfile.FPS_AIMING
                    sensitivity = p.rightPadConfig.sensitivity
                    trackballEnabled = p.rightPadConfig.trackballEnabled
                    gyroEnabled = p.gyroAimingEnabled
                    leftGrip = p.buttonMappings.leftGrip
                    rightGrip = p.buttonMappings.rightGrip
                },
                colors = ButtonDefaults.colors(containerColor = SteamSurfaceVariant, focusedContainerColor = SteamAccentBlue)
            ) {
                Text("Preset: FPS Aiming")
            }

            Button(
                onClick = {
                    val p = InputProfile.RETRO_DPAD
                    sensitivity = p.rightPadConfig.sensitivity
                    trackballEnabled = p.rightPadConfig.trackballEnabled
                    gyroEnabled = p.gyroAimingEnabled
                    leftGrip = p.buttonMappings.leftGrip
                    rightGrip = p.buttonMappings.rightGrip
                },
                colors = ButtonDefaults.colors(containerColor = SteamSurfaceVariant, focusedContainerColor = SteamAccentBlue)
            ) {
                Text("Preset: Retro D-Pad")
            }
        }

        // Trackpad & Stick Settings Card
        Card(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.colors(containerColor = SteamSurface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Right Trackpad Behavior", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sensitivity: ${"%.1f".format(sensitivity)}x", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (sensitivity > 0.6f) sensitivity -= 0.2f },
                            colors = ButtonDefaults.colors(containerColor = SteamSurfaceVariant, focusedContainerColor = SteamAccentCyan)
                        ) { Text("-") }
                        Button(
                            onClick = { if (sensitivity < 3.0f) sensitivity += 0.2f },
                            colors = ButtonDefaults.colors(containerColor = SteamSurfaceVariant, focusedContainerColor = SteamAccentCyan)
                        ) { Text("+") }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trackball Momentum Friction", style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = { trackballEnabled = !trackballEnabled },
                        colors = ButtonDefaults.colors(
                            containerColor = if (trackballEnabled) SteamAccentBlue else SteamSurfaceVariant,
                            focusedContainerColor = SteamAccentCyan
                        )
                    ) {
                        Text(if (trackballEnabled) "ENABLED (Momentum)" else "DISABLED (Instant Stop)")
                    }
                }
            }
        }

        // Grip Paddles & Gyro Card
        Card(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.colors(containerColor = SteamSurface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Back Grip Paddles & Motion", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Left Grip (LG) Mapping: ${leftGrip.name}", style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = {
                            leftGrip = when (leftGrip) {
                                GamepadButton.BTN_A -> GamepadButton.BTN_LB
                                GamepadButton.BTN_LB -> GamepadButton.BTN_L3
                                else -> GamepadButton.BTN_A
                            }
                        },
                        colors = ButtonDefaults.colors(containerColor = SteamSurfaceVariant, focusedContainerColor = SteamAccentCyan)
                    ) {
                        Text("CYCLE BUTTON")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Right Grip (RG) Mapping: ${rightGrip.name}", style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = {
                            rightGrip = when (rightGrip) {
                                GamepadButton.BTN_X -> GamepadButton.BTN_B
                                GamepadButton.BTN_B -> GamepadButton.BTN_RB
                                else -> GamepadButton.BTN_X
                            }
                        },
                        colors = ButtonDefaults.colors(containerColor = SteamSurfaceVariant, focusedContainerColor = SteamAccentCyan)
                    ) {
                        Text("CYCLE BUTTON")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gyroscope Aim Assist", style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = { gyroEnabled = !gyroEnabled },
                        colors = ButtonDefaults.colors(
                            containerColor = if (gyroEnabled) SteamAccentBlue else SteamSurfaceVariant,
                            focusedContainerColor = SteamAccentCyan
                        )
                    ) {
                        Text(if (gyroEnabled) "GYRO ON" else "GYRO OFF")
                    }
                }
            }
        }

        // Apply Button
        Button(
            onClick = {
                val updated = currentProfile.copy(
                    rightPadConfig = currentProfile.rightPadConfig.copy(
                        sensitivity = sensitivity,
                        trackballEnabled = trackballEnabled
                    ),
                    buttonMappings = currentProfile.buttonMappings.copy(
                        leftGrip = leftGrip,
                        rightGrip = rightGrip
                    ),
                    gyroAimingEnabled = gyroEnabled
                )
                onSaveProfile(updated)
                onBack()
            },
            colors = ButtonDefaults.colors(containerColor = SteamAccentBlue, focusedContainerColor = SteamAccentCyan),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("SAVE AND APPLY PROFILE", style = MaterialTheme.typography.titleMedium)
        }
    }
}
