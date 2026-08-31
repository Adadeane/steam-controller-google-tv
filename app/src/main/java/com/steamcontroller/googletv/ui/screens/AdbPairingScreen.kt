@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.steamcontroller.googletv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.steamcontroller.googletv.ui.theme.SteamAccentBlue
import com.steamcontroller.googletv.ui.theme.SteamAccentCyan
import com.steamcontroller.googletv.ui.theme.SteamError
import com.steamcontroller.googletv.ui.theme.SteamSuccess
import com.steamcontroller.googletv.ui.theme.SteamSurface
import com.steamcontroller.googletv.ui.theme.SteamSurfaceVariant
import com.steamcontroller.googletv.ui.theme.TextPrimary
import com.steamcontroller.googletv.ui.theme.TextSecondary

@Composable
fun AdbPairingScreen(
    onPair: (port: Int, code: String) -> Unit,
    pairingStatus: String,
    onBack: () -> Unit
) {
    var portText by remember { mutableStateOf("") }
    var codeText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Wireless Debugging Setup",
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

        Text(
            text = "Instructions: Go to Settings > System > Developer Options > Wireless Debugging > 'Pair device with pairing code'. Enter the Port and 6-digit Code shown on your TV screen.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Card(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.colors(containerColor = SteamSurface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Port input
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { androidx.compose.material3.Text("Wireless Debugging Pairing Port (e.g. 37845)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = SteamAccentCyan,
                        unfocusedBorderColor = TextSecondary,
                        focusedLabelColor = SteamAccentCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Code input
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    label = { androidx.compose.material3.Text("6-Digit Pairing Code (e.g. 849302)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = SteamAccentCyan,
                        unfocusedBorderColor = TextSecondary,
                        focusedLabelColor = SteamAccentCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val port = portText.toIntOrNull()
                            if (port != null && codeText.isNotBlank()) {
                                onPair(port, codeText.trim())
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = SteamAccentBlue,
                            focusedContainerColor = SteamAccentCyan
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                    ) {
                        Text("PAIR AND SAVE KEYS", color = TextPrimary)
                    }

                    if (pairingStatus.isNotBlank()) {
                        Text(
                            text = pairingStatus,
                            color = if (pairingStatus.contains("Success", ignoreCase = true)) SteamSuccess else SteamError,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
