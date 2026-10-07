package com.medicaldrone.model;

import jakarta.persistence.*;

@Entity
@Table(name = "zones")
public class DroneZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // NORTH_ZONE, EAST_ZONE, SOUTH_ZONE, WEST_ZONE

    @Column(nullable = false, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hub_id")
    private CentralHub hub;

    @Column(name = "center_lat", nullable = false)
    private Double centerLat;

    @Column(name = "center_lng", nullable = false)
    private Double centerLng;

    @Column(name = "radius_km", nullable = false)
    private Double radiusKm;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    public DroneZone() {}

    public DroneZone(String name, String code, CentralHub hub, Double centerLat, Double centerLng, Double radiusKm, String description) {
        this.name = name;
        this.code = code;
        this.hub = hub;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.radiusKm = radiusKm;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public CentralHub getHub() { return hub; }
    public void setHub(CentralHub hub) { this.hub = hub; }

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
}
