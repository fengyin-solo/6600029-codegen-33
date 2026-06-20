package com.drone.model;

import java.util.List;

public class RiskAssessment {
    private String id;
    private String overallRisk;
    private List<String> warnings;
    private List<ConflictZone> conflictZones;
    private double riskScore;
    private long assessedAt;

    public static class ConflictZone {
        private String zoneId;
        private String zoneName;
        private double distance;
        private String severity;
        private List<Integer> affectedWaypointIndices;

        public ConflictZone() {}

        public ConflictZone(String zoneId, String zoneName, double distance, String severity, List<Integer> affectedWaypointIndices) {
            this.zoneId = zoneId;
            this.zoneName = zoneName;
            this.distance = distance;
            this.severity = severity;
            this.affectedWaypointIndices = affectedWaypointIndices;
        }

        public String getZoneId() { return zoneId; }
        public void setZoneId(String zoneId) { this.zoneId = zoneId; }
        public String getZoneName() { return zoneName; }
        public void setZoneName(String zoneName) { this.zoneName = zoneName; }
        public double getDistance() { return distance; }
        public void setDistance(double distance) { this.distance = distance; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public List<Integer> getAffectedWaypointIndices() { return affectedWaypointIndices; }
        public void setAffectedWaypointIndices(List<Integer> affectedWaypointIndices) { this.affectedWaypointIndices = affectedWaypointIndices; }
    }

    public RiskAssessment() {}

    public RiskAssessment(String overallRisk, List<String> warnings, List<ConflictZone> conflictZones, double riskScore) {
        this.id = "risk-" + System.currentTimeMillis();
        this.overallRisk = overallRisk;
        this.warnings = warnings;
        this.conflictZones = conflictZones;
        this.riskScore = riskScore;
        this.assessedAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOverallRisk() { return overallRisk; }
    public void setOverallRisk(String overallRisk) { this.overallRisk = overallRisk; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public List<ConflictZone> getConflictZones() { return conflictZones; }
    public void setConflictZones(List<ConflictZone> conflictZones) { this.conflictZones = conflictZones; }
    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
    public long getAssessedAt() { return assessedAt; }
    public void setAssessedAt(long assessedAt) { this.assessedAt = assessedAt; }
}
