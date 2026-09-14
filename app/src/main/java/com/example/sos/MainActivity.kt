package com.example.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import com.example.sos.data.MessageRepository
import com.example.sos.data.PreferencesManager
import com.example.sos.model.EmergencyMessage
import com.example.sos.network.CloudSyncManager
import com.example.sos.network.MeshNetworkManager
import com.example.sos.ui.screens.*
import com.example.sos.ui.theme.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var preferences: PreferencesManager
    private lateinit var repository: MessageRepository
    private lateinit var networkManager: MeshNetworkManager
    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val logMessages = mutableStateListOf<String>()
    private val locationText = mutableStateOf("Checking GPS...")
    private val isLocationReady = mutableStateOf(false)
    private val isGpsEnabled = mutableStateOf(false)
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var lastAccuracy = 0f

    private val requiredPermissions = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        else -> {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            addLog("All mesh and location permissions granted.")
            networkManager.startMesh()
            fetchLocation()
        } else {
            addLog("Some permissions denied. Nearby mesh might be restricted.")
            networkManager.startMesh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = PreferencesManager(this)
        repository = MessageRepository(preferences.deviceId)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        networkManager = MeshNetworkManager(
            context = this,
            deviceId = preferences.deviceId,
            repository = repository,
            onLog = { addLog(it) }
        )

        cloudSyncManager = CloudSyncManager(
            context = this,
            repository = repository,
            onLog = { addLog(it) }
        )

        addLog("ResQMesh node initialized: ${preferences.deviceId}")
        checkAndRequestPermissions()

        setContent {
            SOSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedTab by remember { mutableIntStateOf(0) }
                    var showConfirmationDialog by remember { mutableStateOf(false) }
                    var sentAlertId by remember { mutableStateOf<String?>(null) }
                    var showDiagnosticsScreen by remember { mutableStateOf(false) }

                    if (showConfirmationDialog) {
                        SosConfirmationDialog(
                            locationText = locationText.value,
                            isLocationReady = isLocationReady.value,
                            isGpsEnabled = isGpsEnabled.value,
                            onOpenLocationSettings = {
                                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                            onConfirm = { p, m, h ->
                                showConfirmationDialog = false
                                val newId = triggerEmergencySos(p, m, h)
                                sentAlertId = newId
                            },
                            onDismiss = { showConfirmationDialog = false }
                        )
                    }

                    sentAlertId?.let { id ->
                        SosSentDialog(
                            messageId = id,
                            peerCount = networkManager.connectedDevices.size,
                            onDismiss = { sentAlertId = null }
                        )
                    }

                    if (showDiagnosticsScreen) {
                        DiagnosticsScreen(
                            deviceId = preferences.deviceId,
                            meshRole = networkManager.currentRole.value,
                            isOnline = cloudSyncManager.isOnline.value,
                            isBleActive = networkManager.isBleBroadcasting.value,
                            connectedPeersCount = networkManager.connectedDevices.size,
                            pendingQueueCount = repository.pendingPeerQueue.size,
                            logMessages = logMessages,
                            onBack = { showDiagnosticsScreen = false },
                            onSimulatePhoneA = { simulatePhoneASos() },
                            onSimulatePhoneB = { simulatePhoneBRelay() },
                            onSimulatePhoneC = { simulatePhoneCDelivery() },
                            onSimulateCloudSync = { simulateCloudSync() },
                            onResetDemo = { repository.clear(); addLog("Test queue reset.") }
                        )
                    } else {
                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = SurfaceDark,
                                    tonalElevation = 6.dp
                                ) {
                                    // 1. HOME TAB
                                    NavigationBarItem(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        icon = {
                                            HomeNavIcon(selected = selectedTab == 0)
                                        },
                                        label = { Text("Home", fontSize = 11.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedTextColor = TextPrimary,
                                            unselectedTextColor = TextMuted,
                                            indicatorColor = SurfaceElevated
                                        )
                                    )

                                    // 2. MESH TAB
                                    NavigationBarItem(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        icon = {
                                            MeshNavIcon(selected = selectedTab == 1)
                                        },
                                        label = { Text("Mesh", fontSize = 11.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedTextColor = TextPrimary,
                                            unselectedTextColor = TextMuted,
                                            indicatorColor = SurfaceElevated
                                        )
                                    )

                                    // 3. ALERTS TAB
                                    NavigationBarItem(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2 },
                                        icon = {
                                            AlertsNavIcon(selected = selectedTab == 2)
                                        },
                                        label = { Text("Alerts", fontSize = 11.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedTextColor = TextPrimary,
                                            unselectedTextColor = TextMuted,
                                            indicatorColor = SurfaceElevated
                                        )
                                    )

                                    // 4. PROFILE TAB
                                    NavigationBarItem(
                                        selected = selectedTab == 3,
                                        onClick = { selectedTab = 3 },
                                        icon = {
                                            ProfileNavIcon(selected = selectedTab == 3)
                                        },
                                        label = { Text("Profile", fontSize = 11.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedTextColor = TextPrimary,
                                            unselectedTextColor = TextMuted,
                                            indicatorColor = SurfaceElevated
                                        )
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                when (selectedTab) {
                                    0 -> HomeScreen(
                                        deviceId = preferences.deviceId,
                                        isOnline = cloudSyncManager.isOnline.value,
                                        peerCount = networkManager.connectedDevices.size,
                                        locationText = locationText.value,
                                        isLocationReady = isLocationReady.value,
                                        recentAlertText = if (repository.messageHistory.isNotEmpty()) {
                                            val top = repository.messageHistory[0]
                                            when (top.status) {
                                                "RESOLVED" -> "✓ Rescued  •  Incident resolved"
                                                "IN_PROGRESS" -> "🚨 Dispatched: ${top.assignedUnit ?: "Rescue squad"} en route"
                                                "SYNCED" -> "Delivered to command center (${top.getFormattedTime()})"
                                                "CREATED" -> "Broadcasting SOS: ${top.hazardType} (${top.getFormattedTime()})"
                                                else -> "${top.status}: ${top.hazardType} (${top.getFormattedTime()})"
                                            }
                                        } else {
                                            "SOS ready  •  No active emergency"
                                        },
                                        onPressSos = {
                                            checkLocationProviderState()
                                            fetchLocation()
                                            showConfirmationDialog = true
                                        }
                                    )
                                    1 -> MeshScreen(
                                        deviceId = preferences.deviceId,
                                        peers = networkManager.connectedDevices,
                                        messagesRelayed = repository.relayedCount.intValue
                                    )
                                    2 -> AlertsScreen(
                                        messages = repository.messageHistory
                                    )
                                    3 -> ProfileScreen(
                                        deviceId = preferences.deviceId,
                                        initialName = preferences.userName,
                                        initialPhone = preferences.userPhone,
                                        isLocationEnabled = isLocationReady.value,
                                        onSaveProfile = { n, p ->
                                            preferences.saveProfile(n, preferences.userAge, p)
                                            addLog("Profile updated: ${preferences.userName}")
                                        },
                                        onOpenDiagnostics = {
                                            showDiagnosticsScreen = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkManager.stopMesh()
    }

    private fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        runOnUiThread {
            logMessages.add(0, "[$time] $msg")
            if (logMessages.size > 80) logMessages.removeLast()
        }
        Log.d("ResQMesh", msg)
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            networkManager.startMesh()
            checkLocationProviderState()
            fetchLocation()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        checkLocationProviderState()
        if (isGpsEnabled.value) {
            fetchLocation()
        }
    }

    private fun checkLocationProviderState(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val enabled = try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
        isGpsEnabled.value = enabled
        if (!enabled) {
            isLocationReady.value = false
            locationText.value = "Location Disabled (Turn on GPS)"
        }
        return enabled
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        if (!checkLocationProviderState()) {
            locationText.value = "Location Disabled (Turn on GPS)"
            isLocationReady.value = false
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationText.value = "Permission Denied"
            isLocationReady.value = false
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && loc.latitude != 0.0) {
                lastLat = loc.latitude
                lastLng = loc.longitude
                lastAccuracy = loc.accuracy
                isLocationReady.value = true
                locationText.value = "${String.format("%.4f", lastLat)}, ${String.format("%.4f", lastLng)} (±${lastAccuracy.toInt()}m)"
            } else {
                locationText.value = "Acquiring GPS fix..."
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { freshLoc ->
                        if (freshLoc != null && freshLoc.latitude != 0.0) {
                            lastLat = freshLoc.latitude
                            lastLng = freshLoc.longitude
                            lastAccuracy = freshLoc.accuracy
                            isLocationReady.value = true
                            locationText.value = "${String.format("%.4f", lastLat)}, ${String.format("%.4f", lastLng)} (±${lastAccuracy.toInt()}m)"
                        } else {
                            locationText.value = "Searching for satellites..."
                        }
                    }
            }
        }.addOnFailureListener {
            locationText.value = "GPS Error"
            isLocationReady.value = false
        }
    }

    private fun triggerEmergencySos(rawPeople: String, rawMedical: String, rawHazard: String): String {
        fetchLocation()

        val people = if (rawPeople.isBlank()) "1" else rawPeople
        val medical = if (rawMedical.isBlank()) "None" else rawMedical
        val hazard = if (rawHazard.isBlank()) "General Emergency" else rawHazard

        val msgId = "RQ-" + UUID.randomUUID().toString().substring(0, 6).uppercase()

        val alert = EmergencyMessage(
            messageId = msgId,
            senderId = preferences.deviceId,
            timestamp = System.currentTimeMillis(),
            latitude = lastLat,
            longitude = lastLng,
            locationAccuracy = lastAccuracy,
            messageType = "SOS",
            priority = "CRITICAL",
            hopCount = 0,
            originDevice = preferences.deviceId,
            lastRelayDevice = preferences.deviceId,
            status = "CREATED",
            peopleCount = people,
            medicalNeeds = medical,
            hazardType = hazard,
            senderName = preferences.userName,
            senderPhone = preferences.userPhone
        )

        addLog("SOS alert created: [${alert.messageId}] Hazard: $hazard")

        repository.addCreatedMessage(alert)
        networkManager.broadcastMessage(alert)
        networkManager.startBleSosBeacon(alert)

        if (cloudSyncManager.isOnline.value) {
            cloudSyncManager.syncPendingMessages()
        }

        return msgId
    }

    // SIH Demo Simulator actions
    private fun simulatePhoneASos() {
        val demoAlert = EmergencyMessage(
            messageId = "RQ-" + (1000..9999).random(),
            senderId = "PHONE-A",
            timestamp = System.currentTimeMillis(),
            latitude = 28.6139,
            longitude = 77.2090,
            locationAccuracy = 4f,
            messageType = "SOS",
            priority = "CRITICAL",
            hopCount = 0,
            originDevice = "PHONE-A",
            lastRelayDevice = "PHONE-A",
            status = "CREATED",
            peopleCount = "2",
            medicalNeeds = "Severe Bleeding",
            hazardType = "Flash Flood",
            senderName = "Rahul Sharma",
            senderPhone = "+91 9811002233"
        )
        repository.addCreatedMessage(demoAlert)
        addLog("[SIM] Phone A: Created emergency alert [${demoAlert.messageId}]")
    }

    private fun simulatePhoneBRelay() {
        val latest = repository.messageHistory.firstOrNull()
        if (latest != null) {
            val relayed = latest.copy(
                hopCount = latest.hopCount + 1,
                lastRelayDevice = "PHONE-B (Relay)",
                status = "RELAYED"
            )
            repository.messageHistory[0] = relayed
            repository.relayedCount.intValue += 1
            addLog("[SIM] Phone B: Relayed alert [${relayed.messageId}] (Hop ${relayed.hopCount})")
        } else {
            addLog("[SIM] Trigger Phone A SOS first before relaying.")
        }
    }

    private fun simulatePhoneCDelivery() {
        val latest = repository.messageHistory.firstOrNull()
        if (latest != null) {
            val delivered = latest.copy(
                status = "RECEIVED",
                lastRelayDevice = "PHONE-C (Gateway)"
            )
            repository.messageHistory[0] = delivered
            addLog("[SIM] Phone C: Gateway received multi-hop alert [${delivered.messageId}]")
        } else {
            addLog("[SIM] No alert found to deliver.")
        }
    }

    private fun simulateCloudSync() {
        val latest = repository.messageHistory.firstOrNull()
        if (latest != null) {
            repository.markMessageSynced(latest.messageId)
            addLog("[SIM] Internet Restored: Synced [${latest.messageId}] to Command Center")
        } else {
            addLog("[SIM] No alert found to sync.")
        }
    }
}

// Clean Vector Navigation Icons (No emojis, no extra dependencies)
@Composable
private fun HomeNavIcon(selected: Boolean) {
    val tint = if (selected) EmergencyRed else TextMuted
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.85f, h * 0.45f)
            lineTo(w * 0.85f, h * 0.85f)
            lineTo(w * 0.15f, h * 0.85f)
            lineTo(w * 0.15f, h * 0.45f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun MeshNavIcon(selected: Boolean) {
    val tint = if (selected) NetworkBlue else TextMuted
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        // 3 connected nodes
        val c1 = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.25f)
        val c2 = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.75f)
        val c3 = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.75f)

        drawLine(color = tint, start = c1, end = c2, strokeWidth = 1.8.dp.toPx())
        drawLine(color = tint, start = c1, end = c3, strokeWidth = 1.8.dp.toPx())
        drawLine(color = tint, start = c2, end = c3, strokeWidth = 1.8.dp.toPx())

        drawCircle(color = tint, radius = 3.dp.toPx(), center = c1)
        drawCircle(color = tint, radius = 3.dp.toPx(), center = c2)
        drawCircle(color = tint, radius = 3.dp.toPx(), center = c3)
    }
}

@Composable
private fun AlertsNavIcon(selected: Boolean) {
    val tint = if (selected) StatusConnected else TextMuted
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        // Clean notification bell path
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.75f, h * 0.55f)
            lineTo(w * 0.85f, h * 0.7f)
            lineTo(w * 0.15f, h * 0.7f)
            lineTo(w * 0.25f, h * 0.55f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = 1.8.dp.toPx()))
        drawCircle(color = tint, radius = 1.8.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.85f))
    }
}

@Composable
private fun ProfileNavIcon(selected: Boolean) {
    val tint = if (selected) TextPrimary else TextMuted
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        // Head
        drawCircle(color = tint, radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.35f), style = Stroke(width = 1.8.dp.toPx()))
        // Shoulders
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.85f)
            cubicTo(w * 0.2f, h * 0.6f, w * 0.8f, h * 0.6f, w * 0.8f, h * 0.85f)
        }
        drawPath(path, color = tint, style = Stroke(width = 1.8.dp.toPx()))
    }
}