package com.drone.model;

import java.util.List;

public class RerouteSuggestion {
    private String id;
    private String description;
    private List<Waypoint> suggestedWaypoints;
    private double savedDistance;
    private double savedTime;
    private double riskReduction;
    private String reason;
    private long createdAt;

    public RerouteSuggestion() {}

    public RerouteSuggestion(String description, List<Waypoint> suggestedWaypoints,
                              double savedDistance, double savedTime, double riskReduction, String reason) {
        this.id = "reroute-" + System.currentTimeMillis();
        this.description = description;
        this.suggestedWaypoints = suggestedWaypoints;
        this.savedDistance = savedDistance;
        this.savedTime = savedTime;
        this.riskReduction = riskReduction;
        this.reason = reason;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Waypoint> getSuggestedWaypoints() { return suggestedWaypoints; }
    public void setSuggestedWaypoints(List<Waypoint> suggestedWaypoints) { this.suggestedWaypoints = suggestedWaypoints; }
    public double getSavedDistance() { return savedDistance; }
    public void setSavedDistance(double savedDistance) { this.savedDistance = savedDistance; }
    public double getSavedTime() { return savedTime; }
    public void setSavedTime(double savedTime) { this.savedTime = savedTime; }
    public double getRiskReduction() { return riskReduction; }
    public void setRiskReduction(double riskReduction) { this.riskReduction = riskReduction; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
