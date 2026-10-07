package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(nullable = false)
    private String algorithm = "A*-Airspace";

    @Column(name = "total_distance_km", nullable = false)
    private Double totalDistanceKm;

    @Column(name = "baseline_eta_minutes")
    private Double baselineEtaMinutes;

    @Column(name = "ml_eta_minutes")
    private Double mlEtaMinutes;

    @Column(name = "final_eta_minutes")
    private Double finalEtaMinutes;

    @Column(name = "eta_minutes", nullable = false)
    private Double etaMinutes;

    @Column(name = "battery_required_pct", nullable = false)
    private Double batteryRequiredPct;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<RouteWaypoint> waypoints = new ArrayList<>();

    public Route() {}

    public Route(Mission mission, String algorithm, Double totalDistanceKm, Double etaMinutes, Double batteryRequiredPct) {
        this.mission = mission;
        this.algorithm = algorithm;
        this.totalDistanceKm = totalDistanceKm;
        this.baselineEtaMinutes = etaMinutes;
        this.finalEtaMinutes = etaMinutes;
        this.etaMinutes = etaMinutes;
        this.batteryRequiredPct = batteryRequiredPct;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Mission getMission() { return mission; }
    public void setMission(Mission mission) { this.mission = mission; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public Double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(Double totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }

    public Double getBaselineEtaMinutes() { return baselineEtaMinutes; }
    public void setBaselineEtaMinutes(Double baselineEtaMinutes) { this.baselineEtaMinutes = baselineEtaMinutes; }

    public Double getMlEtaMinutes() { return mlEtaMinutes; }
    public void setMlEtaMinutes(Double mlEtaMinutes) { this.mlEtaMinutes = mlEtaMinutes; }

    public Double getFinalEtaMinutes() { return finalEtaMinutes; }
    public void setFinalEtaMinutes(Double finalEtaMinutes) { this.finalEtaMinutes = finalEtaMinutes; }

    public Double getEtaMinutes() { return etaMinutes; }
    public void setEtaMinutes(Double etaMinutes) { this.etaMinutes = etaMinutes; }

    public Double getBatteryRequiredPct() { return batteryRequiredPct; }
    public void setBatteryRequiredPct(Double batteryRequiredPct) { this.batteryRequiredPct = batteryRequiredPct; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<RouteWaypoint> getWaypoints() { return waypoints; }
    public void setWaypoints(List<RouteWaypoint> waypoints) { this.waypoints = waypoints; }

    public void addWaypoint(RouteWaypoint wp) {
        waypoints.add(wp);
        wp.setRoute(this);
    }
}
