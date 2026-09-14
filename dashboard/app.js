// ResQMesh Operations Console — Clean Modern Dashboard
let map;
let markers = {};
let alerts = [];
let selectedAlertId = null;
let currentFilter = "ALL";
let searchQuery = "";

document.addEventListener("DOMContentLoaded", () => {
  initMap();
  initListeners();
  fetchAlerts();
  // Poll for live alerts from phone app / gateway every 2 seconds
  setInterval(fetchAlerts, 2000);
});

function initMap() {
  map = L.map('map', {
    center: [28.6139, 77.2090],
    zoom: 13,
    zoomControl: true
  });

  // Modern dark tile layer (CartoDB Dark Matter)
  L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
    attribution: '&copy; OpenStreetMap &copy; CARTO',
    subdomains: 'abcd',
    maxZoom: 19
  }).addTo(map);
}

function initListeners() {
  // Simulate SOS button
  document.getElementById("btn-simulate").addEventListener("click", () => {
    fetch('/api/simulate', { method: 'POST' })
      .then(r => r.json())
      .then(newAlert => {
        fetchAlerts().then(() => {
          selectAlert(newAlert.messageId);
        });
      })
      .catch(() => {
        // Fallback local simulation if offline
        const simId = "RQ-" + Math.random().toString(36).substring(2, 6).toUpperCase();
        alerts.unshift({
          messageId: simId,
          senderId: "DEVICE-SIM",
          originDevice: `Phone (${simId})`,
          lastRelayDevice: "RQ-3301",
          timestamp: Date.now(),
          latitude: 28.6139 + (Math.random() - 0.5) * 0.04,
          longitude: 77.2090 + (Math.random() - 0.5) * 0.04,
          locationAccuracy: 4,
          peopleCount: "2",
          hazardType: "Flash Flood / High Water",
          medicalNeeds: "Hypothermia",
          hopCount: 1,
          status: "ACTIVE",
          assignedUnit: null,
          notes: "",
          senderName: "Field Citizen",
          senderPhone: "+91 98000 11111",
          timeline: [{ time: "Just now", text: "Simulated distress call received", type: "alert" }]
        });
        renderUI();
        selectAlert(simId);
      });
  });

  // Filter Tabs
  document.querySelectorAll(".filter-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".filter-btn").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      currentFilter = btn.dataset.filter;
      renderUI();
    });
  });

  // Search input
  document.getElementById("search-input").addEventListener("input", (e) => {
    searchQuery = e.target.value.toLowerCase().trim();
    renderUI();
  });

  // Drawer Close Button
  document.getElementById("drawer-close-btn").addEventListener("click", () => {
    document.getElementById("inspector-drawer").classList.remove("open");
    selectedAlertId = null;
    renderAlertList();
  });

  // Modal Cancel
  document.getElementById("modal-cancel-btn").addEventListener("click", () => {
    document.getElementById("dispatch-modal").classList.remove("open");
  });

  // Modal Confirm Dispatch
  document.getElementById("modal-confirm-btn").addEventListener("click", () => {
    const unit = document.getElementById("modal-unit-select").value;
    const notes = document.getElementById("modal-notes-input").value;
    if (!selectedAlertId) return;

    fetch(`/api/alerts/${selectedAlertId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        status: "IN_PROGRESS",
        assignedUnit: unit,
        notes: notes
      })
    }).then(r => r.json()).then(updated => {
      document.getElementById("dispatch-modal").classList.remove("open");
      fetchAlerts();
    }).catch(() => {
      // Local fallback
      const target = alerts.find(a => a.messageId === selectedAlertId);
      if (target) {
        target.status = "IN_PROGRESS";
        target.assignedUnit = unit;
        target.notes = notes;
        target.timeline.push({ time: "Just now", text: `Dispatched: ${unit}`, type: "dispatch" });
        renderUI();
        selectAlert(selectedAlertId);
      }
      document.getElementById("dispatch-modal").classList.remove("open");
    });
  });

  // Export CSV
  document.getElementById("btn-export").addEventListener("click", exportCSV);

  // QR Modal listeners
  const btnQr = document.getElementById("btn-qr");
  if (btnQr) {
    btnQr.addEventListener("click", () => {
      document.getElementById("qr-modal").classList.add("open");
    });
  }
  const btnQrClose = document.getElementById("qr-close-btn");
  if (btnQrClose) {
    btnQrClose.addEventListener("click", () => {
      document.getElementById("qr-modal").classList.remove("open");
    });
  }
}

function fetchAlerts() {
  return fetch('/api/alerts')
    .then(res => res.json())
    .then(data => {
      if (Array.isArray(data) && data.length > 0) {
        alerts = data;
        renderUI();
        if (selectedAlertId) {
          const stillExists = alerts.find(a => a.messageId === selectedAlertId);
          if (stillExists) renderDrawer(stillExists);
        }
      } else if (alerts.length === 0) {
        loadDefaultFallbackAlerts();
      }
    })
    .catch(err => {
      if (alerts.length === 0) {
        loadDefaultFallbackAlerts();
      }
    });
}

function loadDefaultFallbackAlerts() {
  alerts = [
    {
      messageId: "RQ-8F29A1",
      senderId: "DEVICE-001",
      originDevice: "Phone A (RQ-8F29)",
      lastRelayDevice: "Phone B (RQ-204)",
      timestamp: Date.now() - 1000 * 60 * 12,
      latitude: 28.6210,
      longitude: 77.2150,
      locationAccuracy: 4,
      peopleCount: "3",
      hazardType: "Flash Flood / Rising Water",
      medicalNeeds: "Hypothermia, First Aid",
      hopCount: 2,
      status: "ACTIVE",
      assignedUnit: null,
      notes: "Victims trapped on elevated terrace.",
      senderName: "Rohan Verma",
      senderPhone: "+91 98100 12345",
      timeline: [
        { time: "12m ago", text: "Distress beacon initiated via Nearby P2P mesh", type: "alert" },
        { time: "9m ago", text: "Relayed by Phone B (RQ-204) [Hop 1]", type: "alert" }
      ]
    },
    {
      messageId: "RQ-7A12BC",
      senderId: "DEVICE-004",
      originDevice: "Phone D (RQ-7A12)",
      lastRelayDevice: "Phone D (RQ-7A12)",
      timestamp: Date.now() - 1000 * 60 * 25,
      latitude: 28.6080,
      longitude: 77.2010,
      locationAccuracy: 3,
      peopleCount: "1",
      hazardType: "Building Collapse",
      medicalNeeds: "Leg Fracture, Trauma Care",
      hopCount: 0,
      status: "IN_PROGRESS",
      assignedUnit: "NDRF Battalion 8 (Quick Response)",
      notes: "Acoustic debris search team on site.",
      senderName: "Sunita Rao",
      senderPhone: "+91 98111 54321",
      timeline: [
        { time: "25m ago", text: "Direct P2P distress received", type: "alert" },
        { time: "18m ago", text: "Dispatched: NDRF Battalion 8 (Quick Response)", type: "dispatch" }
      ]
    },
    {
      messageId: "RQ-2C90A4",
      senderId: "DEVICE-019",
      originDevice: "Phone M (RQ-2C90)",
      lastRelayDevice: "Phone P (RQ-1102)",
      timestamp: Date.now() - 1000 * 60 * 55,
      latitude: 28.6280,
      longitude: 77.2250,
      locationAccuracy: 5,
      peopleCount: "2",
      hazardType: "Waterlogged Underpass",
      medicalNeeds: "None reported",
      hopCount: 3,
      status: "RESOLVED",
      assignedUnit: "Civil Defense Volunteer Brigade",
      notes: "Both victims evacuated safely to Relief Camp 4.",
      senderName: "Pooja Sharma",
      senderPhone: "+91 98765 43210",
      timeline: [
        { time: "55m ago", text: "Received via 3 mesh hops", type: "alert" },
        { time: "45m ago", text: "Dispatched: Civil Defense Volunteer Brigade", type: "dispatch" },
        { time: "15m ago", text: "Rescue complete: Victims safely evacuated", type: "resolve" }
      ]
    }
  ];
  renderUI();
}

function renderUI() {
  updateCounts();
  renderAlertList();
  syncMapMarkers();
}

function updateCounts() {
  const active = alerts.filter(a => a.status === "ACTIVE").length;
  const progress = alerts.filter(a => a.status === "IN_PROGRESS").length;
  const resolved = alerts.filter(a => a.status === "RESOLVED").length;

  document.getElementById("stat-active").textContent = `${active} Active`;
  document.getElementById("stat-progress").textContent = `${progress} Dispatched`;
  document.getElementById("stat-resolved").textContent = `${resolved} Rescued`;

  document.getElementById("count-all").textContent = alerts.length;
  document.getElementById("count-active").textContent = active;
  document.getElementById("count-progress").textContent = progress;
  document.getElementById("count-resolved").textContent = resolved;
}

function renderAlertList() {
  const container = document.getElementById("incident-container");
  
  let filtered = alerts;
  if (currentFilter !== "ALL") {
    filtered = filtered.filter(a => a.status === currentFilter);
  }

  if (searchQuery) {
    filtered = filtered.filter(a => 
      a.messageId.toLowerCase().includes(searchQuery) ||
      a.hazardType.toLowerCase().includes(searchQuery) ||
      a.senderName.toLowerCase().includes(searchQuery) ||
      (a.assignedUnit && a.assignedUnit.toLowerCase().includes(searchQuery))
    );
  }

  if (filtered.length === 0) {
    container.innerHTML = `
      <div style="color:var(--text-muted); font-size:12px; text-align:center; padding:40px 10px;">
        No incidents in this view.
      </div>
    `;
    return;
  }

  container.innerHTML = filtered.map(a => {
    const isSel = a.messageId === selectedAlertId ? "active" : "";
    const badge = getBadgeHTML(a.status);
    const timeAgo = formatTimeAgo(a.timestamp);

    return `
      <div class="incident-card ${isSel}" onclick="selectAlert('${a.messageId}')">
        <div class="card-top">
          <span class="card-id">${a.messageId}</span>
          <div style="display:flex; align-items:center; gap:6px;">
            ${badge}
            ${a.status === "RESOLVED" ? `<button class="btn btn-secondary" style="padding:1px 6px; font-size:10px; border-color:rgba(239,68,68,0.4); color:#f87171;" onclick="event.stopPropagation(); dismissAlert('${a.messageId}')" title="Remove completed request from front panel">✕ Remove</button>` : ''}
          </div>
        </div>
        <div class="card-hazard">${a.hazardType} (${a.peopleCount} ${a.peopleCount === "1" ? "person" : "people"})</div>
        <div class="card-meta">
          <span>${a.hopCount === 0 ? "Direct P2P" : a.hopCount + " hops"} • ${timeAgo}</span>
          <span>${a.senderName}</span>
        </div>
        ${a.assignedUnit ? `<div class="card-dispatch">Assigned: ${a.assignedUnit}</div>` : ''}
      </div>
    `;
  }).join("");
}

function selectAlert(messageId) {
  selectedAlertId = messageId;
  renderAlertList();

  const alert = alerts.find(a => a.messageId === messageId);
  if (!alert) return;

  renderDrawer(alert);

  // Open drawer smoothly
  const drawer = document.getElementById("inspector-drawer");
  drawer.classList.add("open");

  // Pan to map
  if (markers[messageId]) {
    map.panTo([alert.latitude, alert.longitude], { animate: true, duration: 0.5 });
    markers[messageId].openPopup();
  }
}

function renderDrawer(alert) {
  document.getElementById("drawer-id").textContent = alert.messageId;
  document.getElementById("drawer-time").textContent = `Reported ${formatTimeAgo(alert.timestamp)} • ${alert.originDevice}`;

  // Action Banner
  const actionBanner = document.getElementById("drawer-action-banner");
  if (alert.status === "ACTIVE") {
    actionBanner.innerHTML = `
      <button class="btn-full btn-dispatch-action" onclick="openDispatchModal('${alert.messageId}')">
        🚒 Deploy Rescue Team
      </button>
    `;
  } else if (alert.status === "IN_PROGRESS") {
    actionBanner.innerHTML = `
      <div style="font-size:12px; color:var(--amber); font-weight:700; margin-bottom:4px;">
        UNIT DEPLOYED: ${alert.assignedUnit}
      </div>
      <div style="font-size:11px; color:var(--text-muted); margin-bottom:8px;">
        ${alert.notes || 'In route to coordinates.'}
      </div>
      <div style="display:flex; gap:8px;">
        <button class="btn-full btn-resolve-action" style="flex:2;" onclick="markResolved('${alert.messageId}')">
          ✓ Mark as Rescued / Solved
        </button>
        <button class="btn btn-secondary" style="flex:1;" onclick="openDispatchModal('${alert.messageId}')">
          Reassign
        </button>
      </div>
    `;
  } else if (alert.status === "RESOLVED") {
    actionBanner.innerHTML = `
      <div style="font-size:12px; color:var(--green); font-weight:700; margin-bottom:6px;">
        ✓ Incident Successfully Resolved
      </div>
      <div style="display:flex; gap:8px;">
        <button class="btn-full btn-reopen-action" style="flex:1;" onclick="reopenAlert('${alert.messageId}')">
          ↺ Reopen
        </button>
        <button class="btn btn-secondary" style="flex:1.2; justify-content:center; border-color:rgba(239,68,68,0.5); color:#ef4444; font-weight:700;" onclick="dismissAlert('${alert.messageId}')">
          ✕ Remove from Panel
        </button>
      </div>
    `;
  }

  // Key Facts
  document.getElementById("fact-hazard").textContent = alert.hazardType;
  document.getElementById("fact-people").textContent = `${alert.peopleCount} Person(s)`;
  document.getElementById("fact-medical").textContent = alert.medicalNeeds || "None reported";
  document.getElementById("fact-hops").textContent = `${alert.hopCount} hop(s) via ${alert.lastRelayDevice}`;
  document.getElementById("fact-coords").textContent = `${alert.latitude.toFixed(4)}° N, ${alert.longitude.toFixed(4)}° E (±${alert.locationAccuracy}m)`;
  document.getElementById("fact-reporter").textContent = `${alert.senderName} (${alert.senderPhone || 'In field'})`;

  // External Map link
  document.getElementById("drawer-maps-link").href = `https://www.google.com/maps/search/?api=1&query=${alert.latitude},${alert.longitude}`;

  // Timeline
  const timelineWrap = document.getElementById("drawer-timeline");
  timelineWrap.innerHTML = (alert.timeline || []).map(t => {
    let tClass = "t-alert";
    if (t.type === "dispatch") tClass = "t-dispatch";
    if (t.type === "resolve") tClass = "t-resolve";

    return `
      <div class="timeline-item ${tClass}">
        <span class="timeline-time">${t.time}</span>
        <span class="timeline-text">${t.text}</span>
      </div>
    `;
  }).join("");
}

function openDispatchModal(messageId) {
  document.getElementById("dispatch-modal").classList.add("open");
}

function markResolved(messageId) {
  fetch(`/api/alerts/${messageId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status: "RESOLVED" })
  }).then(() => fetchAlerts()).catch(() => {
    const target = alerts.find(a => a.messageId === messageId);
    if (target) {
      target.status = "RESOLVED";
      target.timeline.push({ time: "Just now", text: "Marked as RESOLVED", type: "resolve" });
      renderUI();
      selectAlert(messageId);
    }
  });
}

function reopenAlert(messageId) {
  fetch(`/api/alerts/${messageId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status: "ACTIVE" })
  }).then(() => fetchAlerts()).catch(() => {
    const target = alerts.find(a => a.messageId === messageId);
    if (target) {
      target.status = "ACTIVE";
      target.timeline.push({ time: "Just now", text: "Incident reopened", type: "alert" });
      renderUI();
      selectAlert(messageId);
    }
  });
}

function dismissAlert(messageId) {
  fetch(`/api/alerts/${messageId}`, { method: 'DELETE' })
    .then(r => r.json())
    .then(() => {
      if (markers[messageId]) {
        map.removeLayer(markers[messageId]);
        delete markers[messageId];
      }
      if (selectedAlertId === messageId) {
        document.getElementById("inspector-drawer").classList.remove("open");
        selectedAlertId = null;
      }
      fetchAlerts();
    })
    .catch(() => {
      alerts = alerts.filter(a => a.messageId !== messageId);
      if (markers[messageId]) {
        map.removeLayer(markers[messageId]);
        delete markers[messageId];
      }
      if (selectedAlertId === messageId) {
        document.getElementById("inspector-drawer").classList.remove("open");
        selectedAlertId = null;
      }
      renderUI();
    });
}

function syncMapMarkers() {
  alerts.forEach(a => {
    const marker = markers[a.messageId];
    const icon = getMarkerIcon(a.status);

    if (!marker) {
      const newMarker = L.marker([a.latitude, a.longitude], { icon: icon }).addTo(map);
      newMarker.bindPopup(`
        <div style="font-family:sans-serif; font-size:12px; color:#111;">
          <b>${a.messageId}</b> • ${a.status}<br/>
          ${a.hazardType} (${a.peopleCount} people)<br/>
          ${a.hopCount} hops via ${a.lastRelayDevice}
        </div>
      `);
      newMarker.on('click', () => selectAlert(a.messageId));
      markers[a.messageId] = newMarker;
    } else {
      marker.setIcon(icon);
      marker.setLatLng([a.latitude, a.longitude]);
    }

    // Filter visibility
    if (currentFilter === "ALL" || a.status === currentFilter) {
      if (!map.hasLayer(markers[a.messageId])) markers[a.messageId].addTo(map);
    } else {
      if (map.hasLayer(markers[a.messageId])) map.removeLayer(markers[a.messageId]);
    }
  });
}

function getMarkerIcon(status) {
  let color = "#ef4444";
  let pulse = true;

  if (status === "IN_PROGRESS") {
    color = "#f59e0b";
    pulse = false;
  } else if (status === "RESOLVED") {
    color = "#10b981";
    pulse = false;
  }

  const pulseRing = pulse
    ? `<div style="position:absolute; top:-4px; left:-4px; width:24px; height:24px; border-radius:50%; border:2px solid ${color}; opacity:0.8; animation:pulse 1.8s infinite;"></div>`
    : '';

  return L.divIcon({
    className: 'tactical-pin',
    html: `
      <div style="position:relative; width:16px; height:16px;">
        ${pulseRing}
        <div style="background-color:${color}; width:16px; height:16px; border-radius:50%; border:2px solid #ffffff; box-shadow:0 0 8px ${color};"></div>
      </div>
    `,
    iconSize: [16, 16],
    iconAnchor: [8, 8]
  });
}

function getBadgeHTML(status) {
  if (status === "ACTIVE") return `<span class="status-badge active-badge">● Active</span>`;
  if (status === "IN_PROGRESS") return `<span class="status-badge progress-badge">● Dispatched</span>`;
  if (status === "RESOLVED") return `<span class="status-badge resolved-badge">● Rescued</span>`;
  return `<span class="status-badge">${status}</span>`;
}

function formatTimeAgo(timestamp) {
  const diffSec = Math.floor((Date.now() - timestamp) / 1000);
  if (diffSec < 60) return "Just now";
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  return `${diffHr}h ago`;
}

function exportCSV() {
  const headers = ["Alert_ID", "Status", "Timestamp", "Latitude", "Longitude", "Hazard", "Casualties", "Medical_Priority", "Hops", "Origin_Device", "Relay_Device", "Assigned_Unit", "Reporter"];
  const rows = alerts.map(a => [
    a.messageId,
    a.status,
    new Date(a.timestamp).toISOString(),
    a.latitude,
    a.longitude,
    `"${a.hazardType}"`,
    a.peopleCount,
    `"${a.medicalNeeds || ''}"`,
    a.hopCount,
    a.originDevice,
    a.lastRelayDevice,
    `"${a.assignedUnit || 'Unassigned'}"`,
    `"${a.senderName}"`
  ]);

  const csv = "data:text/csv;charset=utf-8," + [headers.join(","), ...rows.map(e => e.join(","))].join("\n");
  const link = document.createElement("a");
  link.setAttribute("href", encodeURI(csv));
  link.setAttribute("download", `ResQMesh_Incident_Log_${new Date().toISOString().slice(0,10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
