#!/usr/bin/env python3
"""
ResQMesh Incident Command Server
Provides:
1. High-speed static file server for Command Center dashboard
2. REST API for Realtime Phone App -> Government Dashboard connectivity:
   - GET  /api/alerts        -> Fetch all incident alerts
   - POST /api/alerts        -> Phone app uploads distress alert via Wi-Fi/cellular gateway
   - PATCH /api/alerts/<id>  -> Authority dispatches unit or marks resolved
   - POST /api/simulate      -> Triggers demonstration distress event
"""

import http.server
import socketserver
import json
import os
import sys
import time
import urllib.parse
from datetime import datetime

PORT = int(os.environ.get("PORT", 8081))
DIRECTORY = os.path.dirname(os.path.abspath(__file__))

# Initial mock alerts for emergency operations demonstrations
alerts = [
    {
        "messageId": "RQ-8F29A1",
        "senderId": "DEVICE-001",
        "originDevice": "Phone A (RQ-8F29)",
        "lastRelayDevice": "Phone B (RQ-204)",
        "timestamp": int(time.time() * 1000) - 1000 * 60 * 12,
        "latitude": 28.6210,
        "longitude": 77.2150,
        "locationAccuracy": 4,
        "peopleCount": "3",
        "hazardType": "Flash Flood / Rising Water",
        "medicalNeeds": "Hypothermia, First Aid",
        "hopCount": 2,
        "status": "ACTIVE",  # ACTIVE | IN_PROGRESS | RESOLVED
        "assignedUnit": None,
        "notes": "Victims trapped on elevated terrace.",
        "senderName": "Rohan Verma",
        "senderPhone": "+91 98100 12345",
        "timeline": [
            {"time": "12m ago", "text": "Distress beacon initiated via Nearby P2P mesh", "type": "alert"},
            {"time": "9m ago", "text": "Relayed by Phone B (RQ-204) [Hop 1]", "type": "alert"},
            {"time": "7m ago", "text": "Gateway synchronized to Operations Cloud", "type": "alert"}
        ]
    },
    {
        "messageId": "RQ-7A12BC",
        "senderId": "DEVICE-004",
        "originDevice": "Phone D (RQ-7A12)",
        "lastRelayDevice": "Phone D (RQ-7A12)",
        "timestamp": int(time.time() * 1000) - 1000 * 60 * 25,
        "latitude": 28.6080,
        "longitude": 77.2010,
        "locationAccuracy": 3,
        "peopleCount": "1",
        "hazardType": "Building Collapse",
        "medicalNeeds": "Leg Fracture, Trauma Care",
        "hopCount": 0,
        "status": "IN_PROGRESS",
        "assignedUnit": "NDRF Battalion 8 (Quick Response)",
        "notes": "Acoustic debris search team on site.",
        "senderName": "Sunita Rao",
        "senderPhone": "+91 98111 54321",
        "timeline": [
            {"time": "25m ago", "text": "Direct P2P distress received", "type": "alert"},
            {"time": "18m ago", "text": "Dispatched: NDRF Battalion 8 (Quick Response)", "type": "dispatch"}
        ]
    },
    {
        "messageId": "RQ-2C90A4",
        "senderId": "DEVICE-019",
        "originDevice": "Phone M (RQ-2C90)",
        "lastRelayDevice": "Phone P (RQ-1102)",
        "timestamp": int(time.time() * 1000) - 1000 * 60 * 55,
        "latitude": 28.6280,
        "longitude": 77.2250,
        "locationAccuracy": 5,
        "peopleCount": "2",
        "hazardType": "Waterlogged Underpass",
        "medicalNeeds": "None reported",
        "hopCount": 3,
        "status": "RESOLVED",
        "assignedUnit": "Civil Defense Volunteer Brigade",
        "notes": "Both victims evacuated safely to Relief Camp 4.",
        "senderName": "Pooja Sharma",
        "senderPhone": "+91 98765 43210",
        "timeline": [
            {"time": "55m ago", "text": "Received via 3 mesh hops", "type": "alert"},
            {"time": "45m ago", "text": "Dispatched: Civil Defense Volunteer Brigade", "type": "dispatch"},
            {"time": "15m ago", "text": "Rescue complete: Victims safely evacuated", "type": "resolve"}
        ]
    }
]

class ResQMeshHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def _send_cors_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, PATCH, DELETE, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')

    def do_OPTIONS(self):
        self.send_response(200)
        self._send_cors_headers()
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/api/alerts":
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self._send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps(alerts).encode('utf-8'))
            return
        
        # Static files
        super().do_GET()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        content_length = int(self.headers.get('Content-Length', 0))
        post_data = self.rfile.read(content_length).decode('utf-8')

        if parsed.path == "/api/alerts":
            # Incoming alert from Android phone app or gateway node!
            try:
                payload = json.loads(post_data) if post_data else {}
            except Exception as e:
                self.send_response(400)
                self._send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({"error": f"Invalid JSON: {e}"}).encode('utf-8'))
                return

            message_id = payload.get("messageId") or f"RQ-{int(time.time()*1000)%100000:04X}"
            
            # Check if alert already exists (deduplication)
            existing = next((a for a in alerts if a["messageId"] == message_id), None)
            if existing:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self._send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({"status": "already_exists", "messageId": message_id}).encode('utf-8'))
                return

            new_alert = {
                "messageId": message_id,
                "senderId": payload.get("senderId", "DEVICE-UNKNOWN"),
                "originDevice": payload.get("originDevice", f"Device ({message_id})"),
                "lastRelayDevice": payload.get("lastRelayDevice", "Direct"),
                "timestamp": payload.get("timestamp", int(time.time() * 1000)),
                "latitude": float(payload.get("latitude", 28.6139)),
                "longitude": float(payload.get("longitude", 77.2090)),
                "locationAccuracy": int(payload.get("locationAccuracy", 4)),
                "peopleCount": str(payload.get("peopleCount", "1")),
                "hazardType": payload.get("hazardType", "Emergency Distress"),
                "medicalNeeds": payload.get("medicalNeeds", "Unspecified"),
                "hopCount": int(payload.get("hopCount", 0)),
                "status": "ACTIVE",
                "assignedUnit": None,
                "notes": payload.get("notes", ""),
                "senderName": payload.get("senderName", "Citizen"),
                "senderPhone": payload.get("senderPhone", ""),
                "timeline": [
                    {
                        "time": "Just now",
                        "text": f"Uploaded by {payload.get('originDevice', 'phone')} via {payload.get('hopCount', 0)} hops",
                        "type": "alert"
                    }
                ]
            }

            alerts.insert(0, new_alert)
            print(f"[RESQMESH] Received emergency alert from Phone App: {message_id} ({new_alert['hazardType']})")

            self.send_response(201)
            self.send_header('Content-Type', 'application/json')
            self._send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps({"status": "created", "messageId": message_id}).encode('utf-8'))
            return

        if parsed.path == "/api/simulate":
            # Quick simulation trigger
            sim_id = f"RQ-{int(time.time()*1000)%100000:04X}"
            new_alert = {
                "messageId": sim_id,
                "senderId": f"DEVICE-{int(time.time()%900 + 100)}",
                "originDevice": f"Phone ({sim_id})",
                "lastRelayDevice": "RQ-9102",
                "timestamp": int(time.time() * 1000),
                "latitude": 28.6139 + (0.015 * (time.time() % 3 - 1)),
                "longitude": 77.2090 + (0.015 * (time.time() % 2 - 1)),
                "locationAccuracy": 4,
                "peopleCount": "2",
                "hazardType": "Flash Flood / Rising Water",
                "medicalNeeds": "Urgent Evacuation",
                "hopCount": 2,
                "status": "ACTIVE",
                "assignedUnit": None,
                "notes": "Simulated field distress signal.",
                "senderName": "Simulated Citizen",
                "senderPhone": "+91 99999 00000",
                "timeline": [
                    {"time": "Just now", "text": "Alert triggered via P2P mesh relay", "type": "alert"}
                ]
            }
            alerts.insert(0, new_alert)

            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self._send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps(new_alert).encode('utf-8'))
            return

        self.send_response(404)
        self.end_headers()

    def do_PATCH(self):
        # Update incident status (e.g. In Progress, Dispatched, Solved)
        parsed = urllib.parse.urlparse(self.path)
        parts = parsed.path.strip("/").split("/")
        if len(parts) == 3 and parts[0] == "api" and parts[1] == "alerts":
            target_id = parts[2]
            content_length = int(self.headers.get('Content-Length', 0))
            patch_data = self.rfile.read(content_length).decode('utf-8')
            try:
                updates = json.loads(patch_data)
            except Exception:
                updates = {}

            alert = next((a for a in alerts if a["messageId"] == target_id), None)
            if not alert:
                self.send_response(404)
                self._send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({"error": "Alert not found"}).encode('utf-8'))
                return

            if "status" in updates:
                alert["status"] = updates["status"]
            if "assignedUnit" in updates:
                alert["assignedUnit"] = updates["assignedUnit"]
            if "notes" in updates:
                alert["notes"] = updates["notes"]

            # Add to timeline
            now_str = datetime.now().strftime("%I:%M %p")
            if updates.get("status") == "IN_PROGRESS":
                unit = updates.get("assignedUnit", "Disaster Squad")
                alert["timeline"].append({
                    "time": now_str,
                    "text": f"Dispatched: {unit}. Note: {updates.get('notes', '')}",
                    "type": "dispatch"
                })
            elif updates.get("status") == "RESOLVED":
                alert["timeline"].append({
                    "time": now_str,
                    "text": "Incident resolved: Victims secured safely.",
                    "type": "resolve"
                })
            elif updates.get("status") == "ACTIVE":
                alert["timeline"].append({
                    "time": now_str,
                    "text": "Incident reopened by dispatcher.",
                    "type": "alert"
                })

            print(f"[RESQMESH] Updated {target_id} -> status: {alert['status']}")

            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self._send_cors_headers()
            self.end_headers()
            self.wfile.write(json.dumps(alert).encode('utf-8'))
            return

        self.send_response(404)
        self.end_headers()

    def do_DELETE(self):
        parsed = urllib.parse.urlparse(self.path)
        parts = parsed.path.strip("/").split("/")
        if len(parts) == 3 and parts[0] == "api" and parts[1] == "alerts":
            target_id = parts[2]
            global alerts
            before_len = len(alerts)
            alerts = [a for a in alerts if a["messageId"] != target_id]
            if len(alerts) < before_len:
                print(f"[RESQMESH] Dismissed/Removed completed alert: {target_id}")
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self._send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({"status": "deleted", "messageId": target_id}).encode('utf-8'))
                return
            else:
                self.send_response(404)
                self.send_header('Content-Type', 'application/json')
                self._send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({"error": "Alert not found"}).encode('utf-8'))
                return

        self.send_response(404)
        self.end_headers()


def run_server():
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("", PORT), ResQMeshHandler) as httpd:
        print(f"=======================================================")
        print(f"  ResQMesh Operations Server running on port {PORT}")
        print(f"  Dashboard: http://localhost:{PORT}")
        print(f"  API Endpoint: http://localhost:{PORT}/api/alerts")
        print(f"=======================================================")
        httpd.serve_forever()

if __name__ == "__main__":
    run_server()
