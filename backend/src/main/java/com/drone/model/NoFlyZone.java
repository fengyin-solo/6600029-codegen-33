package com.drone.model;

public class NoFlyZone {
    private String id;
    private String name;
    private double centerLat;
    private double centerLng;
    private double radius;
    private String type;
    private long createdAt;
    private boolean isTemporary;
    private Long expiresAt;

    public NoFlyZone() {}

    public NoFlyZone(String id, String name, double centerLat, double centerLng, double radius, String type) {
        this.id = id;
        this.name = name;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.radius = radius;
        this.type = type;
        this.createdAt = System.currentTimeMillis();
        this.isTemporary = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getCenterLat() { return centerLat; }
    public void setCenterLat(double centerLat) { this.centerLat = centerLat; }
    public double getCenterLng() { return centerLng; }
    public void setCenterLng(double centerLng) { this.centerLng = centerLng; }
    public double getRadius() { return radius; }
    public void setRadius(double radius) { this.radius = radius; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public boolean isTemporary() { return isTemporary; }
    public void setTemporary(boolean temporary) { isTemporary = temporary; }
    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }
}
