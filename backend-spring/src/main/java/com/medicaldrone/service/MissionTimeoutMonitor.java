package com.medicaldrone.service;

import com.medicaldrone.repository.MissionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MissionTimeoutMonitor {
    private final MissionRepository missionRepository;
    private final MissionMonitoringService missionMonitoringService;
    private final double timeoutMultiplier;

    public MissionTimeoutMonitor(MissionRepository missionRepository, MissionMonitoringService missionMonitoringService,
                                 @Value("${mission.monitoring.timeout-multiplier:1.5}") double timeoutMultiplier) {
        this.missionRepository = missionRepository;
        this.missionMonitoringService = missionMonitoringService;
        this.timeoutMultiplier = Math.max(1.0, timeoutMultiplier);
    }

    @Scheduled(fixedDelayString = "${mission.monitoring.interval-ms:60000}")
    public void detectOverdueMissions() {
        LocalDateTime now = LocalDateTime.now();
        missionRepository.findByStatusIn(List.of("DISPATCHED", "TRAVELLING_TO_PICKUP", "IN_TRANSIT"))
                .stream()
                .filter(mission -> mission.getStartedAt() != null && mission.getEtaMinutes() != null && mission.getEtaMinutes() > 0)
                .filter(mission -> now.isAfter(mission.getStartedAt().plusSeconds((long) (mission.getEtaMinutes() * 60.0 * timeoutMultiplier))))
                .forEach(mission -> missionMonitoringService.recordAnomaly(mission.getId(), 1.0, "TIMEOUT", "CRITICAL",
                        "Mission exceeded " + timeoutMultiplier + "x its operational ETA without a completed status."));
    }
}
