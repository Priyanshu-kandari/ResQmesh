package com.example.sos.model

/**
 * Represents a peer node in the local ResQMesh ad-hoc network.
 */
data class MeshDevice(
    val endpointId: String,
    val deviceName: String,
    var connectionState: String = "CONNECTED", // "DISCOVERED", "CONNECTING", "CONNECTED", "RELAY_NODE"
    val connectedAt: Long = System.currentTimeMillis(),
    var messagesRelayedThrough: Int = 0
) {
    /**
     * Formatted display label (e.g. "Device RQ-204" or raw endpoint)
     */
    fun getDisplayBadge(): String {
        return if (deviceName.startsWith("RQ-")) {
            deviceName
        } else if (endpointId.length >= 4) {
            "RQ-${endpointId.takeLast(4).uppercase()}"
        } else {
            "RQ-NODE"
        }
    }
}
