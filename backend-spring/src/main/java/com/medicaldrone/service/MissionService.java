package com.medicaldrone.service;

import com.medicaldrone.dto.AIDecisionDto;
import com.medicaldrone.dto.MissionDto;
import com.medicaldrone.model.*;
import com.medicaldrone.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MissionService {

    private static final Logger logger = LoggerFactory.getLogger(MissionService.class);

    private final MissionRepository missionRepository;
    private final MedicalRequestRepository requestRepository;
    private final PaymentRepository paymentRepository;
    private final DroneRepository droneRepository;
    private final RouteRepository routeRepository;
    private final RouteWaypointRepository waypointRepository;
    private final AgentDecisionRepository decisionRepository;
    private final ETAPredictionRepository etaPredictionRepository;
    private final NotificationRepository notificationRepository;
    private final InventoryService inventoryService;
    private final AIServiceClient aiServiceClient;
    private final TrackingSimulationService simulationService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final AuditService auditService;
    private final WeatherObservationService weatherObservationService;
    @Value("${medical-drone.mission-simulation.enabled:true}")
    private boolean missionSimulationEnabled = true;

    public MissionService(MissionRepository missionRepository, MedicalRequestRepository requestRepository,
                          PaymentRepository paymentRepository,
                          DroneRepository droneRepository, RouteRepository routeRepository,
                          RouteWaypointRepository waypointRepository, AgentDecisionRepository decisionRepository,
                          ETAPredictionRepository etaPredictionRepository,
                          NotificationRepository notificationRepository, InventoryService inventoryService,
                          AIServiceClient aiServiceClient, TrackingSimulationService simulationService,
                          org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate,
                          AuditService auditService, WeatherObservationService weatherObservationService) {
        this.missionRepository = missionRepository;
        this.requestRepository = requestRepository;
        this.paymentRepository = paymentRepository;
        this.droneRepository = droneRepository;
        this.routeRepository = routeRepository;
        this.waypointRepository = waypointRepository;
        this.decisionRepository = decisionRepository;
        this.etaPredictionRepository = etaPredictionRepository;
        this.notificationRepository = notificationRepository;
        this.inventoryService = inventoryService;
        this.aiServiceClient = aiServiceClient;
        this.simulationService = simulationService;
        this.messagingTemplate = messagingTemplate;
        this.auditService = auditService;
        this.weatherObservationService = weatherObservationService;
    }

    public List<MissionDto.Response> getAllMissions() {
        return missionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public Optional<MissionDto.Response> getMissionById(Long id) {
        return missionRepository.findById(id).map(this::toResponseDto);
    }

    public List<MissionTracking> getMissionTrackingHistory(Long missionId) {
        return List.of();
    }

    @Transactional
    public MissionDto.Response prepareHubReview(Long requestId, Long actorUserId, String actorEmail) {
        MedicalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found with id: " + requestId));
        requirePaidRequest(request);

        Mission mission = missionRepository.findByRequestId(requestId).orElse(null);
        if (mission != null && List.of("CENTRAL_HUB_REVIEW", "DISPATCH_DELAYED").contains(mission.getStatus())) {
            return toResponseDto(mission);
        }
        if (mission != null && !"REJECTED".equals(mission.getStatus())) {
            throw new IllegalStateException("This request already has an active or completed mission");
        }
        if (!List.of("READY_FOR_DISPATCH", "DISPATCH_REJECTED").contains(request.getStatus())) {
            throw new IllegalStateException("The request must be paid and ready for hub review");
        }
        validateRequestLocations(request);

        List<Drone> available = droneRepository.findByStatus("AVAILABLE");
        AIDecisionDto.ResponsePayload decision;
        try {
            decision = getApprovedDecision(request, available);
        } catch (IllegalStateException ex) {
            auditService.record(actorUserId, actorEmail, "SAFETY_REJECTED", "medical_requests", requestId, ex.getMessage());
            throw ex;
        }
        Drone drone = lockAllocatedDrone(decision, available);

        if (mission == null) {
            String missionCode = "MSN-BLR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            mission = new Mission(missionCode, request, drone, 0.0, 0.0);
            mission.setRequestingHospital(request.getRequestingHospital() != null ? request.getRequestingHospital() : request.getCenter());
            mission.setSupplyingHospital(request.getSupplyingHospital());
            mission.setStartedAt(null);
        } else if (mission.getDrone() != null && "RESERVED_FOR_REVIEW".equals(mission.getDrone().getStatus())) {
            mission.getDrone().setStatus("AVAILABLE");
            droneRepository.save(mission.getDrone());
        }
        mission.setDrone(drone);
        mission.setStatus("CENTRAL_HUB_REVIEW");
        mission.setHubDecisionReason(null);
        mission.setStartedAt(null);
        mission.setUpdatedAt(LocalDateTime.now());
        missionRepository.saveAndFlush(mission);

        drone.setStatus("RESERVED_FOR_REVIEW");
        drone.setUpdatedAt(LocalDateTime.now());
        droneRepository.save(drone);

        Route route = routeRepository.findByMissionId(mission.getId()).orElse(null);
        if (route == null) {
            route = new Route(mission, "A*-Airspace", 10.0, 10.0, 20.0);
        } else {
            route.getWaypoints().clear();
            routeRepository.saveAndFlush(route);
        }
        applyDecisionToRouteAndTrail(request, mission, route, decision);
        auditService.record(actorUserId, actorEmail, "DRONE_ASSIGNED", "missions", mission.getId(),
                "drone=" + drone.getDroneCode() + "; status=RESERVED_FOR_REVIEW");

        request.setStatus("CENTRAL_HUB_REVIEW");
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
        notifyHub(request, mission, "MISSION_AWAITING_HUB_REVIEW",
                "AI recommendation " + mission.getMissionCode() + " is ready for Central Hub review.");
        publishMissionAndRequest(mission, request);
        return toResponseDto(mission);
    }

    @Transactional
    public MissionDto.Response applyHubDecision(Long missionId, String action, String reason, Long actorUserId, String actorEmail) {
        Mission mission = missionRepository.findByIdForUpdate(missionId)
                .orElseThrow(() -> new IllegalArgumentException("Mission not found with id: " + missionId));
        MedicalRequest request = mission.getRequest();
        String decision = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        String decisionReason = reason == null || reason.isBlank() ? null : reason.trim();
        LocalDateTime now = LocalDateTime.now();

        switch (decision) {
            case "DISPATCH" -> {
                if (!List.of("CENTRAL_HUB_REVIEW", "DISPATCH_DELAYED").contains(mission.getStatus())) {
                    throw new IllegalStateException("Only a reviewed or delayed mission can be dispatched");
                }
                requirePaidRequest(request);
                if (!List.of("CENTRAL_HUB_REVIEW", "DISPATCH_DELAYED").contains(request.getStatus())) {
                    throw new IllegalStateException("The request is no longer waiting for Central Hub dispatch authorization");
                }
                validateRequestLocations(request);
                if (mission.getDrone() == null || !"RESERVED_FOR_REVIEW".equals(mission.getDrone().getStatus())) {
                    throw new IllegalStateException("The AI allocated drone is no longer reserved for this review");
                }
                Route route = routeRepository.findByMissionId(missionId)
                        .orElseThrow(() -> new IllegalStateException("The reviewed route is missing"));
                if (route.getWaypoints().size() < 2) throw new IllegalStateException("The reviewed route has no usable waypoints");
                mission.getDrone().setStatus("ASSIGNED");
                mission.getDrone().setUpdatedAt(now);
                droneRepository.save(mission.getDrone());
                mission.setStatus("DISPATCHED");
                mission.setStartedAt(now);
                mission.setHubDecisionReason(decisionReason);
                mission.setUpdatedAt(now);
                missionRepository.save(mission);
                request.setStatus("DISPATCHED");
                request.setUpdatedAt(now);
                requestRepository.save(request);
                auditService.record(actorUserId, actorEmail, "MISSION_DISPATCHED", "missions", mission.getId(),
                        "drone=" + mission.getDrone().getDroneCode() + "; reason=" + Objects.toString(decisionReason, ""));
                auditService.record(actorUserId, actorEmail, "ROUTE_APPROVED", "routes", route.getId(), "mission=" + mission.getMissionCode());
                notifyHub(request, mission, "MISSION_DISPATCHED",
                        "Central Hub dispatched " + mission.getMissionCode() + " with drone " + mission.getDrone().getDroneCode() + ".");
                publishMissionAndRequest(mission, request);
                if (missionSimulationEnabled) simulationService.startMissionSimulation(mission.getId());
            }
            case "DELAY" -> {
                requireReviewable(mission);
                mission.setStatus("DISPATCH_DELAYED");
                mission.setHubDecisionReason(decisionReason);
                mission.setUpdatedAt(now);
                missionRepository.save(mission);
                request.setStatus("DISPATCH_DELAYED");
                request.setUpdatedAt(now);
                requestRepository.save(request);
                auditService.record(actorUserId, actorEmail, "MISSION_DELAYED", "missions", missionId, Objects.toString(decisionReason, ""));
                notifyHub(request, mission, "MISSION_DISPATCH_DELAYED",
                        "Central Hub delayed " + mission.getMissionCode() + "." + (decisionReason == null ? "" : " Reason: " + decisionReason));
                publishMissionAndRequest(mission, request);
            }
            case "REJECT" -> {
                requireReviewable(mission);
                releaseReviewDrone(mission);
                mission.setStatus("REJECTED");
                mission.setHubDecisionReason(decisionReason);
                mission.setUpdatedAt(now);
                missionRepository.save(mission);
                request.setStatus("READY_FOR_DISPATCH");
                request.setUpdatedAt(now);
                requestRepository.save(request);
                auditService.record(actorUserId, actorEmail, "MISSION_RECOMMENDATION_REJECTED", "missions", missionId, Objects.toString(decisionReason, ""));
                notifyHub(request, mission, "MISSION_RECOMMENDATION_REJECTED",
                        "Central Hub rejected the recommendation for " + mission.getMissionCode() + "." + (decisionReason == null ? "" : " Reason: " + decisionReason));
                publishMissionAndRequest(mission, request);
            }
            case "REALLOCATE" -> {
                requireReviewable(mission);
                reallocateReview(mission, decisionReason);
                mission.setHubDecisionReason(decisionReason == null ? "Central Hub requested a new allocation" : decisionReason);
                mission.setUpdatedAt(now);
                missionRepository.save(mission);
                request.setStatus("CENTRAL_HUB_REVIEW");
                request.setUpdatedAt(now);
                requestRepository.save(request);
                auditService.record(actorUserId, actorEmail, "DRONE_ASSIGNED", "missions", missionId,
                        "drone=" + mission.getDrone().getDroneCode() + "; reason=" + Objects.toString(decisionReason, ""));
                notifyHub(request, mission, "MISSION_REALLOCATED",
                        "Central Hub requested a new drone recommendation for " + mission.getMissionCode() + ".");
                publishMissionAndRequest(mission, request);
            }
            default -> throw new IllegalArgumentException("Hub action must be DISPATCH, DELAY, REJECT, or REALLOCATE");
        }
        String auditReason = decisionReason == null ? "Central Hub selected " + decision : decisionReason;
        decisionRepository.save(new AgentDecision(request, mission, "CentralHub", decision, null, auditReason));
        return toResponseDto(mission);
    }

    private void reallocateReview(Mission mission, String reason) {
        List<Drone> available = droneRepository.findByStatus("AVAILABLE").stream()
                .filter(drone -> mission.getDrone() == null || !drone.getId().equals(mission.getDrone().getId()))
                .toList();
        AIDecisionDto.ResponsePayload decision = getApprovedDecision(mission.getRequest(), available);
        Drone newDrone = lockAllocatedDrone(decision, available);
        Drone previousDrone = mission.getDrone();
        newDrone.setStatus("RESERVED_FOR_REVIEW");
        newDrone.setUpdatedAt(LocalDateTime.now());
        droneRepository.save(newDrone);
        mission.setDrone(newDrone);
        if (previousDrone != null && "RESERVED_FOR_REVIEW".equals(previousDrone.getStatus())) {
            previousDrone.setStatus("AVAILABLE");
            previousDrone.setUpdatedAt(LocalDateTime.now());
            droneRepository.save(previousDrone);
        }
        Route route = routeRepository.findByMissionId(mission.getId())
                .orElseThrow(() -> new IllegalStateException("The reviewed route is missing"));
        route.getWaypoints().clear();
        routeRepository.saveAndFlush(route);
        applyDecisionToRouteAndTrail(mission.getRequest(), mission, route, decision);
    }

    private AIDecisionDto.ResponsePayload getApprovedDecision(MedicalRequest request, List<Drone> candidates) {
        if (candidates.isEmpty()) throw new IllegalStateException("No available drones for AI resource allocation");
        AIDecisionDto.ResponsePayload decision = aiServiceClient.getAIDecision(request, candidates);
        if (decision != null) weatherObservationService.persistDecision(decision.getWeather());
        if (decision == null || !"APPROVED".equalsIgnoreCase(decision.getStatus())) {
            String reason = decision != null && decision.getSafety() != null
                    ? Objects.toString(decision.getSafety().get("reason"), "Safety constraints not met.")
                    : "Safety constraints not met.";
            throw new IllegalStateException("AI recommendation cannot be presented for dispatch: " + reason);
        }
        if (decision.getRoute() == null || decision.getRoute().get("waypoints") == null) {
            throw new IllegalStateException("AI recommendation did not include a route");
        }
        return decision;
    }

    private Drone lockAllocatedDrone(AIDecisionDto.ResponsePayload decision, List<Drone> candidates) {
        Map<String, Object> allocation = decision.getAllocation();
        if (allocation == null || (allocation.get("drone_id") == null && allocation.get("drone_code") == null)) {
            throw new IllegalStateException("AI recommendation did not include a drone allocation");
        }
        Drone drone;
        if (allocation.get("drone_id") != null) {
            Long id = Long.valueOf(allocation.get("drone_id").toString());
            drone = droneRepository.findByIdWithLock(id)
                    .orElseThrow(() -> new IllegalStateException("AI allocated drone not found: " + id));
        } else {
            String code = Objects.toString(allocation.get("drone_code"));
            drone = droneRepository.findByDroneCode(code)
                    .orElseThrow(() -> new IllegalStateException("AI allocated drone not found: " + code));
        }
        if (!candidates.stream().anyMatch(candidate -> candidate.getId().equals(drone.getId()))
                || !"AVAILABLE".equals(drone.getStatus())) {
            throw new IllegalStateException("The AI allocated drone is no longer available");
        }
        return drone;
    }

    @SuppressWarnings("unchecked")
    private void applyDecisionToRouteAndTrail(MedicalRequest request, Mission mission, Route route,
                                               AIDecisionDto.ResponsePayload decision) {
        Map<String, Object> routeMap = decision.getRoute();
        Double distanceKm = value(routeMap.get("distance_km"), 10.0);
        Double baselineEtaMinutes = value(routeMap.get("baseline_eta_minutes"), value(routeMap.get("eta_minutes"), 10.0));
        Double mlEtaMinutes = value(routeMap.get("ml_predicted_eta_minutes"), baselineEtaMinutes);
        Double finalEtaMinutes = value(routeMap.get("final_eta_minutes"), value(routeMap.get("eta_minutes"), mlEtaMinutes));
        Double batteryReq = value(routeMap.get("battery_required_pct"), 20.0);
        String algorithm = Objects.toString(routeMap.get("algorithm"), "A*-Airspace");
        route.setAlgorithm(algorithm);
        route.setTotalDistanceKm(distanceKm);
        route.setBaselineEtaMinutes(baselineEtaMinutes);
        route.setMlEtaMinutes(mlEtaMinutes);
        route.setFinalEtaMinutes(finalEtaMinutes);
        route.setEtaMinutes(finalEtaMinutes);
        route.setBatteryRequiredPct(batteryReq);
        route.setActive(true);
        routeRepository.save(route);
        mission.setDistanceKm(distanceKm);
        mission.setEtaMinutes(finalEtaMinutes);
        missionRepository.save(mission);

        List<Map<String, Object>> waypoints = (List<Map<String, Object>>) routeMap.get("waypoints");
        int sequence = 1;
        for (Map<String, Object> waypoint : waypoints) {
            RouteWaypoint row = new RouteWaypoint(sequence++, Double.valueOf(waypoint.get("lat").toString()),
                    Double.valueOf(waypoint.get("lng").toString()), 60.0,
                    Objects.toString(waypoint.get("type"), "WAYPOINT"));
            row.setRoute(route);
            waypointRepository.save(row);
            route.getWaypoints().add(row);
        }

        if (decision.getDecisionTrail() != null) {
            for (Map<String, Object> item : decision.getDecisionTrail()) {
                AgentDecision row = new AgentDecision(request, mission, Objects.toString(item.get("agent"), "AI"),
                        Objects.toString(item.get("decision"), "RECOMMENDED"), valueOrNull(item.get("score")),
                        Objects.toString(item.get("reason"), "No explanation provided"));
                decisionRepository.save(row);
            }
        }
        String etaModel = Objects.toString(routeMap.get("model_name"), "ETAEnsemble");
        etaPredictionRepository.save(new ETAPrediction(mission, mlEtaMinutes, etaModel, "1.0.0",
                "Distance: " + distanceKm + "km, baseline: " + baselineEtaMinutes + "m, ML: "
                        + mlEtaMinutes + "m, final: " + finalEtaMinutes + "m, battery: " + batteryReq + "%"));
    }

    private void requirePaidRequest(MedicalRequest request) {
        boolean paymentCaptured = paymentRepository.findByRequestId(request.getId()).stream()
                .anyMatch(payment -> "SUCCESS".equalsIgnoreCase(payment.getPaymentStatus()));
        if (!paymentCaptured) throw new IllegalStateException("Successful payment is required before hub review or dispatch");
        if (request.getSupplyingHospital() == null) throw new IllegalStateException("The request has no accepted supplier");
        if (request.getMedicalItem() == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalStateException("A valid medical item quantity is required before hub review or dispatch");
        }
        if (!inventoryService.hasReservedStockForUpdate(request.getSupplyingHospital().getId(), request.getMedicalItem().getId(), request.getQuantity())) {
            throw new IllegalStateException("The accepted supplier no longer has the required inventory reserved");
        }
    }

    private void validateRequestLocations(MedicalRequest request) {
        if (request.getPickupLat() == null || request.getPickupLng() == null
                || request.getDestLat() == null || request.getDestLng() == null) {
            throw new IllegalStateException("Supplier and requester locations must be confirmed before flight review");
        }
    }

    private void requireReviewable(Mission mission) {
        if (!List.of("CENTRAL_HUB_REVIEW", "DISPATCH_DELAYED").contains(mission.getStatus())) {
            throw new IllegalStateException("This mission is not awaiting a Central Hub decision");
        }
    }

    private void releaseReviewDrone(Mission mission) {
        if (mission.getDrone() != null && "RESERVED_FOR_REVIEW".equals(mission.getDrone().getStatus())) {
            mission.getDrone().setStatus("AVAILABLE");
            mission.getDrone().setUpdatedAt(LocalDateTime.now());
            droneRepository.save(mission.getDrone());
        }
    }

    private Double value(Object value, double fallback) {
        return value == null ? fallback : Double.valueOf(value.toString());
    }

    private Double valueOrNull(Object value) {
        return value == null ? null : Double.valueOf(value.toString());
    }

    private void notifyHub(MedicalRequest request, Mission mission, String type, String message) {
        notificationRepository.save(new Notification(request.getCreatedBy(), mission, type, message));
    }

    private void publishMissionAndRequest(Mission mission, MedicalRequest request) {
        MissionDto.Response missionDto = toResponseDto(mission);
        messagingTemplate.convertAndSend("/topic/missions", missionDto);
        messagingTemplate.convertAndSend("/topic/missions/" + mission.getId(), missionDto);
        messagingTemplate.convertAndSend("/topic/requests", Map.of(
                "requestId", request.getId(), "status", request.getStatus(), "missionCode", mission.getMissionCode()));
    }

    @Transactional
    public MissionDto.Response confirmDelivery(Long missionId, Long actorUserId, String actorEmail) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("Mission not found with id: " + missionId));

        mission.setStatus("DELIVERY_CONFIRMED");
        mission.setCompletedAt(LocalDateTime.now());
        mission.setUpdatedAt(LocalDateTime.now());
        missionRepository.save(mission);
        auditService.record(actorUserId, actorEmail, "DELIVERY_CONFIRMED", "missions", mission.getId(), "mission=" + mission.getMissionCode());

        if (mission.getRequest() != null) {
            MedicalRequest req = mission.getRequest();
            req.setStatus("DELIVERY_CONFIRMED");
            req.setUpdatedAt(LocalDateTime.now());
            requestRepository.save(req);

            if (mission.getSupplyingHospital() != null && req.getMedicalItem() != null) {
                inventoryService.finalizeDeduction(mission.getSupplyingHospital().getId(), req.getMedicalItem().getId(), req.getQuantity());
            }
        }

        if (mission.getDrone() != null) {
            Drone drone = mission.getDrone();
            drone.setStatus("AVAILABLE");
            drone.setBatteryPct(Math.max(25.0, drone.getBatteryPct() - 15.0));
            drone.setUpdatedAt(LocalDateTime.now());
            droneRepository.save(drone);
        }

        MissionDto.Response resp = toResponseDto(mission);
        messagingTemplate.convertAndSend("/topic/missions", resp);
        messagingTemplate.convertAndSend("/topic/missions/" + mission.getId(), resp);
        messagingTemplate.convertAndSend("/topic/requests", Map.of(
                "requestId", mission.getRequest() != null ? mission.getRequest().getId() : 0,
                "status", "DELIVERY_CONFIRMED"
        ));

        return resp;
    }

    @Transactional
    public MissionDto.Response delayMission(Long missionId, String reason, Long actorUserId, String actorEmail) {
        Mission mission = findMissionForUpdate(missionId);
        requireOperationalMission(mission);
        String detail = requireReason(reason, "A reason is required to delay a mission");
        mission.setStatus("DISPATCH_DELAYED");
        mission.setHubDecisionReason(detail);
        mission.setUpdatedAt(LocalDateTime.now());
        if (mission.getDrone() != null) {
            mission.getDrone().setStatus("RESERVED_FOR_REVIEW");
            mission.getDrone().setUpdatedAt(LocalDateTime.now());
            droneRepository.save(mission.getDrone());
        }
        missionRepository.save(mission);
        MedicalRequest request = mission.getRequest();
        request.setStatus("DISPATCH_DELAYED");
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
        auditService.record(actorUserId, actorEmail, "MISSION_DELAYED", "missions", missionId, detail);
        notifyHub(request, mission, "MISSION_DISPATCH_DELAYED", "Central Hub delayed " + mission.getMissionCode() + ". Reason: " + detail);
        publishMissionAndRequest(mission, request);
        return toResponseDto(mission);
    }

    @Transactional
    public MissionDto.Response abortMission(Long missionId, String reason, Long actorUserId, String actorEmail) {
        Mission mission = findMissionForUpdate(missionId);
        if (!List.of("DISPATCHED", "TRAVELLING_TO_PICKUP", "IN_TRANSIT", "RECOVERY_REQUIRED", "DISPATCH_DELAYED", "CENTRAL_HUB_REVIEW")
                .contains(mission.getStatus())) {
            throw new IllegalStateException("Only a dispatched, delayed, or review mission can be aborted");
        }
        String detail = requireReason(reason, "A reason is required to abort a mission");
        if ("CENTRAL_HUB_REVIEW".equals(mission.getStatus())) {
            releaseReviewDrone(mission);
        } else if (mission.getDrone() != null) {
            mission.getDrone().setStatus("MAINTENANCE");
            mission.getDrone().setUpdatedAt(LocalDateTime.now());
            droneRepository.save(mission.getDrone());
        }
        mission.setStatus("ABORTED");
        mission.setHubDecisionReason(detail);
        mission.setCompletedAt(LocalDateTime.now());
        mission.setUpdatedAt(LocalDateTime.now());
        missionRepository.save(mission);
        MedicalRequest request = mission.getRequest();
        request.setStatus("ABORTED");
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
        if (mission.getSupplyingHospital() != null && request.getMedicalItem() != null && request.getQuantity() != null) {
            inventoryService.releaseReservedStock(mission.getSupplyingHospital().getId(), request.getMedicalItem().getId(), request.getQuantity());
            auditService.record(actorUserId, actorEmail, "INVENTORY_RESERVATION_RELEASED", "hospital_inventory", request.getId(),
                    "mission aborted; quantity=" + request.getQuantity());
        }
        auditService.record(actorUserId, actorEmail, "MISSION_ABORTED", "missions", missionId, detail);
        notifyHub(request, mission, "MISSION_ABORTED", "Central Hub aborted " + mission.getMissionCode() + ". Reason: " + detail);
        publishMissionAndRequest(mission, request);
        return toResponseDto(mission);
    }

    @Transactional
    public MissionDto.Response reassignMission(Long missionId, String reason, Long actorUserId, String actorEmail) {
        Mission mission = findMissionForUpdate(missionId);
        requireOperationalMission(mission);
        String detail = requireReason(reason, "A reason is required to reassign a mission");
        List<Drone> available = droneRepository.findByStatus("AVAILABLE");
        AIDecisionDto.ResponsePayload decision;
        try {
            decision = getApprovedDecision(mission.getRequest(), available);
        } catch (IllegalStateException ex) {
            auditService.record(actorUserId, actorEmail, "SAFETY_REJECTED", "missions", missionId, ex.getMessage());
            throw ex;
        }
        Drone replacement = lockAllocatedDrone(decision, available);
        Drone previous = mission.getDrone();
        if (previous != null) {
            previous.setStatus("MAINTENANCE");
            previous.setUpdatedAt(LocalDateTime.now());
            droneRepository.save(previous);
        }
        replacement.setStatus("RESERVED_FOR_REVIEW");
        replacement.setUpdatedAt(LocalDateTime.now());
        droneRepository.save(replacement);
        mission.setDrone(replacement);
        mission.setStatus("CENTRAL_HUB_REVIEW");
        mission.setHubDecisionReason("Reassigned after anomaly: " + detail);
        mission.setStartedAt(null);
        mission.setCompletedAt(null);
        mission.setUpdatedAt(LocalDateTime.now());
        missionRepository.save(mission);
        Route route = routeRepository.findByMissionId(missionId)
                .orElseGet(() -> new Route(mission, "A*-Airspace", 10.0, 10.0, 20.0));
        route.getWaypoints().clear();
        routeRepository.saveAndFlush(route);
        applyDecisionToRouteAndTrail(mission.getRequest(), mission, route, decision);
        MedicalRequest request = mission.getRequest();
        request.setStatus("CENTRAL_HUB_REVIEW");
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
        auditService.record(actorUserId, actorEmail, "MISSION_REASSIGNED", "missions", missionId,
                "previousDrone=" + (previous == null ? "none" : previous.getDroneCode()) + "; newDrone=" + replacement.getDroneCode() + "; reason=" + detail);
        notifyHub(request, mission, "MISSION_REASSIGNED_FOR_REVIEW", "AI allocated " + replacement.getDroneCode() + " for " + mission.getMissionCode() + "; operator approval is required before dispatch.");
        publishMissionAndRequest(mission, request);
        return toResponseDto(mission);
    }

    private Mission findMission(Long missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new IllegalArgumentException("Mission not found with id: " + missionId));
    }

    private Mission findMissionForUpdate(Long missionId) {
        return missionRepository.findByIdForUpdate(missionId)
                .orElseThrow(() -> new IllegalArgumentException("Mission not found with id: " + missionId));
    }

    private void requireOperationalMission(Mission mission) {
        if (!List.of("DISPATCHED", "TRAVELLING_TO_PICKUP", "IN_TRANSIT", "RECOVERY_REQUIRED").contains(mission.getStatus())) {
            throw new IllegalStateException("This action requires an active airborne mission");
        }
    }

    private String requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException(message);
        return reason.trim();
    }

    public MissionDto.Response toResponseDto(Mission m) {
        MissionDto.Response resp = new MissionDto.Response();
        resp.setId(m.getId());
        resp.setMissionCode(m.getMissionCode());
        if (m.getRequest() != null) {
            resp.setRequestId(m.getRequest().getId());
            resp.setRequestCode(m.getRequest().getRequestCode());
            resp.setMedicalType(m.getRequest().getMedicalType());
            resp.setUrgency(m.getRequest().getUrgency());
        }
        if (m.getRequestingHospital() != null) {
            resp.setRequestingHospitalId(m.getRequestingHospital().getId());
            resp.setRequestingHospitalName(m.getRequestingHospital().getName());
        }
        if (m.getSupplyingHospital() != null) {
            resp.setSupplyingHospitalId(m.getSupplyingHospital().getId());
            resp.setSupplyingHospitalName(m.getSupplyingHospital().getName());
        }
        if (m.getDrone() != null) {
            resp.setDroneId(m.getDrone().getId());
            resp.setDroneCode(m.getDrone().getDroneCode());
            resp.setDroneModel(m.getDrone().getModel());
            resp.setBatteryPct(m.getDrone().getBatteryPct());
        }
        resp.setStatus(m.getStatus());
        resp.setHubDecisionReason(m.getHubDecisionReason());
        resp.setDistanceKm(m.getDistanceKm());
        resp.setEtaMinutes(m.getEtaMinutes());
        resp.setStartedAt(m.getStartedAt());
        resp.setCompletedAt(m.getCompletedAt());

        // Waypoints
        Route route = routeRepository.findByMissionId(m.getId()).orElse(null);
        if (route != null) {
            resp.setBaselineEtaMinutes(route.getBaselineEtaMinutes());
            resp.setMlEtaMinutes(route.getMlEtaMinutes());
            resp.setFinalEtaMinutes(route.getFinalEtaMinutes());
            if (!route.getWaypoints().isEmpty()) {
                resp.setWaypoints(route.getWaypoints().stream().map(wp ->
                        new MissionDto.WaypointDto(wp.getSequenceNo(), wp.getLatitude(), wp.getLongitude(), wp.getAltitudeMeters(), wp.getType())
                ).collect(Collectors.toList()));
            }
        }

        // Decision Trail
        List<AgentDecision> decisions = decisionRepository.findByMissionIdOrderByCreatedAtAsc(m.getId());
        resp.setDecisionTrail(decisions.stream().map(d ->
                new MissionDto.DecisionTrailDto(d.getAgentName(), d.getDecision(), d.getScore(), null, d.getReason())
        ).collect(Collectors.toList()));

        return resp;
    }
}
