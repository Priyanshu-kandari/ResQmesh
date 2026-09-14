package com.example.sos.model

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured emergency packet representing an SOS alert in the ResQMesh network.
 */
data class EmergencyMessage(
    val messageId: String,               // e.g. "RQ-8F29A1"
    val senderId: String,                // Originating device ID (e.g. "RQ-204")
    val timestamp: Long,                 // Unix epoch timestamp in millis
    val latitude: Double,                // GPS Latitude
    val longitude: Double,               // GPS Longitude
    val locationAccuracy: Float = 0f,    // Accuracy in meters
    val messageType: String = "SOS",     // "SOS", "RELAY", "STATUS"
    val priority: String = "CRITICAL",   // "CRITICAL", "HIGH", "MEDIUM"
    var hopCount: Int = 0,               // Increment on each relay hop
    val originDevice: String,            // Initial device that triggered SOS
    var lastRelayDevice: String,         // Most recent relaying node
    var status: String = "CREATED",      // CREATED, SENT, RECEIVED, RELAYED, STORED_OFFLINE, SYNCED, IN_PROGRESS, RESOLVED, FAILED
    var triageStatus: String = "PENDING", // PENDING, IN_PROGRESS, RESOLVED
    var assignedUnit: String? = null,    // e.g. "NDRF Battalion 8"
    var authorityNotes: String? = null,  // Responders notes
    val peopleCount: String = "1",       // Number of persons affected
    val medicalNeeds: String = "None",   // Medical requirements (e.g. Asthma, Bleeding)
    val hazardType: String = "Unknown",  // Hazard category (e.g. Flood, Fire, Collapse)
    val senderName: String = "Citizen",  // Registered name
    val senderPhone: String = ""         // Contact info
) {

    fun getFormattedTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Serializes message to structured JSON string for P2P and Cloud transport.
     */
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("messageId", messageId)
        json.put("senderId", senderId)
        json.put("timestamp", timestamp)
        json.put("latitude", latitude)
        json.put("longitude", longitude)
        json.put("locationAccuracy", locationAccuracy.toDouble())
        json.put("messageType", messageType)
        json.put("priority", priority)
        json.put("hopCount", hopCount)
        json.put("originDevice", originDevice)
        json.put("lastRelayDevice", lastRelayDevice)
        json.put("status", status)
        json.put("peopleCount", peopleCount)
        json.put("medicalNeeds", medicalNeeds)
        json.put("hazardType", hazardType)
        json.put("senderName", senderName)
        json.put("senderPhone", senderPhone)
        return json.toString()
    }

    /**
     * Compressed BLE manufacturer data payload (< 24 bytes).
     * Format: "RQ,id,ppl,med,haz"
     */
    fun toBlePayload(): String {
        val safeId = messageId.takeLast(4)
        val safePpl = peopleCount.take(2)
        val safeMed = medicalNeeds.take(4)
        val safeHaz = hazardType.take(4)
        return "RQ,$safeId,$safePpl,$safeMed,$safeHaz".take(24)
    }

    companion object {
        /**
         * Safely parses incoming payload from JSON or legacy pipe-delimited format.
         */
        fun parse(raw: String): EmergencyMessage? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            // 1. Try parsing JSON
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                return try {
                    val json = JSONObject(trimmed)
                    EmergencyMessage(
                        messageId = json.optString("messageId", "RQ-" + System.currentTimeMillis().toString().takeLast(6)),
                        senderId = json.optString("senderId", "UNKNOWN"),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                        latitude = json.optDouble("latitude", 0.0),
                        longitude = json.optDouble("longitude", 0.0),
                        locationAccuracy = json.optDouble("locationAccuracy", 0.0).toFloat(),
                        messageType = json.optString("messageType", "SOS"),
                        priority = json.optString("priority", "CRITICAL"),
                        hopCount = json.optInt("hopCount", 0),
                        originDevice = json.optString("originDevice", "UNKNOWN"),
                        lastRelayDevice = json.optString("lastRelayDevice", "UNKNOWN"),
                        status = json.optString("status", "RECEIVED"),
                        peopleCount = json.optString("peopleCount", "1"),
                        medicalNeeds = json.optString("medicalNeeds", "None"),
                        hazardType = json.optString("hazardType", "Unknown"),
                        senderName = json.optString("senderName", "Citizen"),
                        senderPhone = json.optString("senderPhone", "")
                    )
                } catch (e: Exception) {
                    null
                }
            }

            // 2. Try parsing legacy pipe-delimited format:
            // ID | SOS | Lat:X | Lng:Y | Time:T | Name:N | Age:A | Ph:P | Ppl:C | Med:M | Haz:H
            if (trimmed.contains("|")) {
                return try {
                    val parts = trimmed.split("|")
                    val msgId = parts.getOrNull(0) ?: ("RQ-" + System.currentTimeMillis().toString().takeLast(6))
                    var lat = 0.0
                    var lng = 0.0
                    var time = System.currentTimeMillis()
                    var name = "Citizen"
                    var phone = ""
                    var ppl = "1"
                    var med = "None"
                    var haz = "Unknown"

                    for (p in parts) {
                        val token = p.trim()
                        when {
                            token.startsWith("Lat:") -> lat = token.removePrefix("Lat:").toDoubleOrNull() ?: 0.0
                            token.startsWith("Lng:") -> lng = token.removePrefix("Lng:").toDoubleOrNull() ?: 0.0
                            token.startsWith("Time:") -> time = token.removePrefix("Time:").toLongOrNull() ?: System.currentTimeMillis()
                            token.startsWith("Name:") -> name = token.removePrefix("Name:")
                            token.startsWith("Ph:") -> phone = token.removePrefix("Ph:")
                            token.startsWith("Ppl:") -> ppl = token.removePrefix("Ppl:")
                            token.startsWith("Med:") -> med = token.removePrefix("Med:")
                            token.startsWith("Haz:") -> haz = token.removePrefix("Haz:")
                        }
                    }

                    EmergencyMessage(
                        messageId = msgId,
                        senderId = "LEGACY",
                        timestamp = time,
                        latitude = lat,
                        longitude = lng,
                        messageType = "SOS",
                        priority = "CRITICAL",
                        hopCount = 1,
                        originDevice = name,
                        lastRelayDevice = "P2P_LEGACY",
                        status = "RECEIVED",
                        peopleCount = ppl,
                        medicalNeeds = med,
                        hazardType = haz,
                        senderName = name,
                        senderPhone = phone
                    )
                } catch (e: Exception) {
                    null
                }
            }

            return null
        }
    }
}
