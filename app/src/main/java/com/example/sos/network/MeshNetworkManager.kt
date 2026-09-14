package com.example.sos.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.sos.data.MessageRepository
import com.example.sos.model.EmergencyMessage
import com.example.sos.model.MeshDevice
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

/**
 * Manages Google Nearby Connections mesh networking and BLE hardware beaconing.
 * Implements 12-second advertising/discovery role cycling and multi-hop forwarding.
 */
class MeshNetworkManager(
    private val context: Context,
    private val deviceId: String,
    private val repository: MessageRepository,
    private val onLog: (String) -> Unit
) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Service identifier (compatible with legacy peers)
    private val SERVICE_ID = "sos_mesh_v2"

    // Mesh Strategy: P2P_CLUSTER allows M-to-N multi-device mesh connections
    private val MESH_STRATEGY = Strategy.P2P_CLUSTER

    // Observable states for UI
    val connectedDevices = mutableStateListOf<MeshDevice>()
    val currentRole = mutableStateOf("IDLE") // "ADVERTISING", "DISCOVERY", "CONNECTED", "IDLE"
    val isBleBroadcasting = mutableStateOf(false)

    private var isAdvertising = false
    private var isConnecting = false

    // 12-second role switcher
    private val switchRoleRunnable = object : Runnable {
        override fun run() {
            // If connected or currently establishing a handshake, do not interrupt
            if (connectedDevices.isNotEmpty() || isConnecting) {
                mainHandler.postDelayed(this, 12000)
                return
            }

            stopEndpoints()

            if (isAdvertising) {
                currentRole.value = "DISCOVERY"
                onLog("🔄 Switching to DISCOVERY Mode...")
                startDiscovery()
            } else {
                currentRole.value = "ADVERTISING"
                onLog("🔄 Switching to ADVERTISING Mode...")
                startAdvertising()
            }
            isAdvertising = !isAdvertising
            mainHandler.postDelayed(this, 12000)
        }
    }

    fun startMesh() {
        onLog("🚀 ResQMesh Engine: Initiating Auto-Mesh...")
        mainHandler.removeCallbacks(switchRoleRunnable)
        mainHandler.post(switchRoleRunnable)
    }

    fun stopMesh() {
        mainHandler.removeCallbacks(switchRoleRunnable)
        stopEndpoints()
        currentRole.value = "IDLE"
        connectedDevices.clear()
        isConnecting = false
    }

    private fun stopEndpoints() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {
            Log.e("ResQMesh", "Error stopping endpoints: ${e.message}")
        }
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(MESH_STRATEGY)
            .setLowPower(false)
            .build()

        connectionsClient.startAdvertising(deviceId, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnFailureListener {
                onLog("❌ Advertising Failed: ${it.message}")
            }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(MESH_STRATEGY)
            .setLowPower(false)
            .build()

        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnFailureListener {
                onLog("❌ Discovery Failed: ${it.message}")
            }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            onLog("👀 Discovered Peer: ${info.endpointName} ($endpointId)")
            isConnecting = true
            connectionsClient.requestConnection(deviceId, endpointId, connectionLifecycleCallback)
                .addOnFailureListener {
                    isConnecting = false
                    onLog("⚠️ Connection Request Failed: ${it.message}")
                }
        }

        override fun onEndpointLost(endpointId: String) {
            connectedDevices.removeAll { it.endpointId == endpointId }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            onLog("🤝 Peer Handshake: ${info.endpointName}")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            isConnecting = false
            if (result.status.isSuccess) {
                val peer = MeshDevice(endpointId = endpointId, deviceName = "Device $endpointId")
                if (connectedDevices.none { it.endpointId == endpointId }) {
                    connectedDevices.add(peer)
                }
                currentRole.value = "CONNECTED (${connectedDevices.size})"
                onLog("✅ Connected to $endpointId")

                // Flush store-and-forward queue to the new peer
                flushQueueToPeer(endpointId)
            } else {
                onLog("⚠️ Connection Rejected: ${result.status.statusMessage}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedDevices.removeAll { it.endpointId == endpointId }
            onLog("🔌 Disconnected from $endpointId")
            if (connectedDevices.isEmpty()) {
                currentRole.value = if (isAdvertising) "ADVERTISING" else "DISCOVERY"
                mainHandler.post(switchRoleRunnable)
            } else {
                currentRole.value = "CONNECTED (${connectedDevices.size})"
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes()
            if (bytes == null) {
                Log.w("ResQMesh", "Received non-byte payload; ignoring.")
                return
            }

            val rawString = String(bytes)
            onLog("📩 Packet Received from $endpointId")

            val (message, shouldRelay) = repository.handleIncomingMessage(rawString, endpointId)
            if (message != null) {
                onLog("🚨 Alert: [${message.messageId}] Hop:${message.hopCount} | ${message.hazardType} (${message.peopleCount} ppl)")
                if (shouldRelay) {
                    onLog("🔄 Store & Forward: Relaying [${message.messageId}] to nearby peers...")
                    forwardMessage(message, endpointId)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    /**
     * Broadcasts an emergency alert to all connected peers.
     */
    fun broadcastMessage(message: EmergencyMessage) {
        val payload = Payload.fromBytes(message.toJsonString().toByteArray())
        if (connectedDevices.isEmpty()) {
            onLog("⚠️ No active peers. Alert cached in offline store-and-forward queue.")
        } else {
            for (device in connectedDevices) {
                connectionsClient.sendPayload(device.endpointId, payload)
                onLog("📤 Dispatched [${message.messageId}] to ${device.getDisplayBadge()}")
            }
        }
    }

    /**
     * Relays an alert to all peers except the sender node.
     */
    fun forwardMessage(message: EmergencyMessage, excludeEndpoint: String?) {
        val payload = Payload.fromBytes(message.toJsonString().toByteArray())
        for (device in connectedDevices) {
            if (device.endpointId != excludeEndpoint) {
                connectionsClient.sendPayload(device.endpointId, payload)
                device.messagesRelayedThrough += 1
                onLog("🔁 Forwarded [${message.messageId}] to ${device.getDisplayBadge()} (Hop ${message.hopCount})")
            }
        }
    }

    private fun flushQueueToPeer(endpointId: String) {
        if (repository.pendingPeerQueue.isNotEmpty()) {
            onLog("📨 Delivering ${repository.pendingPeerQueue.size} queued alerts to $endpointId...")
            for (queued in repository.pendingPeerQueue) {
                val payload = Payload.fromBytes(queued.toJsonString().toByteArray())
                connectionsClient.sendPayload(endpointId, payload)
            }
        }
    }

    /**
     * Broadcasts a 24-byte hardware BLE manufacturer beacon (Fallback for non-mesh devices).
     */
    @SuppressLint("MissingPermission")
    fun startBleSosBeacon(message: EmergencyMessage) {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

            if (advertiser == null) {
                onLog("ℹ️ BLE Beacon unavailable (Bluetooth disabled or unsupported)")
                return
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            val payloadBytes = message.toBlePayload().toByteArray()
            val data = AdvertiseData.Builder()
                .addManufacturerData(0x02E5, payloadBytes)
                .build()

            val callback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    isBleBroadcasting.value = true
                    onLog("📡 BLE Hardware Beacon: Broadcasting [${message.toBlePayload()}]")
                }

                override fun onStartFailure(errorCode: Int) {
                    isBleBroadcasting.value = false
                    onLog("⚠️ BLE Beacon Failed: Error Code $errorCode")
                }
            }

            advertiser.startAdvertising(settings, data, callback)

            // Stop BLE beacon after 30 seconds to conserve battery
            mainHandler.postDelayed({
                try {
                    advertiser.stopAdvertising(callback)
                    isBleBroadcasting.value = false
                } catch (e: Exception) {
                    Log.e("ResQMesh", "Error stopping BLE: ${e.message}")
                }
            }, 30000)
        } catch (e: SecurityException) {
            onLog("❌ BLE Beacon: Bluetooth Permission Missing")
        } catch (e: Exception) {
            onLog("⚠️ BLE Beacon Error: ${e.message}")
        }
    }
}
