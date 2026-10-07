package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mission_tracking")
public class MissionTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "battery_pct", nullable = false)
    private Double batteryPct;

    @Column(name = "eta_minutes")
    private Double etaMinutes;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public MissionTracking() {}

    public MissionTracking(Mission mission, Double latitude, Double longitude, Double batteryPct, Double etaMinutes, String status) {
        this.mission = mission;
        this.latitude = latitude;
        this.longitude = longitude;
        this.batteryPct = batteryPct;
        this.etaMinutes = etaMinutes;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Mission getMission() { return mission; }
    public void setMission(Mission mission) { this.mission = mission; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getBatteryPct() { return batteryPct; }
    public void setBatteryPct(Double batteryPct) { this.batteryPct = batteryPct; }

    public Double getEtaMinutes() { return etaMinutes; }
    public void setEtaMinutes(Double etaMinutes) { this.etaMinutes = etaMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
