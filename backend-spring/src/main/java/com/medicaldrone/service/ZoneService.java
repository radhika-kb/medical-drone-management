package com.medicaldrone.service;

import com.medicaldrone.dto.ZoneDto;
import com.medicaldrone.model.DroneZone;
import com.medicaldrone.repository.DroneRepository;
import com.medicaldrone.repository.DroneZoneRepository;
import com.medicaldrone.repository.MissionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ZoneService {

    private final DroneZoneRepository zoneRepository;
    private final DroneRepository droneRepository;
    private final MissionRepository missionRepository;

    public ZoneService(DroneZoneRepository zoneRepository, DroneRepository droneRepository, MissionRepository missionRepository) {
        this.zoneRepository = zoneRepository;
        this.droneRepository = droneRepository;
        this.missionRepository = missionRepository;
    }

    public List<ZoneDto> getAllZones() {
        return zoneRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public Optional<DroneZone> getZoneById(Long id) {
        return zoneRepository.findById(id);
    }

    public Optional<DroneZone> getZoneByName(String name) {
        return zoneRepository.findByName(name);
    }

    public DroneZone resolveZoneForCoordinates(Double lat, Double lng) {
        List<DroneZone> zones = zoneRepository.findAllByActiveTrue();
        if (zones.isEmpty()) {
            return null;
        }

        DroneZone bestZone = zones.get(0);
        double minDistance = Double.MAX_VALUE;

        for (DroneZone z : zones) {
            double dist = haversineKm(lat, lng, z.getCenterLat(), z.getCenterLng());
            if (dist < minDistance) {
                minDistance = dist;
                bestZone = z;
            }
        }
        return bestZone;
    }

    private ZoneDto toDto(DroneZone zone) {
        ZoneDto dto = new ZoneDto();
        dto.setId(zone.getId());
        dto.setName(zone.getName());
        dto.setCode(zone.getCode());
        dto.setHubName(zone.getHub() != null ? zone.getHub().getName() : "Central Hub");
        dto.setCenterLat(zone.getCenterLat());
        dto.setCenterLng(zone.getCenterLng());
        dto.setRadiusKm(zone.getRadiusKm());
        dto.setDescription(zone.getDescription());
        dto.setActive(zone.getActive());

        long dCount = droneRepository.findByZoneId(zone.getId()).size();
        dto.setDroneCount(dCount);

        long mCount = missionRepository.findAll().stream()
                .filter(m -> "DISPATCHED".equals(m.getStatus()) || "IN_TRANSIT".equals(m.getStatus()))
                .filter(m -> m.getRequest() != null && m.getRequest().getZone() != null && m.getRequest().getZone().getId().equals(zone.getId()))
                .count();
        dto.setActiveMissionCount(mCount);

        return dto;
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
