package com.drone.controller;

import com.drone.model.NoFlyNotification;
import com.drone.model.NoFlyZone;
import com.drone.model.RerouteSuggestion;
import com.drone.model.RiskAssessment;
import com.drone.model.Waypoint;
import com.drone.service.RouteService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/route/plan")
    public List<Waypoint> planRoute(@RequestBody Map<String, Object> request) {
        double startLat = ((Number) request.get("startLat")).doubleValue();
        double startLng = ((Number) request.get("startLng")).doubleValue();
        double goalLat = ((Number) request.get("goalLat")).doubleValue();
        double goalLng = ((Number) request.get("goalLng")).doubleValue();
        String algorithm = (String) request.getOrDefault("algorithm", "astar");

        return routeService.planRoute(startLat, startLng, goalLat, goalLng, algorithm);
    }

    @GetMapping("/noflyzones")
    public List<Map<String, Object>> getNoFlyZones() {
        return routeService.getNoFlyZones();
    }

    @GetMapping("/terrain")
    public List<Map<String, Object>> getTerrain() {
        return routeService.getTerrain();
    }

    @PostMapping("/route/export")
    public Map<String, String> exportRoute(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wpData = (List<Map<String, Object>>) request.get("waypoints");
        String name = (String) request.getOrDefault("name", "Flight Plan");

        List<Waypoint> waypoints = new java.util.ArrayList<>();
        for (Map<String, Object> w : wpData) {
            waypoints.add(new Waypoint(
                (String) w.get("id"),
                ((Number) w.get("lat")).doubleValue(),
                ((Number) w.get("lng")).doubleValue(),
                ((Number) w.get("altitude")).doubleValue(),
                ((Number) w.get("speed")).doubleValue(),
                (String) w.get("action")
            ));
        }

        String kml = routeService.exportKML(waypoints, name);
        return Map.of("kml", kml);
    }

    // ─── Temporary No-Fly Zone APIs ──────────────────────────────────────────
    @PostMapping("/noflyzones/temporary")
    public NoFlyNotification addTemporaryNoFlyZone(@RequestBody Map<String, Object> request) {
        NoFlyZone zone = new NoFlyZone();
        zone.setName((String) request.get("name"));
        zone.setCenterLat(((Number) request.get("lat")).doubleValue());
        zone.setCenterLng(((Number) request.get("lng")).doubleValue());
        zone.setRadius(((Number) request.get("radius")).doubleValue());
        zone.setType((String) request.getOrDefault("type", "restricted"));
        if (request.containsKey("expiresAt")) {
            zone.setExpiresAt(((Number) request.get("expiresAt")).longValue());
        }
        return routeService.addTemporaryNoFlyZone(zone);
    }

    @DeleteMapping("/noflyzones/temporary/{zoneId}")
    public Map<String, Object> removeTemporaryNoFlyZone(@PathVariable String zoneId) {
        boolean removed = routeService.removeTemporaryNoFlyZone(zoneId);
        return Map.of("success", removed);
    }

    @GetMapping("/noflyzones/dynamic")
    public List<NoFlyZone> getDynamicNoFlyZones() {
        return routeService.getDynamicNoFlyZones();
    }

    // ─── Notification APIs ───────────────────────────────────────────────────
    @GetMapping("/notifications")
    public List<NoFlyNotification> getNotifications() {
        return routeService.getNotifications();
    }

    @PostMapping("/notifications/{id}/acknowledge")
    public Map<String, Object> acknowledgeNotification(@PathVariable String id) {
        boolean acknowledged = routeService.acknowledgeNotification(id);
        return Map.of("success", acknowledged);
    }

    // ─── Risk Assessment APIs ────────────────────────────────────────────────
    @PostMapping("/route/assess-risk")
    public RiskAssessment assessRisk(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wpData = (List<Map<String, Object>>) request.get("waypoints");
        List<Waypoint> waypoints = parseWaypoints(wpData);
        return routeService.assessRisk(waypoints);
    }

    // ─── Reroute Suggestion APIs ─────────────────────────────────────────────
    @PostMapping("/route/reroute-suggestions")
    public List<RerouteSuggestion> getRerouteSuggestions(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wpData = (List<Map<String, Object>>) request.get("waypoints");
        List<Waypoint> waypoints = parseWaypoints(wpData);
        return routeService.generateRerouteSuggestions(waypoints);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────
    private List<Waypoint> parseWaypoints(List<Map<String, Object>> wpData) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (Map<String, Object> w : wpData) {
            waypoints.add(new Waypoint(
                    (String) w.get("id"),
                    ((Number) w.get("lat")).doubleValue(),
                    ((Number) w.get("lng")).doubleValue(),
                    ((Number) w.get("altitude")).doubleValue(),
                    ((Number) w.get("speed")).doubleValue(),
                    (String) w.get("action")
            ));
        }
        return waypoints;
    }
}
