package com.medicaldrone.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {
    private final WebClient aiClient;

    public WeatherController(@Value("${ai.service.url:http://localhost:8000}") String aiServiceUrl) {
        this.aiClient = WebClient.builder().baseUrl(aiServiceUrl).build();
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL', 'SUPPLYING_HOSPITAL')")
    public ResponseEntity<?> current(@RequestParam(defaultValue = "12.9716") double lat,
                                     @RequestParam(defaultValue = "77.5946") double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return ResponseEntity.badRequest().body(Map.of("status", "UNAVAILABLE", "reason", "Invalid location"));
        }
        Map<String, Object> point = Map.of("lat", lat, "lng", lng);
        try {
            Map<?, ?> result = aiClient.post().uri("/internal/ai/weather")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("pickup", point, "destination", point))
                    .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(12)).block();
            if (result == null || !(result.get("observations") instanceof java.util.List<?> rows) || rows.isEmpty()) {
                return ResponseEntity.ok(Map.of("status", "UNAVAILABLE", "reason", "Live weather observations are unavailable."));
            }
            return ResponseEntity.ok(Map.of("status", "LIVE", "data", result));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("status", "UNAVAILABLE", "reason", "Live weather service is unavailable."));
        }
    }
}
