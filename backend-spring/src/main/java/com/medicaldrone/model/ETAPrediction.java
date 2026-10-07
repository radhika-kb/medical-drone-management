package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eta_predictions")
public class ETAPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "predicted_minutes", nullable = false)
    private Double predictedMinutes;

    @Column(name = "model_name")
    private String modelName = "XGBoostETARegressor";

    @Column(name = "model_version")
    private String modelVersion = "1.0.0";

    @Column(name = "features_snapshot", columnDefinition = "TEXT")
    private String featuresSnapshot;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public ETAPrediction() {}

    public ETAPrediction(Mission mission, Double predictedMinutes, String modelName, String modelVersion, String featuresSnapshot) {
        this.mission = mission;
        this.predictedMinutes = predictedMinutes;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.featuresSnapshot = featuresSnapshot;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Mission getMission() { return mission; }
    public void setMission(Mission mission) { this.mission = mission; }

    public Double getPredictedMinutes() { return predictedMinutes; }
    public void setPredictedMinutes(Double predictedMinutes) { this.predictedMinutes = predictedMinutes; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getFeaturesSnapshot() { return featuresSnapshot; }
    public void setFeaturesSnapshot(String featuresSnapshot) { this.featuresSnapshot = featuresSnapshot; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
