package com.drone.model;

public class NoFlyNotification {
    private String id;
    private String title;
    private String message;
    private String severity;
    private NoFlyZone zone;
    private long timestamp;
    private boolean acknowledged;

    public NoFlyNotification() {}

    public NoFlyNotification(String id, String title, String message, String severity, NoFlyZone zone) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.severity = severity;
        this.zone = zone;
        this.timestamp = System.currentTimeMillis();
        this.acknowledged = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public NoFlyZone getZone() { return zone; }
    public void setZone(NoFlyZone zone) { this.zone = zone; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
}
