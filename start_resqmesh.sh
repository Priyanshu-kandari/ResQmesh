#!/usr/bin/env bash
# ==============================================================================
# ResQMesh 1-Click Presentation & Demo Launcher
# Automatically starts:
# 1. Government Operations Dashboard & REST Gateway (Port 8081)
# 2. APK Download Server for smartphones (Port 8080)
# 3. Cloudflare Public HTTPS Tunnel for worldwide live access
# ==============================================================================

echo "=========================================================="
echo "   🚀 STARTING RESQMESH EMERGENCY COMMAND CENTER"
echo "=========================================================="

# Find local IP
LOCAL_IP=$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K\S+' || hostname -I | awk '{print $1}')

# Check if server is already running on 8081
if ! lsof -i :8081 >/dev/null 2>&1; then
    echo "[1/3] Starting Command Center & REST Gateway on port 8081..."
    python3 SOS-p2p/dashboard/server.py > /tmp/resqmesh_server.log 2>&1 &
    SERVER_PID=$!
else
    echo "[1/3] Command Center already active on port 8081."
fi

# Check if APK server is already running on 8080
if ! lsof -i :8080 >/dev/null 2>&1; then
    echo "[2/3] Starting Phone APK Download Server on port 8080..."
    python3 -m http.server 8080 --directory SOS-p2p/app/build/outputs/apk/debug > /tmp/resqmesh_apk.log 2>&1 &
    APK_PID=$!
else
    echo "[2/3] Phone APK Server already active on port 8080."
fi

# Check if cloudflared tunnel is running
if ! pgrep -f "cloudflared tunnel" >/dev/null 2>&1; then
    echo "[3/3] Starting Cloudflare Public HTTPS Tunnel..."
    ~/.local/bin/cloudflared tunnel --url http://localhost:8081 > /tmp/resqmesh_tunnel.log 2>&1 &
    TUNNEL_PID=$!
    sleep 3
else
    echo "[3/3] Cloudflare Tunnel already active."
fi

echo ""
echo "=========================================================="
echo "   ✅ RESQMESH IS READY FOR YOUR PITCH!"
echo "=========================================================="
echo ""
echo "  💻 Local Dashboard:     http://localhost:8081"
echo "  📱 Phone APK Download:   http://${LOCAL_IP}:8080/app-debug.apk"
echo ""

# Extract tunnel URL if available
TUNNEL_URL=$(grep -o 'https://[a-zA-Z0-9.-]*\.trycloudflare\.com' /tmp/resqmesh_tunnel.log 2>/dev/null | tail -n 1)
if [ -n "$TUNNEL_URL" ]; then
    echo "  🌐 Global Public HTTPS:  ${TUNNEL_URL}"
fi

echo ""
echo "  👉 Tip for judges: Open the Local Dashboard on your laptop screen"
echo "     and let them scan the QR code to install the app on their phones!"
echo "=========================================================="

# If running interactively, open browser
if command -v xdg-open >/dev/null 2>&1 && [ -n "$DISPLAY" ]; then
    xdg-open "http://localhost:8081" >/dev/null 2>&1 &
fi
