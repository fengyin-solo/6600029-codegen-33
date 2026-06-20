export interface Waypoint {
  id: string;
  lat: number;
  lng: number;
  altitude: number;   // meters AGL
  speed: number;      // m/s
  action: 'hover' | 'photo' | 'video' | 'none';
}

export interface FlightPlan {
  id: string;
  name: string;
  waypoints: Waypoint[];
  totalDistance: number;
  estimatedTime: number;
  batteryUsage: number;  // percentage
}

export interface NoFlyZone {
  id: string;
  name: string;
  center: [number, number];
  centerLat: number;
  centerLng: number;
  radius: number;  // meters
  type: 'airport' | 'military' | 'restricted';
  temporary?: boolean;
  createdAt?: number;
  expiresAt?: number;
}

export interface TerrainPoint {
  lat: number;
  lng: number;
  elevation: number;
}

export interface DroneConfig {
  maxAltitude: number;
  maxSpeed: number;
  batteryCapacity: number;  // mAh
  consumptionRate: number;  // mAh/min
  safeDistance: number;     // meters from obstacles
}

export interface NoFlyNotification {
  id: string;
  title: string;
  message: string;
  severity: 'critical' | 'warning' | 'info';
  zone: NoFlyZone;
  timestamp: number;
  acknowledged: boolean;
}

export interface ConflictZone {
  zoneId: string;
  zoneName: string;
  distance: number;
  severity: 'critical' | 'high' | 'medium' | 'low';
  affectedWaypointIndices: number[];
}

export interface RiskAssessment {
  id: string;
  overallRisk: 'safe' | 'caution' | 'warning' | 'critical';
  warnings: string[];
  conflictZones: ConflictZone[];
  riskScore: number;
  assessedAt: number;
}

export interface RerouteSuggestion {
  id: string;
  description: string;
  suggestedWaypoints: Waypoint[];
  savedDistance: number;
  savedTime: number;
  riskReduction: number;
  reason: string;
  createdAt: number;
}
