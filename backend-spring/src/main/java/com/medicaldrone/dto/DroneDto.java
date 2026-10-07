package com.medicaldrone.dto;

public class DroneDto {
    private Long id;
    private String droneCode;
    private String model;
    private Long zoneId;
    private String zoneName;
    private Double batteryPct;
    private Double maxPayloadKg;
    private Double maxRangeKm;
    private Double latitude;
    private Double longitude;
    private String status;

    public DroneDto() {}

    public DroneDto(Long id, String droneCode, String model, Long zoneId, String zoneName, Double batteryPct, Double maxPayloadKg, Double maxRangeKm, Double latitude, Double longitude, String status) {
        this.id = id;
        this.droneCode = droneCode;
        this.model = model;
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.batteryPct = batteryPct;
        this.maxPayloadKg = maxPayloadKg;
        this.maxRangeKm = maxRangeKm;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDroneCode() { return droneCode; }
    public void setDroneCode(String droneCode) { this.droneCode = droneCode; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public Double getBatteryPct() { return batteryPct; }
    public void setBatteryPct(Double batteryPct) { this.batteryPct = batteryPct; }
    public Double getMaxPayloadKg() { return maxPayloadKg; }
    public void setMaxPayloadKg(Double maxPayloadKg) { this.maxPayloadKg = maxPayloadKg; }
    public Double getMaxRangeKm() { return maxRangeKm; }
    public void setMaxRangeKm(Double maxRangeKm) { this.maxRangeKm = maxRangeKm; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
