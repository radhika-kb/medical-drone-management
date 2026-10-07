package com.medicaldrone.dto;

public class ZoneDto {
    private Long id;
    private String name;
    private String code;
    private String hubName;
    private Double centerLat;
    private Double centerLng;
    private Double radiusKm;
    private String description;
    private Boolean active;
    private Long droneCount;
    private Long activeMissionCount;

    public ZoneDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getHubName() { return hubName; }
    public void setHubName(String hubName) { this.hubName = hubName; }
    public Double getCenterLat() { return centerLat; }
    public void setCenterLat(Double centerLat) { this.centerLat = centerLat; }
    public Double getCenterLng() { return centerLng; }
    public void setCenterLng(Double centerLng) { this.centerLng = centerLng; }
    public Double getRadiusKm() { return radiusKm; }
    public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Long getDroneCount() { return droneCount; }
    public void setDroneCount(Long droneCount) { this.droneCount = droneCount; }
    public Long getActiveMissionCount() { return activeMissionCount; }
    public void setActiveMissionCount(Long activeMissionCount) { this.activeMissionCount = activeMissionCount; }
}
