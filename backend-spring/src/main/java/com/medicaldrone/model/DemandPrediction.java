package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "demand_predictions")
public class DemandPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medical_item_id", nullable = false)
    private MedicalItem medicalItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id", nullable = false)
    private DroneZone zone;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hospital_id")
    private MedicalCenter hospital;

    @Column(name = "prediction_period", nullable = false)
    private String predictionPeriod; // NEXT_24H, NEXT_7D, NEXT_30D

    @Column(name = "predicted_quantity", nullable = false)
    private Double predictedQuantity;

    @Column(name = "model_name")
    private String modelName = "RandomForestDemandRegressor";

    @Column(name = "model_version")
    private String modelVersion = "1.0.0";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public DemandPrediction() {}

    public DemandPrediction(MedicalItem medicalItem, DroneZone zone, String predictionPeriod, Double predictedQuantity, String modelName, String modelVersion) {
        this.medicalItem = medicalItem;
        this.zone = zone;
        this.predictionPeriod = predictionPeriod;
        this.predictedQuantity = predictedQuantity;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MedicalItem getMedicalItem() { return medicalItem; }
    public void setMedicalItem(MedicalItem medicalItem) { this.medicalItem = medicalItem; }

    public DroneZone getZone() { return zone; }
    public void setZone(DroneZone zone) { this.zone = zone; }

    public MedicalCenter getHospital() { return hospital; }
    public void setHospital(MedicalCenter hospital) { this.hospital = hospital; }

    public String getPredictionPeriod() { return predictionPeriod; }
    public void setPredictionPeriod(String predictionPeriod) { this.predictionPeriod = predictionPeriod; }

    public Double getPredictedQuantity() { return predictedQuantity; }
    public void setPredictedQuantity(Double predictedQuantity) { this.predictedQuantity = predictedQuantity; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
