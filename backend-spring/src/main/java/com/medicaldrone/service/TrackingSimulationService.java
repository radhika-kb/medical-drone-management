package com.medicaldrone.service;

import com.medicaldrone.dto.MissionDto;
import com.medicaldrone.model.Drone;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.Mission;
import com.medicaldrone.model.MissionTracking;
import com.medicaldrone.model.Notification;
import com.medicaldrone.model.Route;
import com.medicaldrone.model.RouteWaypoint;
import com.medicaldrone.repository.DroneRepository;
import com.medicaldrone.repository.MedicalRequestRepository;
import com.medicaldrone.repository.MissionRepository;
import com.medicaldrone.repository.MissionTrackingRepository;
import com.medicaldrone.repository.NotificationRepository;
import com.medicaldrone.repository.RouteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TrackingSimulationService {

    private static final Logger logger = LoggerFactory.getLogger(TrackingSimulationService.class);

    private final MissionRepository missionRepository;
    private final RouteRepository routeRepository;
    private final DroneRepository droneRepository;
    private final MedicalRequestRepository requestRepository;
    private final MissionTrackingRepository trackingRepository;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AIServiceClient aiServiceClient;
    private final MissionMonitoringService missionMonitoringService;
    private final AuditService auditService;

    public TrackingSimulationService(MissionRepository missionRepository, RouteRepository routeRepository,
                                     DroneRepository droneRepository, MedicalRequestRepository requestRepository,
                                     MissionTrackingRepository trackingRepository, NotificationRepository notificationRepository,
                                     SimpMessagingTemplate messagingTemplate, AIServiceClient aiServiceClient,
                                     MissionMonitoringService missionMonitoringService,
                                     AuditService auditService) {
        this.missionRepository = missionRepository;
        this.routeRepository = routeRepository;
        this.droneRepository = droneRepository;
        this.requestRepository = requestRepository;
        this.trackingRepository = trackingRepository;
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.aiServiceClient = aiServiceClient;
        this.missionMonitoringService = missionMonitoringService;
        this.auditService = auditService;
    }

    @Async
    public void startMissionSimulation(Long missionId) {
        logger.info("Starting background telemetry simulation for mission ID: {}", missionId);
        try {
            Mission mission = missionRepository.findById(missionId).orElse(null);
            if (mission == null) return;

            Route route = routeRepository.findByMissionId(missionId).orElse(null);
            if (route == null || route.getWaypoints().isEmpty()) return;

            List<RouteWaypoint> waypoints = route.getWaypoints();
            int stepsPerSegment = 5;
            double currentBattery = mission.getDrone().getBatteryPct();

            // Simulate movement through each waypoint segment
            for (int i = 0; i < waypoints.size() - 1; i++) {
                if (!isSimulationActive(missionId)) return;
                RouteWaypoint startWp = waypoints.get(i);
                RouteWaypoint endWp = waypoints.get(i + 1);

                String currentStatus = (i == 0) ? "TRAVELLING_TO_PICKUP" : "IN_TRANSIT";
                updateMissionStatus(mission.getId(), currentStatus);

                for (int s = 1; s <= stepsPerSegment; s++) {
                    Thread.sleep(1500); // 1.5 seconds per tick
                    if (!isSimulationActive(missionId)) return;

                    double ratio = (double) s / stepsPerSegment;
                    double currentLat = startWp.getLatitude() + (endWp.getLatitude() - startWp.getLatitude()) * ratio;
                    double currentLng = startWp.getLongitude() + (endWp.getLongitude() - startWp.getLongitude()) * ratio;
                    currentBattery = Math.max(10.0, currentBattery - 0.5);

                    // Calculate remaining distance to final destination
                    RouteWaypoint finalDest = waypoints.get(waypoints.size() - 1);
                    double remainingKm = haversineKm(currentLat, currentLng, finalDest.getLatitude(), finalDest.getLongitude());
                    double remainingEta = (remainingKm / 60.0) * 60.0;

                    // Save tracking snapshot
                    saveTrackingSnapshot(mission.getId(), currentLat, currentLng, currentBattery, remainingEta, currentStatus);

                    // Broadcast STOMP telemetry update
                    MissionDto.TelemetryUpdate update = new MissionDto.TelemetryUpdate();
                    update.setMissionId(mission.getId());
                    update.setMissionCode(mission.getMissionCode());
                    update.setDroneCode(mission.getDrone().getDroneCode());
                    update.setLat(currentLat);
                    update.setLng(currentLng);
                    update.setBatteryPct(currentBattery);
                    update.setEtaMinutes(remainingEta);
                    update.setRemainingDistanceKm(remainingKm);
                    update.setStatus(currentStatus);

                    messagingTemplate.convertAndSend("/topic/missions/" + mission.getId(), update);
                    messagingTemplate.convertAndSend("/topic/fleet", update);

                    // Periodically invoke AI mission anomaly monitoring
                    if (s == 3) {
                        try {
                            double elapsedMin = (i * stepsPerSegment + s) * 0.5;
                            double distFlown = Math.max(0.1, (mission.getDistanceKm() != null ? mission.getDistanceKm() : 10.0) - remainingKm);
                            java.util.Map<String, Object> mon = aiServiceClient.checkMissionMonitoring(
                                    mission.getId(),
                                    elapsedMin,
                                    distFlown,
                                    mission.getDistanceKm() != null ? mission.getDistanceKm() : 10.0,
                                    mission.getEtaMinutes() != null ? mission.getEtaMinutes() : 10.0,
                                    (100.0 - currentBattery)
                            );
                            boolean modelUnavailable = Boolean.FALSE.equals(mon.get("monitoring_available"));
                            if (modelUnavailable || Boolean.TRUE.equals(mon.get("is_anomaly"))) {
                                double aScore = mon.get("anomaly_score") != null ? Double.parseDouble(mon.get("anomaly_score").toString()) : 1.0;
                                String aType = modelUnavailable ? "MONITORING_UNAVAILABLE" : (String) mon.getOrDefault("anomaly_type", "OPERATIONAL_ANOMALY");
                                String aSev = modelUnavailable ? "CRITICAL" : (String) mon.getOrDefault("severity", "HIGH");
                                String aExpl = (String) mon.getOrDefault("explanation", "Operational anomaly detected; holding for Central Hub review.");
                                missionMonitoringService.recordAnomaly(mission.getId(), aScore, aType, aSev, aExpl);
                                return;
                            }
                        } catch (Exception monEx) {
                            logger.error("Anomaly monitoring failed for mission {}; stopping simulation safely: {}", missionId, monEx.getMessage(), monEx);
                            return;
                        }
                    }
                }
            }

            // Final delivery complete
            completeDelivery(mission.getId(), currentBattery);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Simulation interrupted for mission {}", missionId);
        } catch (Exception ex) {
            logger.error("Simulation error for mission {}: {}", missionId, ex.getMessage(), ex);
        }
    }

    @Transactional
    public void updateMissionStatus(Long missionId, String status) {
        missionRepository.findById(missionId).ifPresent(m -> {
            if (!List.of("DISPATCHED", "TRAVELLING_TO_PICKUP", "IN_TRANSIT").contains(m.getStatus())) return;
            String previousStatus = m.getStatus();
            m.setStatus(status);
            missionRepository.save(m);
            if (!status.equals(previousStatus)) {
                auditService.record(null, "MISSION_MONITOR", "MISSION_STATUS_TRANSITION", "missions", missionId,
                        previousStatus + " -> " + status);
            }
            if (m.getDrone() != null) {
                m.getDrone().setStatus(status);
                droneRepository.save(m.getDrone());
            }
        });
    }

    private boolean isSimulationActive(Long missionId) {
        return missionRepository.findById(missionId)
                .map(mission -> List.of("DISPATCHED", "TRAVELLING_TO_PICKUP", "IN_TRANSIT").contains(mission.getStatus()))
                .orElse(false);
    }

    @Transactional
    public void saveTrackingSnapshot(Long missionId, Double lat, Double lng, Double battery, Double eta, String status) {
        missionRepository.findById(missionId).ifPresent(m -> {
            MissionTracking tracking = new MissionTracking(m, lat, lng, battery, eta, status);
            trackingRepository.save(tracking);

            // Also update live drone position
            if (m.getDrone() != null) {
                m.getDrone().setLatitude(lat);
                m.getDrone().setLongitude(lng);
                m.getDrone().setBatteryPct(battery);
                droneRepository.save(m.getDrone());
            }
        });
    }

    @Transactional
    public void completeDelivery(Long missionId, Double finalBattery) {
        missionRepository.findById(missionId).ifPresent(m -> {
            if (!List.of("DISPATCHED", "TRAVELLING_TO_PICKUP", "IN_TRANSIT").contains(m.getStatus())) return;
            m.setStatus("DELIVERED");
            m.setCompletedAt(LocalDateTime.now());
            missionRepository.save(m);
            auditService.record(null, "MISSION_MONITOR", "MISSION_DELIVERED", "missions", missionId, "simulation completed the planned route");

            MedicalRequest req = m.getRequest();
            if (req != null) {
                req.setStatus("DELIVERED");
                req.setUpdatedAt(LocalDateTime.now());
                requestRepository.save(req);
            }

            Drone drone = m.getDrone();
            if (drone != null) {
                drone.setStatus("AVAILABLE");
                drone.setBatteryPct(finalBattery);
                droneRepository.save(drone);
            }

            Notification notification = new Notification(
                    req != null ? req.getCreatedBy() : null,
                    m,
                    "MISSION_DELIVERED",
                    "Mission " + m.getMissionCode() + " delivered successfully to destination."
            );
            notificationRepository.save(notification);

            // Broadcast delivery completion STOMP message
            MissionDto.TelemetryUpdate finalUpdate = new MissionDto.TelemetryUpdate();
            finalUpdate.setMissionId(m.getId());
            finalUpdate.setMissionCode(m.getMissionCode());
            finalUpdate.setDroneCode(drone != null ? drone.getDroneCode() : null);
            finalUpdate.setLat(req != null ? req.getDestLat() : 12.97);
            finalUpdate.setLng(req != null ? req.getDestLng() : 77.59);
            finalUpdate.setBatteryPct(finalBattery);
            finalUpdate.setEtaMinutes(0.0);
            finalUpdate.setRemainingDistanceKm(0.0);
            finalUpdate.setStatus("DELIVERED");

            messagingTemplate.convertAndSend("/topic/missions/" + m.getId(), finalUpdate);
            messagingTemplate.convertAndSend("/topic/fleet", finalUpdate);
            logger.info("Mission {} DELIVERED successfully!", m.getMissionCode());
        });
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
