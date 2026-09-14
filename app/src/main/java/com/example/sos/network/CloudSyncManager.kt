package com.example.sos.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.example.sos.data.MessageRepository
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * Handles opportunistic synchronization with Firebase Realtime Database.
 * Alerts are persisted locally and uploaded only when an active internet connection is available.
 */
class CloudSyncManager(
    private val context: Context,
    private val repository: MessageRepository,
    private val preferences: com.example.sos.data.PreferencesManager,
    private val onLog: (String) -> Unit
) {
    private var database: DatabaseReference? = null
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    val isOnline = mutableStateOf(false)
    val isSyncing = mutableStateOf(false)

    init {
        initializeFirebase()
        registerNetworkCallback()
        startPeriodicSync()
    }

    private fun initializeFirebase() {
        try {
            database = FirebaseDatabase.getInstance().reference
            Log.d("ResQMesh", "Firebase initialized successfully.")
        } catch (e: Exception) {
            Log.w("ResQMesh", "Firebase initialization deferred or offline: ${e.message}")
            database = null
        }
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mainHandler.post {
                        if (!isOnline.value) {
                            isOnline.value = true
                            onLog("🌐 Internet Connection Detected. Triggering Cloud Sync...")
                            syncPendingMessages()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    mainHandler.post {
                        isOnline.value = false
                        onLog("📡 Network Offline. Operating in pure Local Mesh mode.")
                    }
                }
            })

            // Initial check
            isOnline.value = checkCurrentInternet()
        } catch (e: Exception) {
            Log.e("ResQMesh", "Network callback registration error: ${e.message}")
        }
    }

    private fun checkCurrentInternet(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun startPeriodicSync() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (checkCurrentInternet()) {
                    if (repository.getPendingCloudMessages().isNotEmpty()) {
                        syncPendingMessages()
                    }
                    // Fetch live status updates from authority command center
                    pollAuthorityUpdates()
                }
                mainHandler.postDelayed(this, 4000)
            }
        }, 4000)
    }

    private fun getGatewayUrl(): String = preferences.getEffectiveGatewayUrl()

    /**
     * Polls the Emergency Operations Center to check if authority has triaged,
     * dispatched a rescue unit, or resolved the incident.
     */
    fun pollAuthorityUpdates() {
        Thread {
            try {
                val url = java.net.URL(getGatewayUrl())
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000

                if (conn.responseCode in 200..299) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(responseText)
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val id = item.optString("messageId")
                        val status = item.optString("status") // "ACTIVE", "IN_PROGRESS", "RESOLVED"
                        val unit = if (item.isNull("assignedUnit")) null else item.optString("assignedUnit")
                        val notes = if (item.isNull("notes")) null else item.optString("notes")

                        if (status == "IN_PROGRESS" || status == "RESOLVED") {
                            mainHandler.post {
                                val changed = repository.updateTriageStatus(id, status, unit, notes)
                                if (changed) {
                                    if (status == "IN_PROGRESS") {
                                        onLog("🚨 Authority Dispatched: $unit for [$id]")
                                    } else if (status == "RESOLVED") {
                                        onLog("✓ Authority Marked [$id] as RESOLVED")
                                    }
                                }
                            }
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                // Offline or unreachable
            }
        }.start()
    }

    /**
     * Uploads pending alerts to both the Local Operations Command Center (REST Gateway)
     * and Firebase Realtime Database.
     * Messages are only removed from the pending queue upon verified upload.
     */
    fun syncPendingMessages() {
        val pending = repository.getPendingCloudMessages()
        if (pending.isEmpty()) return

        isSyncing.value = true
        onLog("☁️ Cloud Sync: Uploading ${pending.size} alerts to Operations Center...")

        for (msg in pending) {
            // 1. Upload to Local REST Command Center
            uploadToRestGateway(msg) { restSuccess ->
                if (restSuccess) {
                    repository.markMessageSynced(msg.messageId)
                    onLog("☁️ Synced [${msg.messageId}] to Command Center (Gateway)")
                    isSyncing.value = false
                }
            }

            // 2. Opportunistic upload to Firebase if configured
            val db = database
            if (db != null) {
                val alertKey = db.child("sos_alerts").push().key ?: msg.messageId
                val payloadMap = mapOf(
                    "messageId" to msg.messageId,
                    "senderId" to msg.senderId,
                    "timestamp" to msg.timestamp,
                    "latitude" to msg.latitude,
                    "longitude" to msg.longitude,
                    "locationAccuracy" to msg.locationAccuracy,
                    "messageType" to msg.messageType,
                    "priority" to msg.priority,
                    "hopCount" to msg.hopCount,
                    "originDevice" to msg.originDevice,
                    "lastRelayDevice" to msg.lastRelayDevice,
                    "peopleCount" to msg.peopleCount,
                    "medicalNeeds" to msg.medicalNeeds,
                    "hazardType" to msg.hazardType,
                    "senderName" to msg.senderName,
                    "senderPhone" to msg.senderPhone,
                    "syncTimestamp" to System.currentTimeMillis()
                )

                db.child("sos_alerts").child(alertKey).setValue(payloadMap)
                    .addOnSuccessListener {
                        repository.markMessageSynced(msg.messageId)
                        onLog("☁️ Synced [${msg.messageId}] to Firebase Cloud")
                        isSyncing.value = false
                    }
                    .addOnFailureListener { err ->
                        Log.w("ResQMesh", "Firebase sync deferred: ${err.message}")
                    }
            }
        }
    }

    private fun uploadToRestGateway(msg: com.example.sos.model.EmergencyMessage, onResult: (Boolean) -> Unit) {
        Thread {
            var success = false
            val currentUrl = getGatewayUrl()
            try {
                val url = java.net.URL(currentUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                val jsonPayload = org.json.JSONObject().apply {
                    put("messageId", msg.messageId)
                    put("senderId", msg.senderId)
                    put("timestamp", msg.timestamp)
                    put("latitude", msg.latitude)
                    put("longitude", msg.longitude)
                    put("locationAccuracy", msg.locationAccuracy)
                    put("peopleCount", msg.peopleCount)
                    put("hazardType", msg.hazardType)
                    put("medicalNeeds", msg.medicalNeeds)
                    put("hopCount", msg.hopCount)
                    put("originDevice", msg.originDevice)
                    put("lastRelayDevice", msg.lastRelayDevice)
                    put("senderName", msg.senderName)
                    put("senderPhone", msg.senderPhone)
                }.toString()

                conn.outputStream.use { os ->
                    os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    success = true
                } else {
                    mainHandler.post { onLog("⚠️ Gateway HTTP $responseCode from $currentUrl") }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.d("ResQMesh", "REST gateway sync: ${e.message}")
                mainHandler.post { onLog("⚠️ Cloud sync error: ${e.localizedMessage ?: e.message}") }
            }
            mainHandler.post { onResult(success) }
        }.start()
    }
}
