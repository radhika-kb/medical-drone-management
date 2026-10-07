package com.medicaldrone.service;

import com.medicaldrone.model.Mission;
import com.medicaldrone.model.MissionAnomaly;
import com.medicaldrone.model.Notification;
import com.medicaldrone.repository.DroneRepository;
import com.medicaldrone.repository.MedicalRequestRepository;
import com.medicaldrone.repository.MissionAnomalyRepository;
import com.medicaldrone.repository.MissionRepository;
import com.medicaldrone.repository.NotificationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Spring-owned safety gate for anomalies produced by the AI monitoring advisor. */
@Service
public class MissionMonitoringService {
    private static final List<String> AIRBORNE = List.of("DISPATCHED", "TRAVELLING_TO_PICKUP", "IN_TRANSIT");

    private final MissionRepository missionRepository;
    private final MissionAnomalyRepository anomalyRepository;
    private final MedicalRequestRepository requestRepository;
    private final DroneRepository droneRepository;
    private final NotificationRepository notificationRepository;
    private final AuditService auditService;
    private final SimpMessagingTemplate messagingTemplate;

    public MissionMonitoringService(MissionRepository missionRepository, MissionAnomalyRepository anomalyRepository,
                                    MedicalRequestRepository requestRepository, DroneRepository droneRepository,
                                    NotificationRepository notificationRepository, AuditService auditService,
                                    SimpMessagingTemplate messagingTemplate) {
        this.missionRepository = missionRepository;
        this.anomalyRepository = anomalyRepository;
        this.requestRepository = requestRepository;
        this.droneRepository = droneRepository;
        this.notificationRepository = notificationRepository;
        this.auditService = auditService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public boolean recordAnomaly(Long missionId, double score, String type, String severity, String explanation) {
        Mission mission = missionRepository.findByIdForUpdate(missionId).orElse(null);
        if (mission == null || !AIRBORNE.contains(mission.getStatus())) return false;

        String safeType = type == null || type.isBlank() ? "OPERATIONAL_ANOMALY" : type;
        String safeSeverity = severity == null || severity.isBlank() ? "HIGH" : severity;
        String detail = explanation == null || explanation.isBlank() ? "Mission monitoring requires operator review." : explanation;
        MissionAnomaly anomaly = anomalyRepository.save(new MissionAnomaly(mission, score, safeType, safeSeverity, detail));
        mission.setStatus("RECOVERY_REQUIRED");
        mission.setHubDecisionReason("Monitoring alert: " + safeType + ". Central Hub review required.");
        mission.setUpdatedAt(LocalDateTime.now());
        missionRepository.save(mission);

        if (mission.getRequest() != null) {
            mission.getRequest().setStatus("RECOVERY_REQUIRED");
            mission.getRequest().setUpdatedAt(LocalDateTime.now());
            requestRepository.save(mission.getRequest());
        }
        if (mission.getDrone() != null) {
            mission.getDrone().setStatus("RECOVERY_REQUIRED");
            mission.getDrone().setUpdatedAt(LocalDateTime.now());
            droneRepository.save(mission.getDrone());
        }

        String recommendation = recommendedAction(safeType, safeSeverity);
        auditService.record(null, "MISSION_MONITOR", "MISSION_ANOMALY_DETECTED", "missions", missionId,
                "type=" + safeType + "; severity=" + safeSeverity + "; score=" + score);
        auditService.record(null, "MISSION_MONITOR", "MISSION_RECOVERY_REQUIRED", "missions", missionId,
                "type=" + safeType + "; recommendation=" + recommendation);
        notificationRepository.save(new Notification(mission.getRequest() == null ? null : mission.getRequest().getCreatedBy(),
                mission, "MISSION_RECOVERY_REQUIRED", "Mission " + mission.getMissionCode()
                + " is held for Central Hub review. " + detail));

        Map<String, Object> alert = Map.ofEntries(
                Map.entry("type", "ANOMALY_DETECTED"), Map.entry("id", anomaly.getId() == null ? 0L : anomaly.getId()),
                Map.entry("missionId", missionId), Map.entry("missionCode", mission.getMissionCode() == null ? "" : mission.getMissionCode()),
                Map.entry("anomalyType", safeType), Map.entry("severity", safeSeverity), Map.entry("explanation", detail),
                Map.entry("anomalyScore", score), Map.entry("missionStatus", "RECOVERY_REQUIRED"), Map.entry("recoveryRequired", true),
                Map.entry("recommendedAction", recommendation), Map.entry("detectedAt", anomaly.getDetectedAt().toString()));
        messagingTemplate.convertAndSend("/topic/alerts", alert);
        messagingTemplate.convertAndSend("/topic/missions/" + missionId + "/alerts", alert);
        messagingTemplate.convertAndSend("/topic/missions", Map.of("id", missionId,
                "missionCode", mission.getMissionCode() == null ? "" : mission.getMissionCode(),
                "status", "RECOVERY_REQUIRED"));
        if (mission.getRequest() != null) {
            messagingTemplate.convertAndSend("/topic/requests", Map.of(
                    "requestId", mission.getRequest().getId(), "status", "RECOVERY_REQUIRED"));
        }
        return true;
    }

    private String recommendedAction(String type, String severity) {
        String normalized = type.toUpperCase(java.util.Locale.ROOT);
        if ("CRITICAL".equalsIgnoreCase(severity) || normalized.contains("BATTERY") || normalized.contains("TIMEOUT")) return "ABORT";
        return "HIGH".equalsIgnoreCase(severity) ? "REASSIGN" : "DELAY";
    }
}
