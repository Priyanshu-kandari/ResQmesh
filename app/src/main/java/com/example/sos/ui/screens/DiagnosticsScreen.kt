package com.example.sos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sos.ui.theme.*

@Composable
fun DiagnosticsScreen(
    deviceId: String,
    meshRole: String,
    isOnline: Boolean,
    isBleActive: Boolean,
    connectedPeersCount: Int,
    pendingQueueCount: Int,
    logMessages: List<String>,
    onBack: () -> Unit,
    onSimulatePhoneA: () -> Unit,
    onSimulatePhoneB: () -> Unit,
    onSimulatePhoneC: () -> Unit,
    onSimulateCloudSync: () -> Unit,
    onResetDemo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundNavy)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header with Back action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("‹ Back", fontSize = 16.sp, color = NetworkBlue)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Diagnostics & Tools",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Radio & Telemetry Grid
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "RADIO & PROTOCOL STATE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                DiagRow(label = "Nearby Connections Strategy", value = "P2P_CLUSTER (Mesh)")
                DiagRow(label = "Duty Cycle Role", value = meshRole)
                DiagRow(label = "Connected Radio Links", value = "$connectedPeersCount endpoints")
                DiagRow(label = "Hardware BLE Beacon", value = if (isBleActive) "Broadcasting (0x02E5)" else "Standby")
                DiagRow(label = "Internet Backhaul", value = if (isOnline) "Connected (Firebase Ready)" else "Offline (Pure Mesh)")
                DiagRow(label = "Store-and-Forward Queue", value = "$pendingQueueCount packets pending")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SIH 2026 Presentation Tools
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SIH 2026 PRESENTATION SIMULATOR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber,
                    letterSpacing = 1.sp
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onSimulatePhoneA,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("1. Phone A", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onSimulatePhoneB,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NetworkBlue),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("2. Phone B", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BackgroundNavy)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onSimulatePhoneC,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusConnected),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("3. Phone C", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onSimulateCloudSync,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("4. Cloud Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                OutlinedButton(
                    onClick = onResetDemo,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Reset Test Queue", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Raw Logs Stream
        Text(
            text = "EVENT STREAM",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF070B12),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(logMessages) { log ->
                    Text(
                        text = log,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            log.contains("❌") || log.contains("🚨") -> Color(0xFFFCA5A5)
                            log.contains("✅") -> StatusConnected
                            log.contains("🔄") || log.contains("🔁") -> NetworkBlue
                            else -> TextSecondary
                        },
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
