import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type {
  Waypoint,
  NoFlyZone,
  TerrainPoint,
  FlightPlan,
  DroneConfig,
  NoFlyNotification,
  RiskAssessment,
  RerouteSuggestion,
} from '../types';
import {
  aStarPathfind,
  rrtPathfind,
  smoothPath,
  calculateFlightStats,
  checkTerrainCollision,
  exportKML,
  mockNoFlyZones,
  mockTerrainData,
  haversine,
} from '../utils/pathfinding';

const API_BASE = 'http://localhost:8080/api';

export const useDroneStore = defineStore('drone', () => {
  const waypoints = ref<Waypoint[]>([]);
  const noFlyZones = ref<NoFlyZone[]>([]);
  const terrainData = ref<TerrainPoint[]>([]);
  const currentPlan = ref<FlightPlan | null>(null);
  const selectedAlgorithm = ref<'astar' | 'rrt'>('astar');
  const isSimulating = ref(false);
  const simProgress = ref(0);
  const mapCenter = ref<[number, number]>([39.9, 116.4]);

  const notifications = ref<NoFlyNotification[]>([]);
  const riskAssessment = ref<RiskAssessment | null>(null);
  const rerouteSuggestions = ref<RerouteSuggestion[]>([]);
  const selectedSuggestion = ref<RerouteSuggestion | null>(null);
  const highlightedZoneIds = ref<Set<string>>(new Set());
  const suggestedRoutePreview = ref<Waypoint[] | null>(null);

  let notificationPollInterval: ReturnType<typeof setInterval> | null = null;

  const droneConfig = ref<DroneConfig>({
    maxAltitude: 500,
    maxSpeed: 20,
    batteryCapacity: 5000,
    consumptionRate: 100,
    safeDistance: 30,
  });

  // ─── Actions ──────────────────────────────────────────────────────────────
  function addWaypoint(
    lat: number,
    lng: number,
    altitude = 100,
    speed = 10,
    action: Waypoint['action'] = 'none'
  ) {
    const id = `wp-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
    waypoints.value.push({ id, lat, lng, altitude, speed, action });
  }

  function removeWaypoint(id: string) {
    waypoints.value = waypoints.value.filter((w) => w.id !== id);
  }

  function updateWaypoint(id: string, updates: Partial<Waypoint>) {
    const wp = waypoints.value.find((w) => w.id === id);
    if (wp) Object.assign(wp, updates);
  }

  function planRoute(start: [number, number], goal: [number, number]) {
    const bounds = { minLat: 39.85, maxLat: 39.95, minLng: 116.35, maxLng: 116.45 };
    let raw: Waypoint[];
    if (selectedAlgorithm.value === 'astar') {
      raw = aStarPathfind(start, goal, 30, noFlyZones.value, bounds);
    } else {
      raw = rrtPathfind(start, goal, noFlyZones.value);
    }
    const smoothed = smoothPath(raw);
    waypoints.value = smoothed;
    updatePlan();
  }

  function clearRoute() {
    waypoints.value = [];
    currentPlan.value = null;
    simProgress.value = 0;
  }

  function updatePlan() {
    const stats = calculateFlightStats(waypoints.value, droneConfig.value);
    currentPlan.value = {
      id: `plan-${Date.now()}`,
      name: 'Flight Plan',
      waypoints: [...waypoints.value],
      totalDistance: stats.totalDistance,
      estimatedTime: stats.estimatedTime,
      batteryUsage: stats.batteryUsage,
    };
  }

  let simInterval: ReturnType<typeof setInterval> | null = null;

  function simulateFlight() {
    if (waypoints.value.length < 2 || isSimulating.value) return;
    isSimulating.value = true;
    simProgress.value = 0;
    simInterval = setInterval(() => {
      simProgress.value += 1;
      if (simProgress.value >= 100) {
        simProgress.value = 100;
        isSimulating.value = false;
        if (simInterval) clearInterval(simInterval);
      }
    }, 50);
  }

  function loadMockData() {
    noFlyZones.value = mockNoFlyZones;
    terrainData.value = mockTerrainData;
  }

  function exportPlan(): string {
    if (!currentPlan.value) return '';
    return exportKML(currentPlan.value);
  }

  // ─── Notification & No-Fly Zone Actions ─────────────────────────────────
  async function fetchNotifications() {
    try {
      const response = await fetch(`${API_BASE}/notifications`);
      const data = await response.json();
      notifications.value = data;

      const unacknowledged = data.filter((n: NoFlyNotification) => !n.acknowledged);
      if (unacknowledged.length > 0) {
        await fetchNoFlyZones();
        await assessCurrentRisk();
        await fetchRerouteSuggestions();
      }
    } catch (e) {
      console.warn('Failed to fetch notifications, using mock mode');
    }
  }

  async function fetchNoFlyZones() {
    try {
      const response = await fetch(`${API_BASE}/noflyzones`);
      const data = await response.json();
      noFlyZones.value = data.map((z: any) => ({
        ...z,
        center: z.center || [z.centerLat, z.centerLng],
      }));
    } catch (e) {
      console.warn('Using mock no-fly zones');
    }
  }

  async function addTemporaryNoFlyZone(zone: Partial<NoFlyZone> & { name: string; lat: number; lng: number; radius: number }) {
    try {
      const response = await fetch(`${API_BASE}/noflyzones/temporary`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(zone),
      });
      const notification = await response.json();
      notifications.value.unshift(notification);
      await fetchNoFlyZones();
      await assessCurrentRisk();
      await fetchRerouteSuggestions();
      highlightedZoneIds.value.add(notification.zone.id);
      setTimeout(() => highlightedZoneIds.value.delete(notification.zone.id), 10000);
      return notification;
    } catch (e) {
      const mockZone: NoFlyZone = {
        id: `temp-${Date.now()}`,
        name: zone.name,
        center: [zone.lat, zone.lng],
        centerLat: zone.lat,
        centerLng: zone.lng,
        radius: zone.radius,
        type: zone.type || 'restricted',
        temporary: true,
        createdAt: Date.now(),
      };
      const mockNotification: NoFlyNotification = {
        id: `notif-${Date.now()}`,
        title: '突发禁飞通知',
        message: `在 ${zone.name} 附近新增临时禁飞区，半径 ${zone.radius} 米，请立即规避！`,
        severity: zone.radius > 4000 ? 'critical' : 'warning',
        zone: mockZone,
        timestamp: Date.now(),
        acknowledged: false,
      };
      noFlyZones.value.push(mockZone);
      notifications.value.unshift(mockNotification);
      await assessCurrentRisk();
      await fetchRerouteSuggestions();
      highlightedZoneIds.value.add(mockZone.id);
      setTimeout(() => highlightedZoneIds.value.delete(mockZone.id), 10000);
      return mockNotification;
    }
  }

  async function acknowledgeNotification(notificationId: string) {
    try {
      await fetch(`${API_BASE}/notifications/${notificationId}/acknowledge`, {
        method: 'POST',
      });
    } catch (e) {}
    const notif = notifications.value.find(n => n.id === notificationId);
    if (notif) notif.acknowledged = true;
  }

  // ─── Risk Assessment ────────────────────────────────────────────────────
  async function assessCurrentRisk() {
    if (waypoints.value.length < 2) {
      riskAssessment.value = null;
      return;
    }

    try {
      const response = await fetch(`${API_BASE}/route/assess-risk`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ waypoints: waypoints.value }),
      });
      riskAssessment.value = await response.json();
    } catch (e) {
      riskAssessment.value = assessRiskLocally(waypoints.value);
    }

    if (riskAssessment.value && riskAssessment.value.overallRisk !== 'safe') {
      const zoneIds = riskAssessment.value.conflictZones.map(z => z.zoneId);
      zoneIds.forEach(id => highlightedZoneIds.value.add(id));
    }
  }

  function assessRiskLocally(wps: Waypoint[]): RiskAssessment {
    const warnings: string[] = [];
    const conflictZones: RiskAssessment['conflictZones'] = [];
    let maxRiskScore = 0;
    let overallRisk: RiskAssessment['overallRisk'] = 'safe';

    for (const zone of noFlyZones.value) {
      const affectedIndices: number[] = [];
      let minDistance = Infinity;
      let insideZone = false;
      const zoneLat = zone.centerLat ?? zone.center[0];
      const zoneLng = zone.centerLng ?? zone.center[1];

      for (let i = 0; i < wps.length; i++) {
        const wp = wps[i];
        const distance = haversine(wp.lat, wp.lng, zoneLat, zoneLng);
        minDistance = Math.min(minDistance, distance);

        if (distance < zone.radius) {
          insideZone = true;
          affectedIndices.push(i);
        } else if (distance < zone.radius * 1.5) {
          affectedIndices.push(i);
        }
      }

      if (affectedIndices.length > 0) {
        let severity: RiskAssessment['conflictZones'][0]['severity'];
        let riskScore: number;

        if (insideZone) {
          severity = 'critical';
          riskScore = 100;
          warnings.push(`航线穿越${zone.name}禁飞区！`);
        } else if (minDistance < zone.radius * 1.2) {
          severity = 'high';
          riskScore = 70;
          warnings.push(`航线距离${zone.name}过近，存在风险！`);
        } else {
          severity = 'medium';
          riskScore = 40;
          warnings.push(`航线靠近${zone.name}，请保持警惕。`);
        }

        maxRiskScore = Math.max(maxRiskScore, riskScore);
        conflictZones.push({
          zoneId: zone.id,
          zoneName: zone.name,
          distance: minDistance,
          severity,
          affectedWaypointIndices: affectedIndices,
        });
      }
    }

    if (maxRiskScore >= 80) overallRisk = 'critical';
    else if (maxRiskScore >= 50) overallRisk = 'warning';
    else if (maxRiskScore >= 20) overallRisk = 'caution';

    return {
      id: `risk-${Date.now()}`,
      overallRisk,
      warnings,
      conflictZones,
      riskScore: maxRiskScore,
      assessedAt: Date.now(),
    };
  }

  // ─── Reroute Suggestions ────────────────────────────────────────────────
  async function fetchRerouteSuggestions() {
    if (waypoints.value.length < 2) {
      rerouteSuggestions.value = [];
      return;
    }

    try {
      const response = await fetch(`${API_BASE}/route/reroute-suggestions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ waypoints: waypoints.value }),
      });
      rerouteSuggestions.value = await response.json();
    } catch (e) {
      rerouteSuggestions.value = generateRerouteSuggestionsLocally(waypoints.value);
    }
  }

  function generateRerouteSuggestionsLocally(wps: Waypoint[]): RerouteSuggestion[] {
    const suggestions: RerouteSuggestion[] = [];
    const currentRisk = assessRiskLocally(wps);

    if (currentRisk.overallRisk === 'safe' || wps.length < 2) return suggestions;

    for (const conflict of currentRisk.conflictZones) {
      if (conflict.severity === 'critical' || conflict.severity === 'high') {
        const suggestion = createRerouteAroundConflict(wps, conflict);
        if (suggestion) suggestions.push(suggestion);
      }
    }

    if (suggestions.length === 0 && currentRisk.conflictZones.length > 0) {
      const first = wps[0];
      const last = wps[wps.length - 1];
      const bounds = { minLat: 39.85, maxLat: 39.95, minLng: 116.35, maxLng: 116.45 };
      const replanned = smoothPath(aStarPathfind([first.lat, first.lng], [last.lat, last.lng], 30, noFlyZones.value, bounds));
      const origDist = calculateFlightStats(wps, droneConfig.value).totalDistance;
      const newStats = calculateFlightStats(replanned, droneConfig.value);
      const newRisk = assessRiskLocally(replanned);

      suggestions.push({
        id: `reroute-${Date.now()}`,
        description: '全局重新规划',
        suggestedWaypoints: replanned,
        savedDistance: Math.max(0, origDist - newStats.totalDistance),
        savedTime: Math.abs(origDist - newStats.totalDistance) / 10,
        riskReduction: currentRisk.riskScore - newRisk.riskScore,
        reason: '原航线存在不可接受的风险，已重新规划安全航线',
        createdAt: Date.now(),
      });
    }

    return suggestions;
  }

  function createRerouteAroundConflict(wps: Waypoint[], conflict: RiskAssessment['conflictZones'][0]): RerouteSuggestion | null {
    if (conflict.affectedWaypointIndices.length === 0) return null;

    const firstAffected = conflict.affectedWaypointIndices[0];
    const lastAffected = conflict.affectedWaypointIndices[conflict.affectedWaypointIndices.length - 1];

    if (firstAffected <= 0 || lastAffected >= wps.length - 1) return null;

    const newPath: Waypoint[] = [];
    for (let i = 0; i < firstAffected; i++) newPath.push(wps[i]);

    const before = wps[firstAffected - 1];
    const after = wps[lastAffected + 1];
    const zone = noFlyZones.value.find(z => z.id === conflict.zoneId);
    if (!zone) return null;

    const zoneLat = zone.centerLat ?? zone.center[0];
    const zoneLng = zone.centerLng ?? zone.center[1];

    const midLat = (before.lat + after.lat) / 2;
    const midLng = (before.lng + after.lng) / 2;

    let latOffset = (midLat - zoneLat) * 0.5;
    let lngOffset = (midLng - zoneLng) * 0.5;

    const safeDist = (zone.radius * 2) / 111000;
    if (Math.abs(latOffset) < safeDist && Math.abs(lngOffset) < safeDist) {
      if (Math.abs(latOffset) > Math.abs(lngOffset)) {
        latOffset = Math.sign(latOffset || 1) * safeDist;
      } else {
        lngOffset = Math.sign(lngOffset || 1) * safeDist;
      }
    }

    newPath.push({
      id: 'wp-detour-1',
      lat: zoneLat + latOffset,
      lng: zoneLng + lngOffset,
      altitude: Math.max(before.altitude, after.altitude),
      speed: Math.min(before.speed, after.speed),
      action: 'none',
    });

    for (let i = lastAffected + 1; i < wps.length; i++) newPath.push(wps[i]);

    const origDist = calculateSegmentDistance(wps, firstAffected - 1, lastAffected + 1);
    const newDist = calculateSegmentDistance(newPath, firstAffected - 1, newPath.length - 1);
    const newRisk = assessRiskLocally(newPath);

    return {
      id: `reroute-${Date.now()}`,
      description: `绕行 ${conflict.zoneName}`,
      suggestedWaypoints: newPath,
      savedDistance: Math.max(0, origDist - newDist),
      savedTime: Math.abs(origDist - newDist) / 10,
      riskReduction: conflict.severity === 'critical' ? 100 : 70,
      reason: `通过绕行${conflict.zoneName}避开冲突区域`,
      createdAt: Date.now(),
    };
  }

  function calculateSegmentDistance(wps: Waypoint[], start: number, end: number): number {
    let dist = 0;
    for (let i = start; i < end && i < wps.length - 1; i++) {
      dist += haversine(wps[i].lat, wps[i].lng, wps[i + 1].lat, wps[i + 1].lng);
    }
    return dist;
  }

  function previewReroute(suggestion: RerouteSuggestion) {
    selectedSuggestion.value = suggestion;
    suggestedRoutePreview.value = suggestion.suggestedWaypoints;
  }

  function applyReroute(suggestion: RerouteSuggestion) {
    waypoints.value = suggestion.suggestedWaypoints;
    updatePlan();
    rerouteSuggestions.value = [];
    selectedSuggestion.value = null;
    suggestedRoutePreview.value = null;
    assessCurrentRisk();
  }

  function cancelReroutePreview() {
    selectedSuggestion.value = null;
    suggestedRoutePreview.value = null;
  }

  // ─── Polling ────────────────────────────────────────────────────────────
  function startNotificationPolling() {
    if (notificationPollInterval) return;
    notificationPollInterval = setInterval(() => {
      fetchNotifications();
    }, 5000);
  }

  function stopNotificationPolling() {
    if (notificationPollInterval) {
      clearInterval(notificationPollInterval);
      notificationPollInterval = null;
    }
  }

  // ─── Computed ─────────────────────────────────────────────────────────────
  const totalDistance = computed(() => {
    if (!currentPlan.value) return 0;
    return currentPlan.value.totalDistance;
  });

  const estimatedTime = computed(() => {
    if (!currentPlan.value) return 0;
    return currentPlan.value.estimatedTime;
  });

  const batteryPercent = computed(() => {
    if (!currentPlan.value) return 0;
    return currentPlan.value.batteryUsage;
  });

  const terrainProfile = computed(() => {
    if (waypoints.value.length < 2) return [];
    return waypoints.value.map((wp) => {
      let nearestElev = 0;
      let minDist = Infinity;
      for (const tp of terrainData.value) {
        const d =
          (tp.lat - wp.lat) ** 2 + (tp.lng - wp.lng) ** 2;
        if (d < minDist) {
          minDist = d;
          nearestElev = tp.elevation;
        }
      }
      return {
        lat: wp.lat,
        lng: wp.lng,
        altitude: wp.altitude,
        terrainElevation: nearestElev,
      };
    });
  });

  const unacknowledgedNotifications = computed(() =>
    notifications.value.filter(n => !n.acknowledged)
  );

  const hasHighRisk = computed(() =>
    riskAssessment.value &&
    (riskAssessment.value.overallRisk === 'critical' ||
      riskAssessment.value.overallRisk === 'warning')
  );

  return {
    waypoints,
    noFlyZones,
    terrainData,
    currentPlan,
    droneConfig,
    selectedAlgorithm,
    isSimulating,
    simProgress,
    mapCenter,
    notifications,
    riskAssessment,
    rerouteSuggestions,
    selectedSuggestion,
    highlightedZoneIds,
    suggestedRoutePreview,
    totalDistance,
    estimatedTime,
    batteryPercent,
    terrainProfile,
    unacknowledgedNotifications,
    hasHighRisk,
    addWaypoint,
    removeWaypoint,
    updateWaypoint,
    planRoute,
    clearRoute,
    simulateFlight,
    loadMockData,
    exportPlan,
    updatePlan,
    fetchNotifications,
    fetchNoFlyZones,
    addTemporaryNoFlyZone,
    acknowledgeNotification,
    assessCurrentRisk,
    fetchRerouteSuggestions,
    previewReroute,
    applyReroute,
    cancelReroutePreview,
    startNotificationPolling,
    stopNotificationPolling,
  };
});
