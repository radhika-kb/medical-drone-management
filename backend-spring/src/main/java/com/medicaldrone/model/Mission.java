package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "missions")
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mission_code", nullable = false, unique = true)
    private String missionCode;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "request_id", nullable = false)
    private MedicalRequest request;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplying_hospital_id")
    private MedicalCenter supplyingHospital;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requesting_hospital_id")
    private MedicalCenter requestingHospital;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "drone_id", nullable = false)
    private Drone drone;

    @Column(nullable = false)
    private String status = "DISPATCHED"; // DISPATCHED, IN_TRANSIT, DELIVERED, DELIVERY_CONFIRMED, ABORTED, FAILED

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "eta_minutes")
    private Double etaMinutes;

    @Column(name = "started_at")
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "hub_decision_reason", columnDefinition = "TEXT")
    private String hubDecisionReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Mission() {}

    public Mission(String missionCode, MedicalRequest request, Drone drone, Double distanceKm, Double etaMinutes) {
        this.missionCode = missionCode;
        this.request = request;
        this.drone = drone;
        this.distanceKm = distanceKm;
        this.etaMinutes = etaMinutes;
        this.status = "DISPATCHED";
        this.startedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMissionCode() { return missionCode; }
    public void setMissionCode(String missionCode) { this.missionCode = missionCode; }

    public MedicalRequest getRequest() { return request; }
    public void setRequest(MedicalRequest request) { this.request = request; }

    public MedicalCenter getSupplyingHospital() { return supplyingHospital; }
    public void setSupplyingHospital(MedicalCenter supplyingHospital) { this.supplyingHospital = supplyingHospital; }

    public MedicalCenter getRequestingHospital() { return requestingHospital; }
    public void setRequestingHospital(MedicalCenter requestingHospital) { this.requestingHospital = requestingHospital; }

    public Drone getDrone() { return drone; }
    public void setDrone(Drone drone) { this.drone = drone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public Double getEtaMinutes() { return etaMinutes; }
    public void setEtaMinutes(Double etaMinutes) { this.etaMinutes = etaMinutes; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getHubDecisionReason() { return hubDecisionReason; }
    public void setHubDecisionReason(String hubDecisionReason) { this.hubDecisionReason = hubDecisionReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
