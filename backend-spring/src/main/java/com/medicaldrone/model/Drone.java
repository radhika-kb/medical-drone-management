package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "airborne_units")
public class Drone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drone_code", nullable = false, unique = true)
    private String droneCode;

    @Column(nullable = false)
    private String model = "Hexacopter-V2";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id")
    private DroneZone zone;

    @Column(name = "battery_pct", nullable = false)
    private Double batteryPct = 100.0;

    @Column(name = "max_payload_kg", nullable = false)
    private Double maxPayloadKg = 5.0;

    @Column(name = "max_range_km", nullable = false)
    private Double maxRangeKm = 45.0;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private String status = "AVAILABLE"; // AVAILABLE, ASSIGNED, IN_TRANSIT, CHARGING, MAINTENANCE, OFFLINE

    @Version
    private Long version;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Drone() {}

    public Drone(String droneCode, String model, DroneZone zone, Double batteryPct, Double maxPayloadKg, Double maxRangeKm, Double latitude, Double longitude) {
        this.droneCode = droneCode;
        this.model = model;
        this.zone = zone;
        this.batteryPct = batteryPct;
        this.maxPayloadKg = maxPayloadKg;
        this.maxRangeKm = maxRangeKm;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = "AVAILABLE";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDroneCode() { return droneCode; }
    public void setDroneCode(String droneCode) { this.droneCode = droneCode; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public DroneZone getZone() { return zone; }
    public void setZone(DroneZone zone) { this.zone = zone; }

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

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
