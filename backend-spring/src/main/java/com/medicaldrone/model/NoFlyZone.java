package com.medicaldrone.model;

import jakarta.persistence.*;

@Entity
@Table(name = "no_fly_zones")
public class NoFlyZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type = "AIRPORT"; // AIRPORT, MILITARY, VIP_SECURITY

    @Column(name = "center_lat", nullable = false)
    private Double centerLat;

    @Column(name = "center_lng", nullable = false)
    private Double centerLng;

    @Column(name = "radius_km", nullable = false)
    private Double radiusKm;

    @Column(nullable = false)
    private Boolean active = true;

    public NoFlyZone() {}

    public NoFlyZone(String name, String type, Double centerLat, Double centerLng, Double radiusKm) {
        this.name = name;
        this.type = type;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.radiusKm = radiusKm;
        this.active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Double getCenterLat() { return centerLat; }
    public void setCenterLat(Double centerLat) { this.centerLat = centerLat; }

    public Double getCenterLng() { return centerLng; }
    public void setCenterLng(Double centerLng) { this.centerLng = centerLng; }

    public Double getRadiusKm() { return radiusKm; }
    public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
