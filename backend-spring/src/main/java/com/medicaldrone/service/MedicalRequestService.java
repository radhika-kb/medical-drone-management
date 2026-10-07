package com.medicaldrone.service;

import com.medicaldrone.dto.MedicalRequestDto;
import com.medicaldrone.model.DroneZone;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.model.MedicalItem;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.User;
import com.medicaldrone.repository.MedicalCenterRepository;
import com.medicaldrone.repository.MedicalItemRepository;
import com.medicaldrone.repository.MedicalRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MedicalRequestService {

    private final MedicalRequestRepository requestRepository;
    private final MedicalCenterRepository centerRepository;
    private final MedicalItemRepository itemRepository;
    private final ZoneService zoneService;
    private final AuditService auditService;

    public MedicalRequestService(MedicalRequestRepository requestRepository,
                                 MedicalCenterRepository centerRepository,
                                 MedicalItemRepository itemRepository,
                                 ZoneService zoneService,
                                 AuditService auditService) {
        this.requestRepository = requestRepository;
        this.centerRepository = centerRepository;
        this.itemRepository = itemRepository;
        this.zoneService = zoneService;
        this.auditService = auditService;
    }

    public List<MedicalRequestDto.Response> getAllRequests() {
        return requestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public List<MedicalRequestDto.Response> getRequestsByHospital(Long hospitalId) {
        return requestRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> (r.getRequestingHospital() != null && r.getRequestingHospital().getId().equals(hospitalId))
                          || (r.getCenter() != null && r.getCenter().getId().equals(hospitalId))
                          || (r.getSupplyingHospital() != null && r.getSupplyingHospital().getId().equals(hospitalId)))
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public Optional<MedicalRequest> getRequestEntityById(Long id) {
        return requestRepository.findById(id);
    }

    public Optional<MedicalRequestDto.Response> getRequestById(Long id) {
        return requestRepository.findById(id).map(this::toResponseDto);
    }

    @Transactional
    public MedicalRequestDto.Response createRequest(MedicalRequestDto.Create dto, User user) {
        Long hospitalId = dto.getRequestingHospitalId() != null ? dto.getRequestingHospitalId() : dto.getCenterId();
        MedicalCenter requestingHospital = centerRepository.findById(hospitalId)
                .filter(center -> "APPROVED".equalsIgnoreCase(center.getVerificationStatus()))
                .filter(center -> "ACTIVE".equalsIgnoreCase(center.getOperationalStatus())
                        && "ACTIVE".equalsIgnoreCase(center.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found with id: " + hospitalId));

        MedicalItem medicalItem = null;
        if (dto.getMedicalItemId() != null) {
            medicalItem = itemRepository.findById(dto.getMedicalItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Medical item not found: " + dto.getMedicalItemId()));
        }

        MedicalCenter supplyingHospital = null;
        if (dto.getSupplyingHospitalId() != null) {
            supplyingHospital = centerRepository.findById(dto.getSupplyingHospitalId())
                    .orElseThrow(() -> new IllegalArgumentException("Supplying facility not found: " + dto.getSupplyingHospitalId()));
        }

        Double pickupLat = null;
        Double pickupLng = null;
        double destLat = requestingHospital.getLatitude();
        double destLng = requestingHospital.getLongitude();

        if (supplyingHospital != null) {
            pickupLat = supplyingHospital.getLatitude();
            pickupLng = supplyingHospital.getLongitude();
        }

        DroneZone zone = requestingHospital.getZone();
        if (zone == null && supplyingHospital != null) {
            zone = zoneService.resolveZoneForCoordinates(pickupLat, pickupLng);
        }
        if (zone == null) {
            zone = zoneService.resolveZoneForCoordinates(destLat, destLng);
        }

        String requestCode = "REQ-BLR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MedicalRequest request = new MedicalRequest();
        request.setRequestCode(requestCode);
        request.setMedicalType(medicalItem != null ? medicalItem.getCategory() : (dto.getMedicalType() != null ? dto.getMedicalType().toUpperCase() : "BLOOD"));
        request.setMedicalItem(medicalItem);
        request.setUrgency(dto.getUrgency() != null ? dto.getUrgency().toUpperCase() : "HIGH");
        request.setWeightKg(dto.getWeightKg() != null ? dto.getWeightKg() : 1.0);
        request.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
        request.setRequestingHospital(requestingHospital);
        request.setSupplyingHospital(supplyingHospital);
        request.setCenterId(requestingHospital.getId());
        request.setRequiredBy(dto.getRequiredBy() != null ? dto.getRequiredBy() : LocalDateTime.now().plusHours(2));
        request.setNotes(dto.getNotes());
        request.setPickupLat(pickupLat);
        request.setPickupLng(pickupLng);
        request.setDestLat(destLat);
        request.setDestLng(destLng);
        request.setZone(zone);
        request.setStatus(supplyingHospital != null ? "WAITING_SUPPLIER_RESPONSE" : "PENDING");
        request.setCreatedBy(user);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());

        requestRepository.save(request);
        auditService.record(user.getId(), user.getEmail(), "REQUEST_CREATED", "medical_requests", request.getId(),
                "requestCode=" + request.getRequestCode() + "; urgency=" + request.getUrgency());
        return toResponseDto(request);
    }

    @Transactional
    public MedicalRequestDto.Response updateStatus(Long id, String status) {
        MedicalRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found with id: " + id));
        request.setStatus(status.toUpperCase());
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
        return toResponseDto(request);
    }

    public MedicalRequestDto.Response toResponseDto(MedicalRequest req) {
        MedicalRequestDto.Response dto = new MedicalRequestDto.Response();
        dto.setId(req.getId());
        dto.setRequestCode(req.getRequestCode());
        dto.setMedicalType(req.getMedicalType());
        if (req.getMedicalItem() != null) {
            dto.setMedicalItemId(req.getMedicalItem().getId());
            dto.setMedicalItemName(req.getMedicalItem().getName());
        }
        dto.setUrgency(req.getUrgency());
        dto.setWeightKg(req.getWeightKg());
        dto.setQuantity(req.getQuantity());
        
        MedicalCenter reqHosp = req.getRequestingHospital() != null ? req.getRequestingHospital() : req.getCenter();
        if (reqHosp != null) {
            dto.setCenterId(reqHosp.getId());
            dto.setCenterName(reqHosp.getName());
            dto.setRequestingHospitalId(reqHosp.getId());
            dto.setRequestingHospitalName(reqHosp.getName());
        }

        if (req.getSupplyingHospital() != null) {
            dto.setSupplyingHospitalId(req.getSupplyingHospital().getId());
            dto.setSupplyingHospitalName(req.getSupplyingHospital().getName());
        }

        dto.setRequiredBy(req.getRequiredBy());
        dto.setNotes(req.getNotes());
        dto.setPickupLat(req.getPickupLat());
        dto.setPickupLng(req.getPickupLng());
        dto.setDestLat(req.getDestLat());
        dto.setDestLng(req.getDestLng());
        dto.setZoneId(req.getZone() != null ? req.getZone().getId() : null);
        dto.setZoneName(req.getZone() != null ? req.getZone().getName() : null);
        dto.setStatus(req.getStatus());
        dto.setCreatedAt(req.getCreatedAt());
        dto.setUpdatedAt(req.getUpdatedAt());
        return dto;
    }
}
