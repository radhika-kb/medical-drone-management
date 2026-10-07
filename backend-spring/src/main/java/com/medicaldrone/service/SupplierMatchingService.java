package com.medicaldrone.service;

import com.medicaldrone.dto.InventoryDto;
import com.medicaldrone.model.AgentDecision;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.Notification;
import com.medicaldrone.model.SupplyResponse;
import com.medicaldrone.model.User;
import com.medicaldrone.repository.AgentDecisionRepository;
import com.medicaldrone.repository.MedicalCenterRepository;
import com.medicaldrone.repository.MedicalRequestRepository;
import com.medicaldrone.repository.NotificationRepository;
import com.medicaldrone.repository.SupplyResponseRepository;
import com.medicaldrone.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SupplierMatchingService {
    private static final Logger log = LoggerFactory.getLogger(SupplierMatchingService.class);

    private final WebClient aiClient;
    private final MedicalRequestRepository requestRepository;
    private final MedicalCenterRepository centerRepository;
    private final SupplyResponseRepository supplyResponseRepository;
    private final AgentDecisionRepository agentDecisionRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final MedicalRequestService medicalRequestService;
    private final SimpMessagingTemplate messagingTemplate;

    public SupplierMatchingService(@Value("${ai.service.url:http://localhost:8000}") String aiServiceUrl,
                                  MedicalRequestRepository requestRepository,
                                  MedicalCenterRepository centerRepository,
                                  SupplyResponseRepository supplyResponseRepository,
                                  AgentDecisionRepository agentDecisionRepository,
                                  NotificationRepository notificationRepository,
                                  UserRepository userRepository,
                                  InventoryService inventoryService,
                                  MedicalRequestService medicalRequestService,
                                  SimpMessagingTemplate messagingTemplate) {
        this.aiClient = WebClient.builder().baseUrl(aiServiceUrl).build();
        this.requestRepository = requestRepository;
        this.centerRepository = centerRepository;
        this.supplyResponseRepository = supplyResponseRepository;
        this.agentDecisionRepository = agentDecisionRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.medicalRequestService = medicalRequestService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public void startMatching(Long requestId) {
        MedicalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
        if (supplyResponseRepository.findByRequestId(requestId).stream()
                .anyMatch(row -> List.of("PENDING", "QUEUED", "ACCEPTED").contains(row.getResponse()))) return;

        List<InventoryDto> availableStock = inventoryService.getHospitalsWithItem(
                request.getMedicalItem().getId(), request.getQuantity());
        var previouslyResponded = supplyResponseRepository.findByRequestId(requestId).stream()
                .filter(row -> List.of("REJECTED", "ACCEPTED", "SKIPPED").contains(row.getResponse()))
                .map(row -> row.getSupplyingHospital().getId()).collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> candidatePayload = new ArrayList<>();
        for (InventoryDto stock : availableStock) {
            if (request.getRequestingHospital().getId().equals(stock.getHospitalId())) continue;
            if (previouslyResponded.contains(stock.getHospitalId())) continue;
            if (request.getSupplyingHospital() != null && !request.getSupplyingHospital().getId().equals(stock.getHospitalId())) continue;
            MedicalCenter hospital = centerRepository.findById(stock.getHospitalId()).orElse(null);
            if (hospital == null || hospital.getLatitude() == null || hospital.getLongitude() == null) continue;
            candidatePayload.add(new HashMap<>(Map.of(
                    "hospital_id", hospital.getId(),
                    "hospital_name", hospital.getName(),
                    "latitude", hospital.getLatitude(),
                    "longitude", hospital.getLongitude(),
                    "available_quantity", stock.getAvailableQuantity(),
                    "reserved_quantity", stock.getReservedQuantity() == null ? 0 : stock.getReservedQuantity(),
                    "verification_status", hospital.getVerificationStatus()
            )));
        }

        Map<String, Object> workflow = runSupplierMatchingWorkflow(request, candidatePayload);
        Map<String, Object> priority = workflow.get("priority") instanceof Map<?, ?> map
                ? stringKeyedMap(map) : fallbackPriority(request);
        agentDecisionRepository.save(new AgentDecision(request, null, "PriorityAgent",
                Objects.toString(priority.get("priority_level"), Objects.toString(priority.get("priority"), request.getUrgency())),
                number(priority.get("priority_score")), Objects.toString(priority.get("reason"), "Priority calculated")));

        if (candidatePayload.isEmpty()) {
            saveMatchingDecision(request, List.of());
            request.setStatus("NO_SUPPLIER_AVAILABLE");
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);
            publishRequestUpdate(request);
            return;
        }

        Map<String, Object> supplyMatch = workflow.get("supply_matching") instanceof Map<?, ?> map
                ? stringKeyedMap(map) : Map.of();
        List<Map<String, Object>> ranked = supplyMatch.get("ranked_suppliers") instanceof List<?> rows
                ? rows.stream().filter(Map.class::isInstance).map(row -> stringKeyedMap((Map<?, ?>) row)).toList()
                : new ArrayList<>(candidatePayload);
        if (ranked.isEmpty()) ranked = new ArrayList<>(candidatePayload);
        saveMatchingDecision(request, ranked);
        int position = 0;
        SupplyResponse firstInvitation = null;
        for (Map<String, Object> candidate : ranked) {
            Long hospitalId = longValue(candidate.get("hospital_id"));
            if (hospitalId == null) continue;
            MedicalCenter hospital = centerRepository.findById(hospitalId).orElse(null);
            if (hospital == null) continue;
            SupplyResponse invitation = new SupplyResponse(request, hospital, request.getQuantity(),
                    position == 0 ? "PENDING" : "QUEUED", null);
            invitation.setRespondedAt(null);
            invitation = supplyResponseRepository.save(invitation);
            if (position == 0) firstInvitation = invitation;
            position++;
        }

        if (position == 0) {
            request.setStatus("NO_SUPPLIER_AVAILABLE");
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);
            publishRequestUpdate(request);
            return;
        }

        request.setStatus("WAITING_SUPPLIER_RESPONSE");
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
        notifySupplier(firstInvitation);
        publishRequestUpdate(request);
    }

    @Transactional
    public void inviteNextSupplier(MedicalRequest request) {
        SupplyResponse next = supplyResponseRepository
                .findByRequestIdAndResponseOrderByIdAsc(request.getId(), "QUEUED")
                .stream().findFirst().orElse(null);
        if (next == null) {
            request.setSupplyingHospital(null);
            request.setPickupLat(null);
            request.setPickupLng(null);
            request.setStatus("NO_SUPPLIER_AVAILABLE");
            request.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(request);
            publishRequestUpdate(request);
            return;
        }
        next.setResponse("PENDING");
        next.setCreatedAt(LocalDateTime.now());
        next.setRespondedAt(null);
        supplyResponseRepository.save(next);
        request.setSupplyingHospital(null);
        request.setPickupLat(null);
        request.setPickupLng(null);
        request.setStatus("WAITING_SUPPLIER_RESPONSE");
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
        notifySupplier(next);
        publishRequestUpdate(request);
    }

    @Transactional
    public void rematch(Long requestId) {
        MedicalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
        if (!List.of("NO_SUPPLIER_AVAILABLE", "SUPPLIER_REJECTED").contains(request.getStatus())) {
            throw new IllegalStateException("Only requests without an accepted supplier can be rematched");
        }
        // Rejected suppliers are retained for audit; only create rows for new candidates.
        startMatching(requestId);
    }

    private Map<String, Object> runSupplierMatchingWorkflow(MedicalRequest request,
                                                             List<Map<String, Object>> candidates) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("request_id", request.getId());
        payload.put("request_code", request.getRequestCode());
        payload.put("medical_type", request.getMedicalType());
        payload.put("urgency", request.getUrgency());
        payload.put("weight_kg", request.getWeightKg() == null ? 1.0 : request.getWeightKg());
        Double pickupLat = request.getPickupLat() == null ? request.getDestLat() : request.getPickupLat();
        Double pickupLng = request.getPickupLng() == null ? request.getDestLng() : request.getPickupLng();
        payload.put("pickup", Map.of("lat", pickupLat, "lng", pickupLng));
        payload.put("destination", Map.of("lat", request.getDestLat(), "lng", request.getDestLng()));
        payload.put("preferred_zone", request.getZone() == null ? "CENTRAL_HUB" : request.getZone().getName());
        payload.put("drones", List.of());
        payload.put("no_fly_zones", List.of());
        payload.put("required_quantity", request.getQuantity());
        payload.put("supplier_accepted", false);
        payload.put("supplier_candidates", candidates);
        try {
            Map<?, ?> result = aiClient.post().uri("/internal/ai/workflow").bodyValue(payload)
                    .retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(8));
            if (result != null) return stringKeyedMap(result);
        } catch (Exception ex) {
            log.warn("LangGraph supplier workflow unavailable for request {}; using deterministic ranking", request.getId());
        }
        double score = switch (Objects.toString(request.getUrgency(), "HIGH").toUpperCase()) {
            case "CRITICAL" -> 100.0;
            case "HIGH" -> 80.0;
            case "MEDIUM" -> 60.0;
            default -> 40.0;
        };
        candidates.sort(Comparator
                .comparingDouble((Map<String, Object> row) -> distanceKm(number(row.get("latitude")),
                        number(row.get("longitude")), request.getDestLat(), request.getDestLng()))
                .thenComparing(Comparator.comparingInt((Map<String, Object> row) ->
                        ((Number) row.get("available_quantity")).intValue()).reversed()));
        return Map.of(
                "priority", Map.of("priority_level", request.getUrgency(), "priority_score", score,
                        "reason", "LangGraph unavailable; urgency fallback used."),
                "supply_matching", Map.of("status", candidates.isEmpty() ? "NO_ELIGIBLE_SUPPLIER" : "MATCH_FOUND",
                        "ranked_suppliers", candidates),
                "workflow_status", "WAIT_FOR_SUPPLIER");
    }

    private Map<String, Object> fallbackPriority(MedicalRequest request) {
        double score = switch (Objects.toString(request.getUrgency(), "HIGH").toUpperCase()) {
            case "CRITICAL" -> 100.0;
            case "HIGH" -> 80.0;
            case "MEDIUM" -> 60.0;
            default -> 40.0;
        };
        return Map.of("priority_level", request.getUrgency(), "priority_score", score,
                "reason", "LangGraph priority result unavailable; submitted urgency retained.");
    }

    private Map<String, Object> stringKeyedMap(Map<?, ?> source) {
        Map<String, Object> result = new HashMap<>();
        source.forEach((key, value) -> result.put(key.toString(), value));
        return result;
    }

    private void saveMatchingDecision(MedicalRequest request, List<Map<String, Object>> ranked) {
        String summary = ranked.stream().map(row -> Objects.toString(row.get("hospital_name"), "supplier"))
                .reduce((left, right) -> left + ", " + right).orElse("No eligible suppliers");
        agentDecisionRepository.save(new AgentDecision(request, null, "SupplyMatchingAgent", "RANKED",
                ranked.isEmpty() ? 0.0 : 1.0, "Supplier ranking: " + summary));
    }

    private void notifySupplier(SupplyResponse invitation) {
        Long hospitalId = invitation.getSupplyingHospital().getId();
        String message = "Medical request " + invitation.getRequest().getRequestCode() + " is ready for your response.";
        userRepository.findByHospitalId(hospitalId).stream()
                .filter(user -> user.getRole() != null && "ROLE_SUPPLYING_HOSPITAL".equals(user.getRole().getName()))
                .forEach(user -> notificationRepository.save(new Notification(user, null, "SUPPLIER_REQUEST_INVITATION", message)));
        messagingTemplate.convertAndSend("/topic/suppliers/" + hospitalId,
                medicalRequestService.toResponseDto(invitation.getRequest()));
    }

    private void publishRequestUpdate(MedicalRequest request) {
        var dto = medicalRequestService.toResponseDto(request);
        messagingTemplate.convertAndSend("/topic/requests/" + request.getId(), dto);
        messagingTemplate.convertAndSend("/topic/requests", dto);
    }

    private double distanceKm(double lat1, double lng1, Double lat2, Double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : value == null ? null : Double.valueOf(value.toString());
    }

    private Long longValue(Object value) {
        return value instanceof Number n ? n.longValue() : value == null ? null : Long.valueOf(value.toString());
    }
}
