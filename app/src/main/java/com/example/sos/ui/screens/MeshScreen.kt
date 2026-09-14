package com.example.sos.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sos.model.MeshDevice
import com.example.sos.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MeshScreen(
    deviceId: String,
    peers: List<MeshDevice>,
    messagesRelayed: Int
) {
    val scrollState = rememberScrollState()

    // Subtle radar scan pulse when searching for devices
    val infiniteTransition = rememberInfiniteTransition(label = "meshPulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundNavy)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // --- HEADER ---
        Text(
            text = "Local Mesh",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Direct peer-to-peer communication topology",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // --- VISUAL NETWORK GRAPH (CANVAS) ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2

                    if (peers.isEmpty()) {
                        // Searching radar ripple around user node
                        drawCircle(
                            color = NetworkBlue.copy(alpha = pulseAlpha),
                            radius = pulseRadius * 2f,
                            center = Offset(centerX, centerY)
                        )
                    }

                    // Draw connections to peer nodes
                    val peerCount = peers.size
                    if (peerCount > 0) {
                        val orbitRadius = 75.dp.toPx()
                        for (i in 0 until peerCount) {
                            val angle = (2.0 * Math.PI / peerCount * i) - (Math.PI / 2.0)
                            val peerX = centerX + orbitRadius * cos(angle).toFloat()
                            val peerY = centerY + orbitRadius * sin(angle).toFloat()

                            // Connecting radio line
                            drawLine(
                                color = NetworkBlue.copy(alpha = 0.5f),
                                start = Offset(centerX, centerY),
                                end = Offset(peerX, peerY),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )

                            // Peer node circle
                            drawCircle(
                                color = SurfaceCard,
                                radius = 16.dp.toPx(),
                                center = Offset(peerX, peerY)
                            )
                            drawCircle(
                                color = StatusConnected,
                                radius = 6.dp.toPx(),
                                center = Offset(peerX, peerY)
                            )
                        }
                    }

                    // Center user node ("YOU")
                    drawCircle(
                        color = SurfaceCard,
                        radius = 24.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = NetworkBlue,
                        radius = 8.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                }

                // Overlay labels
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    Text(
                        text = "YOU ($deviceId)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- MESH STATUS SUMMARY CARD ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "MESH STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (peers.isNotEmpty()) StatusConnected else WarningAmber, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (peers.isNotEmpty()) "Connected" else "Searching for peers",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Nearby devices", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "${peers.size} ${if (peers.size == 1) "device" else "devices"}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Column {
                        Text("Messages relayed", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "$messagesRelayed",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NetworkBlue
                        )
                    }
                    Column {
                        Text("Radio state", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = if (peers.isNotEmpty()) "Active link" else "Cycling",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- CONNECTED NODES SECTION OR EMPTY STATE ---
        if (peers.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "No nearby ResQMesh devices yet.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Move closer to another device running ResQMesh to extend the mesh.\n\nDevices automatically detect each other over local Wi-Fi Direct and Bluetooth without internet access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            Text(
                text = "NEARBY NODES (${peers.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                peers.forEach { peer ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(StatusConnected, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = peer.getDisplayBadge(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Direct peer node",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Text(
                                text = "CONNECTED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusConnected
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
