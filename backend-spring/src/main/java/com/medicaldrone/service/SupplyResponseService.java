package com.medicaldrone.service;

import com.medicaldrone.dto.SupplyResponseDto;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.SupplyResponse;
import com.medicaldrone.repository.MedicalCenterRepository;
import com.medicaldrone.repository.MedicalRequestRepository;
import com.medicaldrone.repository.SupplyResponseRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupplyResponseService {

    private final SupplyResponseRepository supplyResponseRepository;
    private final MedicalRequestRepository medicalRequestRepository;
    private final MedicalCenterRepository medicalCenterRepository;
    private final InventoryService inventoryService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SupplierMatchingService supplierMatchingService;
    private final AuditService auditService;

    public SupplyResponseService(SupplyResponseRepository supplyResponseRepository,
                                 MedicalRequestRepository medicalRequestRepository,
                                 MedicalCenterRepository medicalCenterRepository,
                                 InventoryService inventoryService,
                                 SimpMessagingTemplate messagingTemplate,
                                 SupplierMatchingService supplierMatchingService,
                                 AuditService auditService) {
        this.supplyResponseRepository = supplyResponseRepository;
        this.medicalRequestRepository = medicalRequestRepository;
        this.medicalCenterRepository = medicalCenterRepository;
        this.inventoryService = inventoryService;
        this.messagingTemplate = messagingTemplate;
        this.supplierMatchingService = supplierMatchingService;
        this.auditService = auditService;
    }

    public List<SupplyResponseDto> getResponsesByRequest(Long requestId) {
        return supplyResponseRepository.findByRequestId(requestId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<SupplyResponseDto> getResponsesBySupplyingHospital(Long hospitalId) {
        return supplyResponseRepository.findBySupplyingHospitalId(hospitalId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SupplyResponseDto respondToRequest(Long requestId, Long supplyingHospitalId, String decision, Integer offeredQuantity, String rejectionReason,
                                               Long actorUserId, String actorEmail) {
        MedicalRequest request = medicalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Medical request not found: " + requestId));
        
        MedicalCenter supplyingHospital = medicalCenterRepository.findById(supplyingHospitalId)
                .orElseThrow(() -> new RuntimeException("Supplying hospital not found: " + supplyingHospitalId));

        if (!"APPROVED".equalsIgnoreCase(supplyingHospital.getVerificationStatus())
                || !"ACTIVE".equalsIgnoreCase(supplyingHospital.getOperationalStatus())
                || !"ACTIVE".equalsIgnoreCase(supplyingHospital.getStatus())
                || request.getRequestingHospital() == null
                || request.getRequestingHospital().getId().equals(supplyingHospitalId)) {
            throw new IllegalStateException("This medical center is not eligible to supply this request");
        }
        if (!"ACCEPTED".equalsIgnoreCase(decision) && !"REJECTED".equalsIgnoreCase(decision)) {
            throw new IllegalArgumentException("Supplier response must be ACCEPTED or REJECTED");
        }

        int qty = (offeredQuantity != null && offeredQuantity > 0) ? offeredQuantity : request.getQuantity();
        if (qty != request.getQuantity()) {
            throw new IllegalArgumentException("The current workflow accepts the full requested quantity only");
        }
        SupplyResponse supplyResponse = supplyResponseRepository.findByRequestIdAndSupplyingHospitalId(requestId, supplyingHospitalId)
                .orElseThrow(() -> new IllegalStateException("This supplier has not been invited to respond"));
        if (!"PENDING".equalsIgnoreCase(supplyResponse.getResponse())
                || !"WAITING_SUPPLIER_RESPONSE".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalStateException("This supplier invitation is no longer active");
        }

        if ("ACCEPTED".equalsIgnoreCase(decision) && (supplyingHospital.getLatitude() == null || supplyingHospital.getLongitude() == null
                || request.getRequestingHospital().getLatitude() == null || request.getRequestingHospital().getLongitude() == null)) {
            throw new IllegalStateException("Both facilities need confirmed map locations before accepting this request");
        }

        if ("ACCEPTED".equalsIgnoreCase(decision) && request.getMedicalItem() != null) {
            boolean hasEligibleStock = inventoryService.getHospitalsWithItem(request.getMedicalItem().getId(), qty).stream()
                    .anyMatch(item -> supplyingHospitalId.equals(item.getHospitalId()));
            if (!hasEligibleStock) {
                decision = "REJECTED";
                rejectionReason = "Stock was no longer eligible or available when the invitation was accepted.";
            }
        }

        if ("ACCEPTED".equalsIgnoreCase(decision) && request.getMedicalItem() != null
                && !inventoryService.reserveStock(supplyingHospitalId, request.getMedicalItem().getId(), qty)) {
            decision = "REJECTED";
            rejectionReason = "Stock changed before reservation; the next ranked supplier is being contacted.";
        }

        supplyResponse.setResponse(decision.toUpperCase());
        supplyResponse.setOfferedQuantity(qty);
        supplyResponse.setRejectionReason(rejectionReason);
        supplyResponse.setRespondedAt(LocalDateTime.now());

        SupplyResponse saved = supplyResponseRepository.save(supplyResponse);
        String auditAction = "ACCEPTED".equalsIgnoreCase(decision) ? "SUPPLIER_ACCEPTED" : "SUPPLIER_REJECTED";
        auditService.record(actorUserId, actorEmail, auditAction, "supply_responses", saved.getId(),
                "request=" + request.getRequestCode() + "; supplier=" + supplyingHospital.getName()
                        + (rejectionReason == null ? "" : "; reason=" + rejectionReason));

        if ("ACCEPTED".equalsIgnoreCase(decision)) {
            auditService.record(actorUserId, actorEmail, "INVENTORY_RESERVED", "hospital_inventory", request.getId(),
                    "hospital=" + supplyingHospital.getName() + "; item=" + (request.getMedicalItem() == null ? "unknown" : request.getMedicalItem().getId()) + "; quantity=" + qty);
            request.setSupplyingHospital(supplyingHospital);
            request.setPickupLat(supplyingHospital.getLatitude());
            request.setPickupLng(supplyingHospital.getLongitude());
            request.setDestLat(request.getRequestingHospital().getLatitude());
            request.setDestLng(request.getRequestingHospital().getLongitude());
            request.setStatus("SUPPLIER_ACCEPTED");
            request.setUpdatedAt(LocalDateTime.now());
            medicalRequestRepository.save(request);

            supplyResponseRepository.findByRequestIdOrderByIdAsc(requestId).stream()
                    .filter(row -> "QUEUED".equalsIgnoreCase(row.getResponse()))
                    .forEach(row -> row.setResponse("SKIPPED"));
            supplyResponseRepository.flush();

            // Publish WebSocket event
            messagingTemplate.convertAndSend("/topic/requests/" + request.getId(), toDto(saved));
            messagingTemplate.convertAndSend("/topic/requests", toDto(saved));
        } else {
            request.setSupplyingHospital(null);
            request.setStatus("SUPPLIER_REJECTED");
            request.setUpdatedAt(LocalDateTime.now());
            medicalRequestRepository.save(request);

            messagingTemplate.convertAndSend("/topic/requests/" + request.getId(), toDto(saved));
            supplierMatchingService.inviteNextSupplier(request);
        }

        return toDto(saved);
    }

    public SupplyResponseDto toDto(SupplyResponse sr) {
        SupplyResponseDto dto = new SupplyResponseDto();
        dto.setId(sr.getId());
        dto.setRequestId(sr.getRequest().getId());
        dto.setRequestCode(sr.getRequest().getRequestCode());
        dto.setSupplyingHospitalId(sr.getSupplyingHospital().getId());
        dto.setSupplyingHospitalName(sr.getSupplyingHospital().getName());
        dto.setOfferedQuantity(sr.getOfferedQuantity());
        dto.setResponse(sr.getResponse());
        dto.setRejectionReason(sr.getRejectionReason());
        dto.setRespondedAt(sr.getRespondedAt());
        return dto;
    }
}
