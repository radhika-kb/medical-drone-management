package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mission_anomalies")
public class MissionAnomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "anomaly_score", nullable = false)
    private Double anomalyScore;

    @Column(name = "anomaly_type", nullable = false)
    private String anomalyType; // DELAY, ALTITUDE_DRIFT, POSITION_DIVERGENCE, BATTERY_DRAIN, TIMEOUT

    @Column(nullable = false)
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt = LocalDateTime.now();

    public MissionAnomaly() {}

    public MissionAnomaly(Mission mission, Double anomalyScore, String anomalyType, String severity, String explanation) {
        this.mission = mission;
        this.anomalyScore = anomalyScore;
        this.anomalyType = anomalyType;
        this.severity = severity;
        this.explanation = explanation;
        this.detectedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Mission getMission() { return mission; }
    public void setMission(Mission mission) { this.mission = mission; }

    public Double getAnomalyScore() { return anomalyScore; }
    public void setAnomalyScore(Double anomalyScore) { this.anomalyScore = anomalyScore; }

    public String getAnomalyType() { return anomalyType; }
    public void setAnomalyType(String anomalyType) { this.anomalyType = anomalyType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
}
