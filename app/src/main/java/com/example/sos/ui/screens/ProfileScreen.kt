package com.example.sos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sos.ui.theme.*

@Composable
fun ProfileScreen(
    deviceId: String,
    initialName: String,
    initialPhone: String,
    initialGatewayUrl: String,
    isLocationEnabled: Boolean,
    onSaveProfile: (name: String, phone: String) -> Unit,
    onSaveGatewayUrl: (url: String) -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var gatewayUrl by remember { mutableStateOf(initialGatewayUrl) }
    var isGatewaySaved by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var isSavedConfirmation by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundNavy)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Emergency identity and system settings",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Profile Identity Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Responder Call-sign", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = if (name.isNotBlank()) name else "Citizen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    TextButton(onClick = { isEditing = !isEditing }) {
                        Text(if (isEditing) "Done" else "Edit", color = NetworkBlue)
                    }
                }

                if (isEditing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NetworkBlue,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Emergency Contact Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NetworkBlue,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onSaveProfile(name.trim(), phone.trim())
                            isEditing = false
                            isSavedConfirmation = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NetworkBlue)
                    ) {
                        Text("Save Changes", color = BackgroundNavy, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // System Permissions & Hardware Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Device ID", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text(deviceId, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = NetworkBlue)
                }

                HorizontalDivider(color = BorderSubtle)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Location", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text(if (isLocationEnabled) "Permission enabled" else "Permission needed", style = MaterialTheme.typography.bodyMedium, color = if (isLocationEnabled) StatusConnected else WarningAmber)
                }

                HorizontalDivider(color = BorderSubtle)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Nearby communication", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium, color = StatusConnected)
                }

                HorizontalDivider(color = BorderSubtle)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Notifications", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium, color = StatusConnected)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Operations Center Gateway URL Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Incident Command Center URL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Cloud dashboard endpoint where intermediate gateway nodes upload mesh distress packets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                OutlinedTextField(
                    value = gatewayUrl,
                    onValueChange = {
                        gatewayUrl = it
                        isGatewaySaved = false
                    },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://resqmesh-fd8l.onrender.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NetworkBlue,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGatewaySaved) {
                        Text("✓ Saved & active", color = StatusConnected, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            onSaveGatewayUrl(gatewayUrl.trim())
                            isGatewaySaved = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NetworkBlue)
                    ) {
                        Text("Save URL", color = BackgroundNavy, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // About & Diagnostics
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("About ResQMesh", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text("Version 1.0 (SIH 2026)", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }

                HorizontalDivider(color = BorderSubtle)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDiagnostics() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Diagnostics & Testing", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Inspect RF state, raw packets, logs & SIH demo flow", style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp, color = TextMuted)
                    }
                    Text("›", fontSize = 20.sp, color = TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
