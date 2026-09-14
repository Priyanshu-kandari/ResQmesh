# RESQMESH — SIH 2026 MVP

> **Emergency communication when the network fails.**  
> *When infrastructure fails, nearby phones become the network.*

![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen?style=flat-square)
![Platform](https://img.shields.io/badge/Platform-Android%20(API%2024%2B)-blue?style=flat-square)
![Stack](https://img.shields.io/badge/Stack-Kotlin%20%7C%20Jetpack%20Compose%20%7C%20Nearby%20Connections-blueviolet?style=flat-square)
![Hackathon](https://img.shields.io/badge/SIH-2026%20MVP-red?style=flat-square)

---

## 1. Problem Statement

During major natural disasters (earthquakes, flash floods, cyclones) or infrastructure outages, cellular towers and broadband lines often fail within minutes. Stranded citizens and emergency responders cannot dial emergency helplines or report GPS coordinates, creating communication blackouts during the critical golden rescue window.

---

## 2. The ResQMesh Solution

**ResQMesh** is a decentralized, offline-first peer-to-peer distress communication system. It turns consumer Android smartphones into ad-hoc mesh relay nodes that communicate directly over local radio (Wi-Fi Direct and Bluetooth Low Energy) without cellular towers, SIM cards, or internet access.

Alerts propagate through nearby phones using **multi-hop store-and-forward routing**. As soon as any device in the mesh reaches an area with active internet connectivity, it automatically bridges all queued distress alerts to the **ResQMesh Command Center** cloud dashboard.

---

## 3. Communication Flow

```text
PHONE A (Disaster Victim)
  🚨 SOS Broadcast (GPS, Casualties, Hazard)
     │
     │ Google Nearby Connections (P2P Cluster / BLE Beacon)
     ▼
PHONE B (Intermediate Citizen / Responder)
  🔄 STORE & FORWARD (Deduplication + Hop Count +1)
     │
     │ Local P2P Relay
     ▼
PHONE C (Gateway Node)
  📩 Multi-Hop SOS Delivered
     │
     │ Internet Connection Restored
     ▼
FIREBASE REALTIME DATABASE
     │
     ▼
RESQMESH COMMAND CENTER (Web Rescue Dashboard)
  🗺️ Live Geospatial Incident Map & Casualty Triage
```

---

## 4. Key Architectural Features

1. **Autonomous Role Cycling:**  
   Devices dynamically alternate between **Advertising** and **Discovery** every 12 seconds (`Strategy.P2P_CLUSTER`), enabling ad-hoc mesh formation without designated master hardware. Handshake locks prevent premature disconnection during peer negotiation.
2. **Hardware BLE Beacon Fallback:**  
   Simultaneously broadcasts a compressed 24-byte BLE manufacturer data beacon (`0x02E5`). This allows discovery even by devices not actively paired in the Wi-Fi Direct mesh.
3. **Store-and-Forward Engine with Loop Prevention:**  
   Every emergency packet carries a unique identifier (`messageId`), origin node (`originDevice`), and incrementing `hopCount`. Receiving nodes filter duplicates through an in-memory hash set, preventing broadcast storms and infinite relay loops.
4. **Resilient GPS Telemetry:**  
   Utilizes `FusedLocationProviderClient` with a high-accuracy fallback query. If GPS permission is denied or coordinates are unavailable, the alert still transmits cleanly without crashing.
5. **Non-Dropping Cloud Synchronization:**  
   Alerts cached in local storage are only removed from the pending upload queue once the Firebase write confirmation (`addOnSuccessListener`) is received, ensuring zero data loss during intermittent connectivity.
6. **ResQMesh Command Center Web Console:**  
   A dedicated desktop dashboard for incident commanders with real-time Leaflet.js map markers, casualty triage metrics, and packet hop inspection.

---

## 5. Technology Stack

* **Mobile App:** Kotlin, Jetpack Compose, Material 3
* **Mesh & P2P Networking:** Google Play Services Nearby Connections API (`Strategy.P2P_CLUSTER` & `P2P_STAR`)
* **Hardware Beaconing:** Android Bluetooth LE Advertiser (`AdvertiseSettings`, `AdvertiseData`)
* **Location Services:** Google Play Services Location (`FusedLocationProviderClient`)
* **Cloud Backend:** Firebase Realtime Database
* **Rescue Dashboard:** HTML5, CSS3, JavaScript, Leaflet.js, OpenStreetMap CartoDB Dark Tiles

---

## 6. SIH 2026 Demonstration Guide (Step-by-Step)

To demonstrate the full A → B → C multi-hop relay to hackathon judges:

1. **Setup:** Install the debug APK (`app-debug.apk`) on three Android phones (Phone A, Phone B, Phone C).
2. **Offline Isolation:** Turn **Airplane Mode ON** (or disable Wi-Fi and Mobile Data) on all three phones. Ensure **Bluetooth** and **Location** remain **ON**.
3. **Launch & Profile:** Open ResQMesh on all phones and verify the node badges (e.g. `RQ-8F29`, `RQ-204`).
4. **Automatic Peer Mesh:** Within 12–24 seconds, the devices will discover each other via Nearby Connections and display `CONNECTED` in the Mesh tab.
5. **Step 1 (Phone A SOS):** On Phone A, enter emergency details (e.g., *Flood, 2 people*) and tap the large red **SOS** button.
6. **Step 2 (Phone B Relay):** Phone B receives the alert, logs `📩 Packet Received`, increments `hopCount = 1`, logs `🔄 Store & Forward: Relaying...`, and forwards the alert.
7. **Step 3 (Phone C Delivery):** Phone C (out of direct range of Phone A, but connected to Phone B) receives the alert and logs `🚨 Alert: [RQ-XXXX] Hop:2`.
8. **Step 4 (Internet Recovery):** Turn **Mobile Data / Wi-Fi ON** on Phone C.
9. **Step 5 (Cloud Sync):** Phone C detects internet and logs `🌐 Internet Detected -> ☁️ Synced to Command Center`.
10. **Step 6 (Dashboard Verification):** Open `dashboard/index.html` on your laptop to view the alert plotted live on the incident map with the 2-hop audit trail.

*(Note: For presentations without 3 physical phones, switch to the in-app **Demo** tab to run the visual step-by-step simulation).*

---

## 7. Realistic Technical Scope & Honest Disclaimers

As an SIH 2026 hackathon MVP:
* **Transmission Range:** Relies on standard consumer Wi-Fi Direct (~30–70 meters line-of-sight) and Bluetooth LE (~10–20 meters). It does not claim kilometer-range or satellite capabilities.
* **Store-and-Forward Delay:** Hops are subject to peer proximity and the 12-second discovery duty cycle.
* **Environment Factors:** Dense concrete structures, RF interference, and aggressive OS battery optimizations may affect peer discovery speed.

---

## 8. Building & Running

### Prerequisites
* Java 17 or Java 21 (`~/.jdks/temurin-21`)
* Android SDK (API 34 or 36) in `~/Android/Sdk`

### Build Debug APK
```bash
cd SOS-p2p
./gradlew assembleDebug
```
The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install via ADB
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Launch Command Center Dashboard
Open `SOS-p2p/dashboard/index.html` in any modern web browser.
