package com.medicaldrone.service;

import com.medicaldrone.dto.AIDecisionDto;
import com.medicaldrone.model.Drone;
import com.medicaldrone.model.MedicalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(AIServiceClient.class);

    private final WebClient webClient;
    private final long workflowTimeoutSeconds;

    public AIServiceClient(@Value("${ai.service.url:http://localhost:8000}") String aiServiceUrl,
                           @Value("${ai.service.workflow-timeout-seconds:30}") long workflowTimeoutSeconds) {
        this.webClient = WebClient.builder()
                .baseUrl(aiServiceUrl)
                .build();
        this.workflowTimeoutSeconds = workflowTimeoutSeconds;
    }

    public AIDecisionDto.ResponsePayload getAIDecision(MedicalRequest request, List<Drone> candidateDrones) {
        AIDecisionDto.RequestPayload payload = new AIDecisionDto.RequestPayload();
        payload.setRequestId(request.getId());
        payload.setRequestCode(request.getRequestCode());
        payload.setMedicalType(request.getMedicalType());
        payload.setUrgency(request.getUrgency());
        payload.setWeightKg(request.getWeightKg());
        payload.setPickup(new AIDecisionDto.GeoPoint(request.getPickupLat(), request.getPickupLng()));
        payload.setDestination(new AIDecisionDto.GeoPoint(request.getDestLat(), request.getDestLng()));
        payload.setPreferredZone(request.getZone() != null ? request.getZone().getName() : "NORTH_ZONE");
        payload.setRequiredQuantity(request.getQuantity());
        payload.setSupplierAccepted(request.getSupplyingHospital() != null);
        if (request.getSupplyingHospital() != null) {
            var supplier = request.getSupplyingHospital();
            payload.setSupplierCandidates(List.of(Map.of(
                    "hospital_id", supplier.getId(),
                    "hospital_name", supplier.getName(),
                    "latitude", supplier.getLatitude(),
                    "longitude", supplier.getLongitude(),
                    "available_quantity", request.getQuantity(),
                    "reserved_quantity", 0,
                    "verification_status", supplier.getVerificationStatus()
            )));
        } else {
            payload.setSupplierCandidates(List.of());
        }

        // Map candidate drones
        List<AIDecisionDto.DronePayload> dronePayloads = candidateDrones.stream().map(d -> {
            AIDecisionDto.DronePayload dp = new AIDecisionDto.DronePayload();
            dp.setId(d.getId());
            dp.setDroneCode(d.getDroneCode());
            dp.setModel(d.getModel());
            dp.setZoneName(d.getZone() != null ? d.getZone().getName() : "CENTRAL_HUB");
            dp.setBatteryPct(d.getBatteryPct());
            dp.setMaxPayloadKg(d.getMaxPayloadKg());
            dp.setMaxRangeKm(d.getMaxRangeKm());
            dp.setLatitude(d.getLatitude());
            dp.setLongitude(d.getLongitude());
            dp.setStatus(d.getStatus());
            return dp;
        }).collect(Collectors.toList());
        payload.setDrones(dronePayloads);

        // Legacy no_fly_zones are arbitrary database circles, not authoritative
        // DigitalSky geometry. Send no fabricated geometry; AI fails closed.
        payload.setNoFlyZones(List.of());

        try {
            logger.info("Calling LangGraph workflow in FastAPI for request {}", request.getRequestCode());
            return webClient.post()
                    .uri("/internal/ai/workflow")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(AIDecisionDto.ResponsePayload.class)
                    .timeout(Duration.ofSeconds(workflowTimeoutSeconds))
                    .block();
        } catch (Exception ex) {
            logger.error("LangGraph workflow failed for request {}: {}", request.getRequestCode(), ex.getMessage());
            throw new IllegalStateException("AI recommendation workflow is unavailable; Central Hub review cannot be prepared safely.", ex);
        }
    }

    public Map<String, Object> checkMissionMonitoring(Long missionId, double elapsedMinutes,
                                                      double distanceFlownKm, double totalDistanceKm,
                                                      double expectedDurationMinutes, double batteryUsedPct) {
        Map<String, Object> req = Map.of(
                "mission_id", missionId,
                "elapsed_minutes", elapsedMinutes,
                "distance_flown_km", distanceFlownKm,
                "total_distance_km", totalDistanceKm,
                "expected_duration_minutes", expectedDurationMinutes,
                "battery_used_pct", batteryUsedPct,
                "altitude_variance", 0.2,
                "position_drift_meters", 1.0
        );
        try {
            return webClient.post()
                    .uri("/internal/ai/monitor")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (Exception ex) {
            logger.warn("AI monitor call failed: {}. Using deterministic milestone checks.", ex.getMessage());
            boolean timeout = elapsedMinutes > (expectedDurationMinutes * 1.5);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("monitoring_available", false);
            fallback.put("is_anomaly", true);
            fallback.put("anomaly_score", timeout ? 1.0 : 0.95);
            fallback.put("anomaly_type", timeout ? "TIMEOUT" : "MONITORING_UNAVAILABLE");
            fallback.put("severity", "CRITICAL");
            fallback.put("explanation", timeout ? "Mission duration exceeded 150% threshold." : "AI monitoring is unavailable; hold for Central Hub review.");
            return fallback;
        }
    }

    public Map<String, Object> forecastDemand(Long hospitalId, Long medicineId, String medicineCategory, int availableStock) {
        return forecastDemand(hospitalId, medicineId, medicineCategory, availableStock, 0, 0, 0);
    }

    public Map<String, Object> forecastDemand(Long hospitalId, Long medicineId, String medicineCategory,
                                               int availableStock, long previousDayDemand,
                                               long previousWeekDemand, long emergencyRequests) {
        java.time.LocalDate today = java.time.LocalDate.now();
        Map<String, Object> req = new HashMap<>();
        req.put("hospital_id", hospitalId);
        req.put("medicine_id", medicineId);
        req.put("medicine_category", medicineCategory != null ? medicineCategory : "OTHER");
        req.put("available_stock", availableStock);
        req.put("day_of_week", today.getDayOfWeek().getValue() % 7);
        req.put("month", today.getMonthValue());
        req.put("is_holiday", 0);
        req.put("previous_day_demand", previousDayDemand);
        req.put("previous_week_demand", previousWeekDemand);
        req.put("emergency_requests", emergencyRequests);
        try {
            return webClient.post()
                    .uri("/internal/ai/demand")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (Exception ex) {
            logger.warn("AI demand forecasting call failed: {}. Using fallback prediction.", ex.getMessage());
            double predicted = Math.max(previousDayDemand, previousWeekDemand / 7.0);
            int shortage = Math.max(0, (int) Math.round(predicted - availableStock));
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("predicted_demand_quantity", predicted);
            fallback.put("available_stock", availableStock);
            fallback.put("shortage_risk_units", shortage);
            fallback.put("reserved_stock", 0);
            fallback.put("inventory_risk_level", shortage >= 15 ? "HIGH" : (shortage > 0 ? "MEDIUM" : "LOW"));
            fallback.put("model_name", "HistoricalDemandFallback");
            fallback.put("recommendation", shortage > 0 ? "Replenish " + shortage + " units." : "Stock covers the historical demand estimate.");
            return fallback;
        }
    }
}
