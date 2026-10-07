package com.medicaldrone.controller;

import com.medicaldrone.dto.AIDecisionDto;
import com.medicaldrone.dto.InventoryDto;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.repository.AgentDecisionRepository;
import com.medicaldrone.repository.MedicalCenterRepository;
import com.medicaldrone.service.InventoryService;
import com.medicaldrone.service.DemandForecastService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AIController {

    private final WebClient webClient;
    private final InventoryService inventoryService;
    private final MedicalCenterRepository medicalCenterRepository;
    private final AgentDecisionRepository decisionRepository;
    private final com.medicaldrone.repository.DemandPredictionRepository demandPredictionRepository;
    private final com.medicaldrone.repository.ETAPredictionRepository etaPredictionRepository;
    private final com.medicaldrone.repository.MissionAnomalyRepository anomalyRepository;
    private final DemandForecastService demandForecastService;

    public AIController(@Value("${ai.service.url:http://localhost:8000}") String aiServiceUrl,
                        InventoryService inventoryService,
                        MedicalCenterRepository medicalCenterRepository,
                        AgentDecisionRepository decisionRepository,
                        com.medicaldrone.repository.DemandPredictionRepository demandPredictionRepository,
                        com.medicaldrone.repository.ETAPredictionRepository etaPredictionRepository,
                        com.medicaldrone.repository.MissionAnomalyRepository anomalyRepository,
                        DemandForecastService demandForecastService) {
        this.webClient = WebClient.builder().baseUrl(aiServiceUrl).build();
        this.inventoryService = inventoryService;
        this.medicalCenterRepository = medicalCenterRepository;
        this.decisionRepository = decisionRepository;
        this.demandPredictionRepository = demandPredictionRepository;
        this.etaPredictionRepository = etaPredictionRepository;
        this.anomalyRepository = anomalyRepository;
        this.demandForecastService = demandForecastService;
    }

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> getAIHealth() {
        try {
            Map<?, ?> health = webClient.get()
                    .uri("/internal/ai/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            return ResponseEntity.ok(health);
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of(
                    "status", "FALLBACK",
                    "message", "AI Microservice unreachable; using Spring Boot rule fallbacks.",
                    "models_loaded", Map.of("weather", true, "demand", true, "eta", true, "anomaly", true)
            ));
        }
    }

    @PostMapping("/supply-match")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL')")
    public ResponseEntity<?> matchSuppliers(@RequestBody Map<String, Object> payload) {
        Long medicalItemId = Long.valueOf(payload.get("medicalItemId").toString());
        Integer quantity = payload.get("quantity") != null ? Integer.valueOf(payload.get("quantity").toString()) : 1;
        Double destLat = Double.valueOf(payload.get("destLat").toString());
        Double destLng = Double.valueOf(payload.get("destLng").toString());

        List<InventoryDto> stocks = inventoryService.getHospitalsWithItem(medicalItemId, quantity);

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (InventoryDto inv : stocks) {
            MedicalCenter h = medicalCenterRepository.findById(inv.getHospitalId()).orElse(null);
            if (h != null && "APPROVED".equalsIgnoreCase(h.getVerificationStatus())) {
                candidates.add(Map.of(
                        "hospital_id", h.getId(),
                        "hospital_name", h.getName(),
                        "latitude", h.getLatitude(),
                        "longitude", h.getLongitude(),
                        "available_quantity", inv.getAvailableQuantity(),
                        "reserved_quantity", inv.getReservedQuantity(),
                        "verification_status", h.getVerificationStatus()
                ));
            }
        }

        Map<String, Object> req = Map.of(
                "destination", Map.of("lat", destLat, "lng", destLng),
                "required_quantity", quantity,
                "candidates", candidates
        );

        try {
            Map<?, ?> response = webClient.post()
                    .uri("/internal/ai/supply-match")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            // Local fallback ranking
            List<Map<String, Object>> ranked = new ArrayList<>();
            for (Map<String, Object> c : candidates) {
                double cLat = (double) c.get("latitude");
                double cLng = (double) c.get("longitude");
                double dist = Math.sqrt(Math.pow(cLat - destLat, 2) + Math.pow(cLng - destLng, 2)) * 111.0;
                Map<String, Object> row = new HashMap<>(c);
                row.put("distance_km", Math.round(dist * 10.0) / 10.0);
                row.put("match_score", Math.round((1.0 / (1.0 + dist / 10.0)) * 100.0) / 100.0);
                row.put("reason", "Fallback proximity match (" + Math.round(dist) + " km)");
                ranked.add(row);
            }
            ranked.sort((a, b) -> Double.compare((Double) b.get("match_score"), (Double) a.get("match_score")));
            return ResponseEntity.ok(Map.of(
                    "status", ranked.isEmpty() ? "NO_ELIGIBLE_SUPPLIER" : "MATCH_FOUND",
                    "recommended_supplier", ranked.isEmpty() ? null : ranked.get(0),
                    "ranked_suppliers", ranked,
                    "reason", ranked.isEmpty() ? "No eligible suppliers found" : "Ranked by distance and inventory availability."
            ));
        }
    }

    @PostMapping("/demand-forecast")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> forecastDemand(@RequestBody Map<String, Object> payload) {
        if (payload.get("hospital_id") == null || payload.get("medicine_id") == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "hospital_id and medicine_id are required"));
        }
        return ResponseEntity.ok(demandForecastService.forecastOne(
                Long.valueOf(payload.get("hospital_id").toString()),
                Long.valueOf(payload.get("medicine_id").toString())));
    }

    @PostMapping("/demand-forecasts/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> runDemandForecasts() {
        return ResponseEntity.ok(demandForecastService.runForecasts());
    }

    @GetMapping("/demand-risk-alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getActiveDemandRiskAlerts() {
        return ResponseEntity.ok(demandForecastService.getActiveRiskAlerts());
    }

    @GetMapping("/demand-predictions")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> getRecentDemandPredictions() {
        return ResponseEntity.ok(demandPredictionRepository.findTop500ByOrderByCreatedAtDesc());
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> getRecentAnomalies() {
        return ResponseEntity.ok(anomalyRepository.findTop20ByOrderByDetectedAtDesc().stream().map(this::toAnomalyDto).toList());
    }

    @GetMapping("/anomalies/mission/{missionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> getAnomaliesByMission(@PathVariable Long missionId) {
        return ResponseEntity.ok(anomalyRepository.findByMissionId(missionId).stream().map(this::toAnomalyDto).toList());
    }

    private com.medicaldrone.dto.MissionAnomalyDto toAnomalyDto(com.medicaldrone.model.MissionAnomaly anomaly) {
        String severity = anomaly.getSeverity() == null ? "MEDIUM" : anomaly.getSeverity().toUpperCase(java.util.Locale.ROOT);
        String type = anomaly.getAnomalyType() == null ? "OPERATIONAL_ANOMALY" : anomaly.getAnomalyType().toUpperCase(java.util.Locale.ROOT);
        String recommendedAction = severity.equals("CRITICAL") || type.contains("BATTERY") || type.contains("TIMEOUT")
                ? "ABORT" : severity.equals("HIGH") ? "REASSIGN" : "DELAY";
        var mission = anomaly.getMission();
        return new com.medicaldrone.dto.MissionAnomalyDto(anomaly.getId(), mission == null ? null : mission.getId(),
                mission == null ? null : mission.getMissionCode(), anomaly.getAnomalyType(), anomaly.getSeverity(),
                anomaly.getAnomalyScore(), anomaly.getExplanation(), anomaly.getDetectedAt(), recommendedAction);
    }

    @GetMapping("/eta-predictions/mission/{missionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> getETAPredictionsByMission(@PathVariable Long missionId) {
        return ResponseEntity.ok(etaPredictionRepository.findByMissionId(missionId));
    }

    @GetMapping("/decisions/mission/{missionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> getDecisionsByMission(@PathVariable Long missionId) {
        return ResponseEntity.ok(decisionRepository.findByMissionIdOrderByCreatedAtAsc(missionId));
    }
}
