package com.medicaldrone.service;

import com.medicaldrone.dto.MedicalCenterDto;
import com.medicaldrone.model.DroneZone;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.repository.MedicalCenterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MedicalCenterService {

    private final MedicalCenterRepository centerRepository;
    private final ZoneService zoneService;

    public MedicalCenterService(MedicalCenterRepository centerRepository, ZoneService zoneService) {
        this.centerRepository = centerRepository;
        this.zoneService = zoneService;
    }

    public List<MedicalCenterDto> getAllCenters() {
        return centerRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<MedicalCenterDto> getVerifiedHospitals() {
        return centerRepository.findAll().stream()
                .filter(c -> "APPROVED".equalsIgnoreCase(c.getVerificationStatus()))
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getOperationalStatus()) && "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null && c.getZone() != null)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Optional<MedicalCenter> getCenterById(Long id) {
        return centerRepository.findById(id);
    }

    @Transactional
    public MedicalCenterDto createCenter(MedicalCenterDto dto) {
        if (!dto.isLocationConfirmed() || !validBengaluruCoordinates(dto.getLatitude(), dto.getLongitude())) {
            throw new IllegalArgumentException("A confirmed Bengaluru map pin is required before saving the hospital");
        }
        DroneZone zone = zoneService.resolveZoneForCoordinates(dto.getLatitude(), dto.getLongitude());
        if (zone == null) throw new IllegalStateException("No active operational zone is configured for this location");

        MedicalCenter center = new MedicalCenter(
                dto.getName(),
                dto.getType() != null ? dto.getType() : "HOSPITAL",
                dto.getAddress(),
                dto.getLatitude(),
                dto.getLongitude(),
                zone,
                dto.getContactNumber()
        );
        center.setVerificationStatus("PENDING");
        center.setOperationalStatus("INACTIVE");
        center.setStatus("INACTIVE");
        centerRepository.save(center);
        return toDto(center);
    }

    @Transactional
    public MedicalCenterDto updateLocation(Long hospitalId, MedicalCenterDto dto) {
        if (!dto.isLocationConfirmed() || !validBengaluruCoordinates(dto.getLatitude(), dto.getLongitude())) {
            throw new IllegalArgumentException("A confirmed Bengaluru map pin is required before saving the hospital");
        }
        MedicalCenter center = centerRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found: " + hospitalId));
        DroneZone zone = zoneService.resolveZoneForCoordinates(dto.getLatitude(), dto.getLongitude());
        if (zone == null) throw new IllegalStateException("No active operational zone is configured for this location");
        center.setLatitude(dto.getLatitude());
        center.setLongitude(dto.getLongitude());
        center.setAddress(dto.getAddress());
        center.setZone(zone);
        center.setVerificationStatus("PENDING");
        center.setOperationalStatus("INACTIVE");
        center.setStatus("INACTIVE");
        center.setUpdatedAt(LocalDateTime.now());
        return toDto(centerRepository.save(center));
    }

    private boolean validBengaluruCoordinates(Double lat, Double lng) {
        return lat != null && lng != null && lat >= 12.7 && lat <= 13.3 && lng >= 77.3 && lng <= 78.0;
    }

    @Transactional
    public MedicalCenterDto updateVerificationStatus(Long hospitalId, String status) {
        MedicalCenter center = centerRepository.findById(hospitalId)
                .orElseThrow(() -> new RuntimeException("Hospital not found: " + hospitalId));
        if ("APPROVED".equalsIgnoreCase(status)
                && (center.getLatitude() == null || center.getLongitude() == null || center.getZone() == null)) {
            throw new IllegalStateException("Confirm the hospital location and zone before approval");
        }
        center.setVerificationStatus(status.toUpperCase());
        if ("APPROVED".equalsIgnoreCase(status)) {
            center.setOperationalStatus("ACTIVE");
            center.setStatus("ACTIVE");
        } else if ("REJECTED".equalsIgnoreCase(status) || "SUSPENDED".equalsIgnoreCase(status)) {
            center.setOperationalStatus("INACTIVE");
            center.setStatus("INACTIVE");
        } else if (!"PENDING".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Verification status must be APPROVED, REJECTED, SUSPENDED, or PENDING");
        }
        center.setUpdatedAt(LocalDateTime.now());
        MedicalCenter saved = centerRepository.save(center);
        return toDto(saved);
    }

    public MedicalCenterDto toDto(MedicalCenter c) {
        MedicalCenterDto dto = new MedicalCenterDto();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setType(c.getType());
        dto.setAddress(c.getAddress());
        dto.setLatitude(c.getLatitude());
        dto.setLongitude(c.getLongitude());
        dto.setZoneId(c.getZone() != null ? c.getZone().getId() : null);
        dto.setZoneName(c.getZone() != null ? c.getZone().getName() : null);
        dto.setContactNumber(c.getContactNumber());
        dto.setVerificationStatus(c.getVerificationStatus());
        dto.setOperationalStatus(c.getOperationalStatus());
        dto.setStatus(c.getStatus());
        dto.setLocationConfirmed("APPROVED".equalsIgnoreCase(c.getVerificationStatus())
                && c.getLatitude() != null && c.getLongitude() != null);
        return dto;
    }
}
