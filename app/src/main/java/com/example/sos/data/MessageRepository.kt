package com.example.sos.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import com.example.sos.model.EmergencyMessage

/**
 * Thread-safe Store-and-Forward message repository.
 * Implements duplicate message suppression, relay queue management, and delivery audit history.
 */
class MessageRepository(private val myDeviceId: String) {

    // Observable history list for Jetpack Compose UI
    val messageHistory = mutableStateListOf<EmergencyMessage>()

    // Message IDs already processed to prevent duplicate relay cycles
    private val seenMessageIds = mutableSetOf<String>()

    // Queue of messages pending transmission to newly discovered peers
    val pendingPeerQueue = mutableListOf<EmergencyMessage>()

    // Queue of messages pending upload to Firebase Realtime Database
    val pendingCloudQueue = mutableListOf<EmergencyMessage>()

    // Total messages successfully relayed by this node
    val relayedCount = mutableIntStateOf(0)

    /**
     * Registers a new locally created emergency SOS message.
     */
    @Synchronized
    fun addCreatedMessage(msg: EmergencyMessage) {
        if (seenMessageIds.contains(msg.messageId)) return
        seenMessageIds.add(msg.messageId)

        msg.status = "CREATED"
        messageHistory.add(0, msg)
        pendingPeerQueue.add(msg)
        pendingCloudQueue.add(msg)
    }

    /**
     * Processes an incoming raw payload from a peer endpoint.
     * @return Pair(EmergencyMessage?, Boolean):
     * - EmergencyMessage: The parsed message (or null if unparseable).
     * - Boolean: true if this is a NEW message that must be relayed to other peers; false if duplicate or invalid.
     */
    @Synchronized
    fun handleIncomingMessage(rawPayload: String, fromEndpoint: String): Pair<EmergencyMessage?, Boolean> {
        val parsed = EmergencyMessage.parse(rawPayload) ?: return Pair(null, false)

        // Duplicate suppression: if already seen, discard from re-forwarding
        if (seenMessageIds.contains(parsed.messageId)) {
            return Pair(parsed, false)
        }

        seenMessageIds.add(parsed.messageId)

        // Store received message in audit history
        parsed.status = "RECEIVED"
        messageHistory.add(0, parsed)

        // Prepare forwarded copy with incremented hop count
        val forwardCopy = parsed.copy(
            hopCount = parsed.hopCount + 1,
            lastRelayDevice = myDeviceId,
            status = "RELAYED"
        )

        pendingPeerQueue.add(forwardCopy)
        pendingCloudQueue.add(forwardCopy)
        relayedCount.intValue += 1

        return Pair(forwardCopy, true)
    }

    /**
     * Marks a message as successfully uploaded to Firebase cloud.
     */
    @Synchronized
    fun markMessageSynced(messageId: String) {
        pendingCloudQueue.removeAll { it.messageId == messageId }
        val index = messageHistory.indexOfFirst { it.messageId == messageId }
        if (index != -1) {
            val current = messageHistory[index]
            // Keep status if already marked IN_PROGRESS or RESOLVED
            val newStatus = if (current.status == "IN_PROGRESS" || current.status == "RESOLVED") current.status else "SYNCED"
            val updated = current.copy(status = newStatus)
            messageHistory[index] = updated
        }
    }

    /**
     * Updates the status of an emergency alert when the Emergency Operations Center
     * dispatches a relief squad or resolves the incident.
     */
    @Synchronized
    fun updateTriageStatus(messageId: String, newStatus: String, assignedUnit: String?, notes: String?): Boolean {
        val index = messageHistory.indexOfFirst { it.messageId == messageId }
        if (index != -1) {
            val current = messageHistory[index]
            if (current.status != newStatus || current.assignedUnit != assignedUnit) {
                val updated = current.copy(
                    status = newStatus,
                    triageStatus = newStatus,
                    assignedUnit = assignedUnit ?: current.assignedUnit,
                    authorityNotes = notes ?: current.authorityNotes
                )
                messageHistory[index] = updated
                return true
            }
        }
        return false
    }

    /**
     * Returns a snapshot of messages pending cloud upload.
     */
    @Synchronized
    fun getPendingCloudMessages(): List<EmergencyMessage> {
        return ArrayList(pendingCloudQueue)
    }

    /**
     * Clears history (used for demo/reset purposes).
     */
    @Synchronized
    fun clear() {
        messageHistory.clear()
        seenMessageIds.clear()
        pendingPeerQueue.clear()
        pendingCloudQueue.clear()
        relayedCount.intValue = 0
    }
}
