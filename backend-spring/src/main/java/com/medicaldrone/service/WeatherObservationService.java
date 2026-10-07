package com.medicaldrone.service;

import com.medicaldrone.model.WeatherObservation;
import com.medicaldrone.repository.WeatherObservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class WeatherObservationService {

    private final WeatherObservationRepository repository;

    public WeatherObservationService(WeatherObservationRepository repository) {
        this.repository = repository;
    }

    /** Persist live observations independently so safety rejection does not roll them back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistDecision(Map<String, Object> weatherDecision) {
        if (weatherDecision == null) return;
        Object rawObservations = weatherDecision.get("observations");
        if (!(rawObservations instanceof List<?> observations)) return;

        for (Object value : observations) {
            if (!(value instanceof Map<?, ?> observation)) continue;
            Double latitude = number(observation.get("latitude"));
            Double longitude = number(observation.get("longitude"));
            if (latitude == null || longitude == null) continue;

            WeatherObservation row = new WeatherObservation(
                    latitude,
                    longitude,
                    number(observation.get("wind_speed_kmh")),
                    number(observation.get("visibility_km")),
                    number(observation.get("precipitation_mm")),
                    number(observation.get("temperature_c")),
                    text(observation.get("condition")),
                    text(observation.get("flight_condition"))
            );
            String observedAt = text(observation.get("observed_at"));
            if (observedAt != null) row.setObservedAt(LocalDateTime.parse(observedAt));
            row.setModelProbability(number(observation.get("confidence")));
            row.setModelVersion(text(observation.get("model_name")));
            repository.save(row);
        }
    }

    private Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
