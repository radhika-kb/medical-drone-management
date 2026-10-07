package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_risk_alerts",
        uniqueConstraints = @UniqueConstraint(name = "uk_risk_hospital_item", columnNames = {"hospital_id", "medical_item_id"}))
public class InventoryRiskAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hospital_id", nullable = false)
    private MedicalCenter hospital;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medical_item_id", nullable = false)
    private MedicalItem medicalItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id", nullable = false)
    private DroneZone zone;

    @Column(name = "prediction_period", nullable = false)
    private String predictionPeriod = "NEXT_24H";

    @Column(name = "predicted_demand", nullable = false)
    private Integer predictedDemand;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;

    @Column(name = "shortage_quantity", nullable = false)
    private Integer shortageQuantity;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "last_evaluated_at", nullable = false)
    private LocalDateTime lastEvaluatedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public Long getId() { return id; }
    public MedicalCenter getHospital() { return hospital; }
    public void setHospital(MedicalCenter hospital) { this.hospital = hospital; }
    public MedicalItem getMedicalItem() { return medicalItem; }
    public void setMedicalItem(MedicalItem medicalItem) { this.medicalItem = medicalItem; }
    public DroneZone getZone() { return zone; }
    public void setZone(DroneZone zone) { this.zone = zone; }
    public String getPredictionPeriod() { return predictionPeriod; }
    public void setPredictionPeriod(String predictionPeriod) { this.predictionPeriod = predictionPeriod; }
    public Integer getPredictedDemand() { return predictedDemand; }
    public void setPredictedDemand(Integer predictedDemand) { this.predictedDemand = predictedDemand; }
    public Integer getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
    public Integer getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public Integer getShortageQuantity() { return shortageQuantity; }
    public void setShortageQuantity(Integer shortageQuantity) { this.shortageQuantity = shortageQuantity; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastEvaluatedAt() { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(LocalDateTime lastEvaluatedAt) { this.lastEvaluatedAt = lastEvaluatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
