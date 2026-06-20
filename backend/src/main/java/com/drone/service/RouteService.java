package com.drone.service;

import com.drone.model.NoFlyZone;
import com.drone.model.NoFlyNotification;
import com.drone.model.RiskAssessment;
import com.drone.model.RerouteSuggestion;
import com.drone.model.Waypoint;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RouteService {

    private final List<NoFlyZone> dynamicNoFlyZones = new CopyOnWriteArrayList<>();
    private final List<NoFlyNotification> notifications = new CopyOnWriteArrayList<>();

    // ─── A* Pathfinding (simplified server-side) ────────────────────────────
    public List<Waypoint> planRoute(double startLat, double startLng,
                                     double goalLat, double goalLng,
                                     String algorithm) {
        List<NoFlyZone> allZones = getAllNoFlyZones();
        List<Waypoint> path = new ArrayList<>();

        // Simple grid-based A*
        int gridSize = 20;
        double minLat = Math.min(startLat, goalLat) - 0.02;
        double maxLat = Math.max(startLat, goalLat) + 0.02;
        double minLng = Math.min(startLng, goalLng) - 0.02;
        double maxLng = Math.max(startLng, goalLng) + 0.02;
        double dLat = (maxLat - minLat) / gridSize;
        double dLng = (maxLng - minLng) / gridSize;

        int startRow = (int) ((startLat - minLat) / dLat);
        int startCol = (int) ((startLng - minLng) / dLng);
        int goalRow = (int) ((goalLat - minLat) / dLat);
        int goalCol = (int) ((goalLng - minLng) / dLng);

        // Clamp
        startRow = Math.max(0, Math.min(gridSize - 1, startRow));
        startCol = Math.max(0, Math.min(gridSize - 1, startCol));
        goalRow = Math.max(0, Math.min(gridSize - 1, goalRow));
        goalCol = Math.max(0, Math.min(gridSize - 1, goalCol));

        int[][] g = new int[gridSize][gridSize];
        int[][] parent = new int[gridSize][gridSize];
        for (int[] row : g) Arrays.fill(row, Integer.MAX_VALUE);
        for (int[] row : parent) Arrays.fill(row, -1);

        boolean[][] blocked = new boolean[gridSize][gridSize];
        for (NoFlyZone zone : allZones) {
            for (int r = 0; r < gridSize; r++) {
                for (int c = 0; c < gridSize; c++) {
                    double lat = minLat + r * dLat;
                    double lng = minLng + c * dLng;
                    double dist = haversine(lat, lng, zone.getCenterLat(), zone.getCenterLng());
                    if (dist < zone.getRadius()) blocked[r][c] = true;
                }
            }
        }

        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[2] - b[2]);
        g[startRow][startCol] = 0;
        pq.offer(new int[]{startRow, startCol, heuristic(startRow, startCol, goalRow, goalCol)});

        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{-1,1},{1,-1},{1,1}};

        while (!pq.isEmpty()) {
            int[] curr = pq.poll();
            int cr = curr[0], cc = curr[1];

            if (cr == goalRow && cc == goalCol) break;

            for (int[] d : dirs) {
                int nr = cr + d[0], nc = cc + d[1];
                if (nr < 0 || nr >= gridSize || nc < 0 || nc >= gridSize) continue;
                if (blocked[nr][nc]) continue;

                int cost = (d[0] != 0 && d[1] != 0) ? 14 : 10;
                int newG = g[cr][cc] + cost;
                if (newG < g[nr][nc]) {
                    g[nr][nc] = newG;
                    parent[nr][nc] = cr * gridSize + cc;
                    int h = heuristic(nr, nc, goalRow, goalCol);
                    pq.offer(new int[]{nr, nc, newG + h});
                }
            }
        }

        // Trace path
        List<int[]> rawPath = new ArrayList<>();
        int r = goalRow, c = goalCol;
        while (r != startRow || c != startCol) {
            rawPath.add(0, new int[]{r, c});
            int p = parent[r][c];
            if (p == -1) break;
            r = p / gridSize;
            c = p % gridSize;
        }
        rawPath.add(0, new int[]{startRow, startCol});

        int idx = 0;
        for (int[] p : rawPath) {
            double lat = minLat + p[0] * dLat;
            double lng = minLng + p[1] * dLng;
            path.add(new Waypoint("wp-" + idx++, lat, lng, 100, 10, "none"));
        }

        if (path.isEmpty()) {
            path.add(new Waypoint("wp-0", startLat, startLng, 100, 10, "none"));
            path.add(new Waypoint("wp-1", goalLat, goalLng, 100, 10, "none"));
        }

        return path;
    }

    private int heuristic(int r1, int c1, int r2, int c2) {
        return (int) (Math.sqrt((r1 - r2) * (r1 - r2) + (c1 - c2) * (c1 - c2)) * 10);
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ─── Mock Data (Compatibility) ──────────────────────────────────────────
    public List<Map<String, Object>> getNoFlyZones() {
        List<Map<String, Object>> zones = new ArrayList<>();
        for (NoFlyZone zone : getAllNoFlyZones()) {
            zones.add(Map.of(
                    "id", zone.getId(),
                    "name", zone.getName(),
                    "center", List.of(zone.getCenterLat(), zone.getCenterLng()),
                    "radius", zone.getRadius(),
                    "type", zone.getType(),
                    "temporary", zone.isTemporary(),
                    "createdAt", zone.getCreatedAt()
            ));
        }
        return zones;
    }

    public List<Map<String, Object>> getTerrain() {
        List<Map<String, Object>> terrain = new ArrayList<>();
        double baseLat = 39.85;
        double baseLng = 116.35;
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                double lat = baseLat + i * 0.005;
                double lng = baseLng + j * 0.005;
                double elevation = 50 +
                    30 * Math.sin(i * 0.5) * Math.cos(j * 0.4) +
                    20 * Math.sin(i * 0.3 + j * 0.2) +
                    10 * Math.cos(i * 0.7 - j * 0.5);
                terrain.add(Map.of("lat", lat, "lng", lng, "elevation", elevation));
            }
        }
        return terrain;
    }

    public String exportKML(List<Waypoint> waypoints, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n  <Document>\n");
        sb.append("    <name>").append(name).append("</name>\n");
        sb.append("    <Placemark>\n      <name>Flight Route</name>\n");
        sb.append("      <LineString>\n        <altitudeMode>absolute</altitudeMode>\n");
        sb.append("        <coordinates>\n");
        for (Waypoint w : waypoints) {
            sb.append("          ").append(w.getLng()).append(",").append(w.getLat())
              .append(",").append(w.getAltitude()).append("\n");
        }
        sb.append("        </coordinates>\n      </LineString>\n    </Placemark>\n");
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint w = waypoints.get(i);
            sb.append("    <Placemark>\n      <name>WP").append(i + 1).append("</name>\n");
            sb.append("      <Point><coordinates>").append(w.getLng()).append(",")
              .append(w.getLat()).append(",").append(w.getAltitude())
              .append("</coordinates></Point>\n    </Placemark>\n");
        }
        sb.append("  </Document>\n</kml>");
        return sb.toString();
    }

    // ─── Dynamic No-Fly Zone Management ──────────────────────────────────────
    public List<NoFlyZone> getAllNoFlyZones() {
        List<NoFlyZone> allZones = new ArrayList<>();
        allZones.addAll(getStaticNoFlyZones());
        allZones.addAll(dynamicNoFlyZones);
        return allZones;
    }

    public List<NoFlyZone> getDynamicNoFlyZones() {
        return new ArrayList<>(dynamicNoFlyZones);
    }

    private List<NoFlyZone> getStaticNoFlyZones() {
        List<NoFlyZone> zones = new ArrayList<>();
        zones.add(createZone("nfz-1", "首都国际机场", 40.0799, 116.6031, 8000, "airport", false));
        zones.add(createZone("nfz-2", "南苑军事区", 39.7833, 116.3833, 5000, "military", false));
        zones.add(createZone("nfz-3", "中南海限制区", 39.9139, 116.3741, 3000, "restricted", false));
        return zones;
    }

    private NoFlyZone createZone(String id, String name, double lat, double lng, double radius, String type, boolean temp) {
        NoFlyZone zone = new NoFlyZone(id, name, lat, lng, radius, type);
        zone.setTemporary(temp);
        return zone;
    }

    public NoFlyNotification addTemporaryNoFlyZone(NoFlyZone zone) {
        zone.setTemporary(true);
        zone.setId("temp-" + System.currentTimeMillis());
        zone.setCreatedAt(System.currentTimeMillis());
        dynamicNoFlyZones.add(zone);

        String severity = zone.getRadius() > 4000 ? "critical" : "warning";
        String title = "突发禁飞通知";
        String message = String.format("在 %s 附近新增临时%s，半径 %.0f 米，请立即规避！",
                zone.getName(), getZoneTypeName(zone.getType()), zone.getRadius());

        NoFlyNotification notification = new NoFlyNotification(
                "notif-" + System.currentTimeMillis(),
                title,
                message,
                severity,
                zone
        );
        notifications.add(notification);
        return notification;
    }

    private String getZoneTypeName(String type) {
        return switch (type) {
            case "airport" -> "机场限制区";
            case "military" -> "军事管制区";
            case "restricted" -> "飞行限制区";
            default -> "禁飞区";
        };
    }

    public boolean removeTemporaryNoFlyZone(String zoneId) {
        return dynamicNoFlyZones.removeIf(z -> z.getId().equals(zoneId));
    }

    public List<NoFlyNotification> getNotifications() {
        return new ArrayList<>(notifications);
    }

    public boolean acknowledgeNotification(String notificationId) {
        for (NoFlyNotification n : notifications) {
            if (n.getId().equals(notificationId)) {
                n.setAcknowledged(true);
                return true;
            }
        }
        return false;
    }

    // ─── Conflict Detection ──────────────────────────────────────────────────
    public RiskAssessment assessRisk(List<Waypoint> waypoints) {
        List<String> warnings = new ArrayList<>();
        List<RiskAssessment.ConflictZone> conflictZones = new ArrayList<>();
        double maxRiskScore = 0;
        String overallRisk = "safe";

        List<NoFlyZone> allZones = getAllNoFlyZones();

        for (NoFlyZone zone : allZones) {
            List<Integer> affectedIndices = new ArrayList<>();
            double minDistance = Double.MAX_VALUE;
            boolean insideZone = false;

            for (int i = 0; i < waypoints.size(); i++) {
                Waypoint wp = waypoints.get(i);
                double distance = haversine(wp.getLat(), wp.getLng(), zone.getCenterLat(), zone.getCenterLng());
                minDistance = Math.min(minDistance, distance);

                if (distance < zone.getRadius()) {
                    insideZone = true;
                    affectedIndices.add(i);
                } else if (distance < zone.getRadius() * 1.5) {
                    affectedIndices.add(i);
                }
            }

            if (!affectedIndices.isEmpty()) {
                String severity;
                double riskScore;

                if (insideZone) {
                    severity = "critical";
                    riskScore = 100;
                    warnings.add(String.format("航线穿越%s禁飞区！", zone.getName()));
                } else if (minDistance < zone.getRadius() * 1.2) {
                    severity = "high";
                    riskScore = 70;
                    warnings.add(String.format("航线距离%s过近，存在风险！", zone.getName()));
                } else {
                    severity = "medium";
                    riskScore = 40;
                    warnings.add(String.format("航线靠近%s，请保持警惕。", zone.getName()));
                }

                maxRiskScore = Math.max(maxRiskScore, riskScore);
                conflictZones.add(new RiskAssessment.ConflictZone(
                        zone.getId(),
                        zone.getName(),
                        minDistance,
                        severity,
                        affectedIndices
                ));
            }
        }

        if (maxRiskScore >= 80) overallRisk = "critical";
        else if (maxRiskScore >= 50) overallRisk = "warning";
        else if (maxRiskScore >= 20) overallRisk = "caution";

        return new RiskAssessment(overallRisk, warnings, conflictZones, maxRiskScore);
    }

    // ─── Reroute Suggestion ──────────────────────────────────────────────────
    public List<RerouteSuggestion> generateRerouteSuggestions(List<Waypoint> waypoints) {
        List<RerouteSuggestion> suggestions = new ArrayList<>();
        RiskAssessment currentRisk = assessRisk(waypoints);

        if (currentRisk.getOverallRisk().equals("safe") || waypoints.size() < 2) {
            return suggestions;
        }

        for (RiskAssessment.ConflictZone conflict : currentRisk.getConflictZones()) {
            if (conflict.getSeverity().equals("critical") || conflict.getSeverity().equals("high")) {
                RerouteSuggestion suggestion = createRerouteAroundConflict(waypoints, conflict);
                if (suggestion != null) {
                    suggestions.add(suggestion);
                }
            }
        }

        if (suggestions.isEmpty() && currentRisk.getConflictZones().size() > 0) {
            List<Waypoint> replanned = fullReplan(waypoints);
            if (replanned != null && replanned.size() >= 2) {
                double origDist = calculateTotalDistance(waypoints);
                double newDist = calculateTotalDistance(replanned);
                RiskAssessment newRisk = assessRisk(replanned);

                suggestions.add(new RerouteSuggestion(
                        "全局重新规划",
                        replanned,
                        Math.max(0, origDist - newDist),
                        Math.abs(origDist - newDist) / 10,
                        currentRisk.getRiskScore() - newRisk.getRiskScore(),
                        "原航线存在不可接受的风险，已重新规划安全航线"
                ));
            }
        }

        return suggestions;
    }

    private RerouteSuggestion createRerouteAroundConflict(List<Waypoint> waypoints, RiskAssessment.ConflictZone conflict) {
        if (conflict.getAffectedWaypointIndices().isEmpty()) return null;

        int firstAffected = conflict.getAffectedWaypointIndices().get(0);
        int lastAffected = conflict.getAffectedWaypointIndices().get(conflict.getAffectedWaypointIndices().size() - 1);

        if (firstAffected <= 0 || lastAffected >= waypoints.size() - 1) return null;

        List<Waypoint> newPath = new ArrayList<>();

        for (int i = 0; i < firstAffected; i++) {
            newPath.add(waypoints.get(i));
        }

        Waypoint before = waypoints.get(firstAffected - 1);
        Waypoint after = waypoints.get(lastAffected + 1);

        NoFlyZone zone = findZoneById(conflict.getZoneId());
        if (zone == null) return null;

        double midLat = (before.getLat() + after.getLat()) / 2;
        double midLng = (before.getLng() + after.getLng()) / 2;

        double latOffset = (midLat - zone.getCenterLat()) * 0.5;
        double lngOffset = (midLng - zone.getCenterLng()) * 0.5;

        double safeDist = zone.getRadius() * 2 / 111000;
        if (Math.abs(latOffset) < safeDist && Math.abs(lngOffset) < safeDist) {
            if (Math.abs(latOffset) > Math.abs(lngOffset)) {
                latOffset = Math.signum(latOffset) * safeDist;
            } else {
                lngOffset = Math.signum(lngOffset) * safeDist;
            }
        }

        newPath.add(new Waypoint(
                "wp-detour-1",
                zone.getCenterLat() + latOffset,
                zone.getCenterLng() + lngOffset,
                Math.max(before.getAltitude(), after.getAltitude()),
                Math.min(before.getSpeed(), after.getSpeed()),
                "none"
        ));

        for (int i = lastAffected + 1; i < waypoints.size(); i++) {
            newPath.add(waypoints.get(i));
        }

        double origDist = calculateSegmentDistance(waypoints, firstAffected - 1, lastAffected + 1);
        double newDist = calculateSegmentDistance(newPath, firstAffected - 1, newPath.size() - 1);

        RiskAssessment newRisk = assessRisk(newPath);

        return new RerouteSuggestion(
                "绕行 " + conflict.getZoneName(),
                newPath,
                Math.max(0, origDist - newDist),
                Math.abs(origDist - newDist) / 10,
                conflict.getSeverity().equals("critical") ? 100 : 70,
                String.format("通过绕行%s避开冲突区域", conflict.getZoneName())
        );
    }

    private List<Waypoint> fullReplan(List<Waypoint> waypoints) {
        Waypoint start = waypoints.get(0);
        Waypoint end = waypoints.get(waypoints.size() - 1);
        return planRoute(start.getLat(), start.getLng(), end.getLat(), end.getLng(), "astar");
    }

    private double calculateSegmentDistance(List<Waypoint> waypoints, int start, int end) {
        double distance = 0;
        for (int i = start; i < end && i < waypoints.size() - 1; i++) {
            Waypoint a = waypoints.get(i);
            Waypoint b = waypoints.get(i + 1);
            distance += haversine(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        }
        return distance;
    }

    private double calculateTotalDistance(List<Waypoint> waypoints) {
        return calculateSegmentDistance(waypoints, 0, waypoints.size() - 1);
    }

    private NoFlyZone findZoneById(String zoneId) {
        for (NoFlyZone zone : getAllNoFlyZones()) {
            if (zone.getId().equals(zoneId)) return zone;
        }
        return null;
    }
}
