<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, nextTick, onUpdated } from 'vue';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useDroneStore } from '../store/drone';
import type { Waypoint } from '../types';

const store = useDroneStore();
const mapContainer = ref<HTMLElement>();
let map: L.Map | null = null;
let waypointLayer: L.LayerGroup | null = null;
let routeLayer: L.Polyline | null = null;
let suggestedRouteLayer: L.Polyline | null = null;
let zoneLayer: L.LayerGroup | null = null;
let highlightedZoneLayer: L.LayerGroup | null = null;
let droneMarker: L.CircleMarker | null = null;
let dangerWaypointLayer: L.LayerGroup | null = null;

let flashAnimationFrame: number | null = null;
let flashState = 0;

const addMode = ref(false);

function initMap() {
  if (!mapContainer.value || map) return;
  map = L.map(mapContainer.value).setView(store.mapCenter, 12);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap',
    maxZoom: 18,
  }).addTo(map);

  waypointLayer = L.layerGroup().addTo(map);
  zoneLayer = L.layerGroup().addTo(map);
  highlightedZoneLayer = L.layerGroup().addTo(map);
  dangerWaypointLayer = L.layerGroup().addTo(map);

  map.on('click', (e: L.LeafletMouseEvent) => {
    if (addMode.value) {
      store.addWaypoint(e.latlng.lat, e.latlng.lng);
    }
  });
}

function getZoneColor(type: string) {
  return type === 'airport' ? '#ef4444' :
         type === 'military' ? '#f97316' : '#a855f7';
}

function getRiskColor(severity?: string) {
  switch (severity) {
    case 'critical': return '#dc2626';
    case 'high': return '#ea580c';
    case 'medium': return '#d97706';
    default: return '#65a30d';
  }
}

function drawNoFlyZones() {
  if (!zoneLayer) return;
  zoneLayer.clearLayers();
  for (const zone of store.noFlyZones) {
    const color = getZoneColor(zone.type);
    const lat = zone.centerLat ?? zone.center[0];
    const lng = zone.centerLng ?? zone.center[1];
    const circle = L.circle([lat, lng], {
      radius: zone.radius,
      color: color,
      fillColor: color,
      fillOpacity: zone.temporary ? 0.25 : 0.15,
      weight: zone.temporary ? 3 : 2,
      dashArray: zone.temporary ? '10,5' : undefined,
    });
    circle.bindPopup(`
      <div style="min-width:180px">
        <b>${zone.name}</b>${zone.temporary ? ' <span style="color:#ef4444;font-weight:bold">[临时]</span>' : ''}<br>
        Type: ${zone.type}<br>
        Radius: ${zone.radius}m
      </div>
    `);
    circle.addTo(zoneLayer);
  }
}

function drawHighlightedZones() {
  if (!highlightedZoneLayer || !map) return;
  highlightedZoneLayer.clearLayers();

  for (const zoneId of store.highlightedZoneIds) {
    const zone = store.noFlyZones.find(z => z.id === zoneId);
    if (!zone) continue;

    const lat = zone.centerLat ?? zone.center[0];
    const lng = zone.centerLng ?? zone.center[1];
    const color = getZoneColor(zone.type);

    const pulseCircle = L.circle([lat, lng], {
      radius: zone.radius * 1.2,
      color: color,
      fillColor: color,
      fillOpacity: 0.1 + Math.sin(flashState * 0.1) * 0.15,
      weight: 4,
      opacity: 0.8 + Math.sin(flashState * 0.1) * 0.2,
    });
    pulseCircle.addTo(highlightedZoneLayer);

    const innerCircle = L.circle([lat, lng], {
      radius: zone.radius,
      color: '#fef08a',
      fillColor: 'transparent',
      fillOpacity: 0,
      weight: 3,
      opacity: 0.9 + Math.sin(flashState * 0.15) * 0.1,
      dashArray: '5,5',
    });
    innerCircle.addTo(highlightedZoneLayer);
  }

  flashState++;
  if (store.highlightedZoneIds.size > 0) {
    flashAnimationFrame = requestAnimationFrame(drawHighlightedZones);
  } else if (flashAnimationFrame) {
    cancelAnimationFrame(flashAnimationFrame);
    flashAnimationFrame = null;
  }
}

function drawWaypoints() {
  if (!waypointLayer) return;
  waypointLayer.clearLayers();

  const dangerIndices = new Set<number>();
  if (store.riskAssessment) {
    for (const cz of store.riskAssessment.conflictZones) {
      cz.affectedWaypointIndices.forEach(i => dangerIndices.add(i));
    }
  }

  store.waypoints.forEach((wp, idx) => {
    const isDanger = dangerIndices.has(idx);
    const marker = L.circleMarker([wp.lat, wp.lng], {
      radius: isDanger ? 10 : 8,
      color: isDanger ? '#dc2626' : '#3b82f6',
      fillColor: isDanger ? '#ef4444' : '#60a5fa',
      fillOpacity: 0.9,
      weight: isDanger ? 3 : 2,
    });
    marker.bindTooltip(`WP${idx + 1}`, { permanent: true, direction: 'top', className: 'wp-tooltip' });
    marker.bindPopup(`
      <div style="min-width:160px">
        <b>Waypoint ${idx + 1}</b>${isDanger ? ' <span style="color:#ef4444">⚠️</span>' : ''}<br>
        Altitude: ${wp.altitude}m<br>
        Speed: ${wp.speed} m/s<br>
        Action: ${wp.action}<br>
        <button onclick="this.closest('.leaflet-popup').remove()" style="margin-top:4px;color:#ef4444">Remove</button>
      </div>
    `);
    marker.on('dragend', (e: any) => {
      const ll = e.target.getLatLng();
      store.updateWaypoint(wp.id, { lat: ll.lat, lng: ll.lng });
    });
    marker.addTo(waypointLayer!);
  });
}

function drawRoute() {
  if (routeLayer && map) {
    map.removeLayer(routeLayer);
    routeLayer = null;
  }
  if (store.waypoints.length < 2 || !map) return;

  const latlngs = store.waypoints.map((w) => [w.lat, w.lng] as [number, number]);

  let routeColor = '#22c55e';
  let dashArray: string | undefined = undefined;

  if (store.riskAssessment) {
    switch (store.riskAssessment.overallRisk) {
      case 'critical':
        routeColor = '#dc2626';
        dashArray = '10,5';
        break;
      case 'warning':
        routeColor = '#ea580c';
        dashArray = '8,4';
        break;
      case 'caution':
        routeColor = '#d97706';
        break;
    }
  } else {
    let hasDanger = false;
    for (const wp of store.waypoints) {
      for (const zone of store.noFlyZones) {
        const lat = zone.centerLat ?? zone.center[0];
        const lng = zone.centerLng ?? zone.center[1];
        const d = Math.sqrt((wp.lat - lat) ** 2 + (wp.lng - lng) ** 2) * 111000;
        if (d < zone.radius * 1.5) hasDanger = true;
      }
    }
    if (hasDanger) routeColor = '#ef4444';
  }

  routeLayer = L.polyline(latlngs, {
    color: routeColor,
    weight: 3,
    opacity: 0.8,
    dashArray,
  }).addTo(map);
}

function drawSuggestedRoute() {
  if (suggestedRouteLayer && map) {
    map.removeLayer(suggestedRouteLayer);
    suggestedRouteLayer = null;
  }
  if (!store.suggestedRoutePreview || store.suggestedRoutePreview.length < 2 || !map) return;

  const latlngs = store.suggestedRoutePreview.map((w) => [w.lat, w.lng] as [number, number]);
  suggestedRouteLayer = L.polyline(latlngs, {
    color: '#22c55e',
    weight: 4,
    opacity: 0.7,
    dashArray: '15,10',
  }).addTo(map);

  store.suggestedRoutePreview.forEach((wp, idx) => {
    const marker = L.circleMarker([wp.lat, wp.lng], {
      radius: 6,
      color: '#22c55e',
      fillColor: '#86efac',
      fillOpacity: 0.7,
      weight: 2,
    });
    marker.bindTooltip(`建议 WP${idx + 1}`, { direction: 'top', className: 'wp-tooltip' });
    marker.addTo(waypointLayer!);
  });
}

function drawSimDrone() {
  if (!map || store.waypoints.length < 2) return;
  const progress = store.simProgress / 100;
  const totalWp = store.waypoints.length;
  const segIdx = Math.min(Math.floor(progress * (totalWp - 1)), totalWp - 2);
  const segProgress = (progress * (totalWp - 1)) - segIdx;
  const wp1 = store.waypoints[segIdx];
  const wp2 = store.waypoints[segIdx + 1];
  const lat = wp1.lat + (wp2.lat - wp1.lat) * segProgress;
  const lng = wp1.lng + (wp2.lng - wp1.lng) * segProgress;

  if (droneMarker) {
    droneMarker.setLatLng([lat, lng]);
  } else {
    droneMarker = L.circleMarker([lat, lng], {
      radius: 10,
      color: '#fbbf24',
      fillColor: '#f59e0b',
      fillOpacity: 1,
      weight: 3,
    }).addTo(map);
  }
}

function redrawAll() {
  drawNoFlyZones();
  drawWaypoints();
  drawRoute();
  drawSuggestedRoute();
  if (store.highlightedZoneIds.size > 0) {
    drawHighlightedZones();
  }
}

watch(() => store.waypoints.length, () => {
  drawWaypoints();
  drawRoute();
  store.assessCurrentRisk();
});

watch(() => store.noFlyZones.length, () => {
  redrawAll();
});

watch(() => store.simProgress, drawSimDrone);

watch(() => store.highlightedZoneIds.size, () => {
  if (store.highlightedZoneIds.size > 0) {
    drawHighlightedZones();
  } else if (flashAnimationFrame) {
    cancelAnimationFrame(flashAnimationFrame);
    flashAnimationFrame = null;
    if (highlightedZoneLayer) highlightedZoneLayer.clearLayers();
  }
});

watch(() => store.riskAssessment, () => {
  drawWaypoints();
  drawRoute();
});

watch(() => store.suggestedRoutePreview, () => {
  drawSuggestedRoute();
});

onMounted(() => {
  nextTick(initMap);
});

onUnmounted(() => {
  if (map) {
    map.remove();
    map = null;
  }
  if (flashAnimationFrame) {
    cancelAnimationFrame(flashAnimationFrame);
  }
});

function toggleAddMode() {
  addMode.value = !addMode.value;
}

function handlePlanRoute() {
  if (store.waypoints.length < 2) return;
  const first = store.waypoints[0];
  const last = store.waypoints[store.waypoints.length - 1];
  store.planRoute([first.lat, first.lng], [last.lat, last.lng]);
  setTimeout(() => store.assessCurrentRisk(), 100);
}
</script>

<template>
  <div class="relative w-full h-full">
    <div ref="mapContainer" class="w-full h-full rounded-lg" />
    <div class="absolute top-2 right-2 z-[1000] flex flex-col gap-1">
      <button
        @click="toggleAddMode"
        :class="addMode ? 'bg-blue-600 text-white' : 'bg-gray-800 text-gray-300'"
        class="px-3 py-1 rounded text-xs font-medium shadow hover:opacity-90 transition"
      >
        {{ addMode ? '✦ 添加模式' : '○ 点击添加' }}
      </button>
      <button
        @click="handlePlanRoute"
        class="px-3 py-1 rounded text-xs font-medium bg-green-700 text-white shadow hover:opacity-90 transition"
      >
        规划航线
      </button>
      <button
        @click="store.clearRoute()"
        class="px-3 py-1 rounded text-xs font-medium bg-red-700 text-white shadow hover:opacity-90 transition"
      >
        清除
      </button>
    </div>
  </div>
</template>

<style scoped>
:deep(.wp-tooltip) {
  background: rgba(30, 41, 59, 0.9);
  color: #e2e8f0;
  border: 1px solid #475569;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 4px;
}
</style>
