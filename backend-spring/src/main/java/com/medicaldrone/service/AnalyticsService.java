package com.medicaldrone.service;

import com.medicaldrone.dto.AnalyticsSummaryDto;
import com.medicaldrone.model.Drone;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.Mission;
import com.medicaldrone.repository.DroneRepository;
import com.medicaldrone.repository.DroneZoneRepository;
import com.medicaldrone.repository.MedicalRequestRepository;
import com.medicaldrone.repository.MissionRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private final MedicalRequestRepository requestRepository;
    private final MissionRepository missionRepository;
    private final DroneRepository droneRepository;
    private final DroneZoneRepository zoneRepository;

    public AnalyticsService(MedicalRequestRepository requestRepository, MissionRepository missionRepository,
                            DroneRepository droneRepository, DroneZoneRepository zoneRepository) {
        this.requestRepository = requestRepository;
        this.missionRepository = missionRepository;
        this.droneRepository = droneRepository;
        this.zoneRepository = zoneRepository;
    }

    public AnalyticsSummaryDto getSummary() {
        AnalyticsSummaryDto dto = new AnalyticsSummaryDto();

        List<MedicalRequest> requests = requestRepository.findAll();
        dto.setTotalRequests(requests.size());
        dto.setPendingRequests(requests.stream().filter(r -> "PENDING".equals(r.getStatus())).count());

        List<Mission> missions = missionRepository.findAll();
        dto.setTotalMissions(missions.size());
        dto.setActiveMissions(missions.stream().filter(m -> "DISPATCHED".equals(m.getStatus()) || "IN_TRANSIT".equals(m.getStatus())).count());
        dto.setDeliveredMissions(missions.stream().filter(m -> "DELIVERED".equals(m.getStatus())).count());

        List<Drone> drones = droneRepository.findAll();
        dto.setTotalDrones(drones.size());
        dto.setAvailableDrones(drones.stream().filter(d -> "AVAILABLE".equals(d.getStatus())).count());

        double avgBattery = drones.stream().mapToDouble(Drone::getBatteryPct).average().orElse(100.0);
        dto.setAvgBatteryPct(Math.round(avgBattery * 10.0) / 10.0);

        Map<String, Long> dronesPerZone = new HashMap<>();
        Map<String, Long> missionsPerZone = new HashMap<>();

        for (Drone d : drones) {
            String zName = d.getZone() != null ? d.getZone().getName() : "CENTRAL_HUB";
            dronesPerZone.put(zName, dronesPerZone.getOrDefault(zName, 0L) + 1);
        }

        for (Mission m : missions) {
            String zName = m.getRequest() != null && m.getRequest().getZone() != null
                    ? m.getRequest().getZone().getName() : "CENTRAL_HUB";
            missionsPerZone.put(zName, missionsPerZone.getOrDefault(zName, 0L) + 1);
        }

        dto.setDronesPerZone(dronesPerZone);
        dto.setMissionsPerZone(missionsPerZone);

        return dto;
    }
}
