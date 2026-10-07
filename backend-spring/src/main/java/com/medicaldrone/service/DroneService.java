package com.medicaldrone.service;

import com.medicaldrone.dto.DroneDto;
import com.medicaldrone.model.Drone;
import com.medicaldrone.repository.DroneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DroneService {

    private final DroneRepository droneRepository;

    public DroneService(DroneRepository droneRepository) {
        this.droneRepository = droneRepository;
    }

    public List<DroneDto> getAllDrones() {
        return droneRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<DroneDto> getDronesByZone(Long zoneId) {
        return droneRepository.findByZoneId(zoneId).stream().map(this::toDto).collect(Collectors.toList());
    }

    public Optional<DroneDto> getDroneById(Long id) {
        return droneRepository.findById(id).map(this::toDto);
    }

    @Transactional
    public DroneDto updateDroneStatus(Long id, String status) {
        Drone drone = droneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Drone not found with id: " + id));
        drone.setStatus(status);
        drone.setUpdatedAt(LocalDateTime.now());
        droneRepository.save(drone);
        return toDto(drone);
    }

    public DroneDto toDto(Drone d) {
        return new DroneDto(
                d.getId(),
                d.getDroneCode(),
                d.getModel(),
                d.getZone() != null ? d.getZone().getId() : null,
                d.getZone() != null ? d.getZone().getName() : "CENTRAL_HUB",
                d.getBatteryPct(),
                d.getMaxPayloadKg(),
                d.getMaxRangeKm(),
                d.getLatitude(),
                d.getLongitude(),
                d.getStatus()
        );
    }
}
