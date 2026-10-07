package com.medicaldrone.service;

import com.medicaldrone.model.WeatherObservation;
import com.medicaldrone.repository.WeatherObservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherObservationServiceTest {

    @Mock private WeatherObservationRepository repository;
    @InjectMocks private WeatherObservationService service;

    @Test
    void persistsEveryCurrentWeatherFieldIncludingModelMetadata() {
        Map<String, Object> observation = Map.ofEntries(
                Map.entry("latitude", 12.9), Map.entry("longitude", 77.5),
                Map.entry("observed_at", "2026-10-06T14:00"),
                Map.entry("wind_speed_kmh", 14.2), Map.entry("visibility_km", 8.4),
                Map.entry("precipitation_mm", 0.2), Map.entry("temperature_c", 29.4),
                Map.entry("condition", "Rain"), Map.entry("flight_condition", "CAUTION"),
                Map.entry("confidence", 0.94), Map.entry("model_name", "weather-model-v1"));

        service.persistDecision(Map.of("observations", List.of(observation)));

        ArgumentCaptor<WeatherObservation> captor = ArgumentCaptor.forClass(WeatherObservation.class);
        verify(repository).save(captor.capture());
        WeatherObservation saved = captor.getValue();
        assertEquals(12.9, saved.getLatitude());
        assertEquals(77.5, saved.getLongitude());
        assertEquals(LocalDateTime.parse("2026-10-06T14:00"), saved.getObservedAt());
        assertEquals(14.2, saved.getWindSpeedKmh());
        assertEquals(8.4, saved.getVisibilityKm());
        assertEquals(0.2, saved.getRainMm());
        assertEquals(29.4, saved.getTemperatureC());
        assertEquals("Rain", saved.getCondition());
        assertEquals("CAUTION", saved.getFlightCondition());
        assertEquals(0.94, saved.getModelProbability());
        assertEquals("weather-model-v1", saved.getModelVersion());
    }

    @Test
    void skipsMissingOrMalformedRowsAndAcceptsAbsentDecision() {
        service.persistDecision(Map.of("observations", List.of(
                Map.of("latitude", 12.9),
                "not an observation")));
        service.persistDecision(null);
        service.persistDecision(Map.of("observations", "not a list"));

        verifyNoInteractions(repository);
    }
}
