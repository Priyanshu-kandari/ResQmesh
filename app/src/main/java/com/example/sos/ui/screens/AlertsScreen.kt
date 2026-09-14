package com.example.sos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sos.model.EmergencyMessage
import com.example.sos.ui.theme.*

@Composable
fun AlertsScreen(
    messages: List<EmergencyMessage>
) {
    var selectedMessageId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundNavy)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // --- HEADER ---
        Text(
            text = "Alerts",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Emergency event timeline and delivery audit",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (messages.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "No emergency alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your emergency activity, sent distress alerts, and relayed messages will appear here as a chronological timeline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            Text(
                text = "TODAY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(messages) { msg ->
                    val isExpanded = selectedMessageId == msg.messageId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMessageId = if (isExpanded) null else msg.messageId
                            }
                    ) {
                        // Timeline vertical spine
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        when (msg.status) {
                                            "RESOLVED" -> StatusConnected
                                            "IN_PROGRESS" -> WarningAmber
                                            "SYNCED" -> NetworkBlue
                                            "CREATED" -> EmergencyRed
                                            "RELAYED" -> NetworkBlue
                                            else -> TextSecondary
                                        },
                                        CircleShape
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .height(if (isExpanded) 120.dp else 65.dp)
                                    .background(BorderSubtle)
                            )
                        }

                        // Event details row (Clean, no huge cards)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, bottom = 18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (msg.status) {
                                        "RESOLVED" -> "RESCUED • CASE RESOLVED"
                                        "IN_PROGRESS" -> "RESCUE TEAM DISPATCHED"
                                        "SYNCED" -> "DELIVERED TO COMMAND"
                                        "CREATED" -> "EMERGENCY ALERT (SOS)"
                                        "RELAYED" -> "RELAYED"
                                        "RECEIVED" -> "RECEIVED FROM PEER"
                                        else -> msg.status
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (msg.status) {
                                        "RESOLVED" -> StatusConnected
                                        "IN_PROGRESS" -> WarningAmber
                                        "SYNCED" -> NetworkBlue
                                        "CREATED" -> EmergencyRed
                                        "RELAYED" -> NetworkBlue
                                        else -> TextPrimary
                                    }
                                )

                                Text(
                                    text = msg.getFormattedTime(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }

                            Text(
                                text = when (msg.status) {
                                    "RESOLVED" -> "Responders verified rescue: Victims secured safely"
                                    "IN_PROGRESS" -> "Dispatched: ${msg.assignedUnit ?: "Relief Team"} en route${if (!msg.authorityNotes.isNullOrBlank()) " (${msg.authorityNotes})" else ""}"
                                    "SYNCED" -> "Emergency alert delivered to cloud command center"
                                    "CREATED" -> "Alert created • Broadcasting over local mesh"
                                    "RELAYED" -> "Relayed through ${if (msg.hopCount == 0) "direct peer" else "${msg.hopCount} devices"}"
                                    "RECEIVED" -> "Distress signal received from nearby device"
                                    else -> "Packet ${msg.messageId}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp,
                                color = if (msg.status == "IN_PROGRESS") WarningAmber else TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            if (isExpanded) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceCard,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Message ID", fontSize = 11.sp, color = TextMuted)
                                            Text(msg.messageId, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Hazard / Casualties", fontSize = 11.sp, color = TextMuted)
                                            Text("${msg.hazardType} (${msg.peopleCount} people)", fontSize = 11.sp, color = TextPrimary)
                                        }
                                        if (msg.medicalNeeds != "None") {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Medical Needs", fontSize = 11.sp, color = TextMuted)
                                                Text(msg.medicalNeeds, fontSize = 11.sp, color = WarningAmber)
                                            }
                                        }
                                        if (msg.latitude != 0.0 || msg.longitude != 0.0) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("GPS Coordinates", fontSize = 11.sp, color = TextMuted)
                                                Text("${String.format("%.4f", msg.latitude)}, ${String.format("%.4f", msg.longitude)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
