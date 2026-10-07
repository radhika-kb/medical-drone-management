package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "weather_observations")
public class WeatherObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "observed_at", nullable = false)
    private LocalDateTime observedAt = LocalDateTime.now();

    @Column(name = "wind_speed_kmh")
    private Double windSpeedKmh;

    @Column(name = "visibility_km")
    private Double visibilityKm;

    @Column(name = "rain_mm")
    private Double rainMm;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "`condition`")
    private String condition;

    @Column(name = "flight_condition", nullable = false)
    private String flightCondition = "SAFE"; // SAFE, CAUTION, UNSAFE

    @Column(name = "model_probability")
    private Double modelProbability;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    public WeatherObservation() {}

    public WeatherObservation(Double latitude, Double longitude, Double windSpeedKmh, Double visibilityKm, Double rainMm, Double temperatureC, String condition, String flightCondition) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.observedAt = LocalDateTime.now();
        this.windSpeedKmh = windSpeedKmh;
        this.visibilityKm = visibilityKm;
        this.rainMm = rainMm;
        this.temperatureC = temperatureC;
        this.condition = condition;
        this.flightCondition = flightCondition;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }

    public Double getWindSpeedKmh() { return windSpeedKmh; }
    public void setWindSpeedKmh(Double windSpeedKmh) { this.windSpeedKmh = windSpeedKmh; }

    public Double getVisibilityKm() { return visibilityKm; }
    public void setVisibilityKm(Double visibilityKm) { this.visibilityKm = visibilityKm; }

    public Double getRainMm() { return rainMm; }
    public void setRainMm(Double rainMm) { this.rainMm = rainMm; }

    public Double getTemperatureC() { return temperatureC; }
    public void setTemperatureC(Double temperatureC) { this.temperatureC = temperatureC; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getFlightCondition() { return flightCondition; }
    public void setFlightCondition(String flightCondition) { this.flightCondition = flightCondition; }

    public Double getModelProbability() { return modelProbability; }
    public void setModelProbability(Double modelProbability) { this.modelProbability = modelProbability; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
}
