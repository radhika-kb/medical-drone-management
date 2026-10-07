package com.medicaldrone.controller;

import com.medicaldrone.dto.SupplyResponseDto;
import com.medicaldrone.service.SupplyResponseService;
import com.medicaldrone.repository.MedicalRequestRepository;
import com.medicaldrone.model.MedicalRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.medicaldrone.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/supply-responses")
@CrossOrigin(origins = "*")
public class SupplyResponseController {

    private final SupplyResponseService supplyResponseService;
    private final MedicalRequestRepository medicalRequestRepository;

    public SupplyResponseController(SupplyResponseService supplyResponseService, MedicalRequestRepository medicalRequestRepository) {
        this.supplyResponseService = supplyResponseService;
        this.medicalRequestRepository = medicalRequestRepository;
    }

    @GetMapping("/request/{requestId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL', 'SUPPLYING_HOSPITAL')")
    public ResponseEntity<List<SupplyResponseDto>> getResponsesByRequest(@PathVariable Long requestId,
                                                                          @AuthenticationPrincipal UserPrincipal principal) {
        MedicalRequest request = medicalRequestRepository.findById(requestId).orElse(null);
        if (request == null) return ResponseEntity.notFound().build();
        if (!isGlobalRole(principal) && (principal == null || principal.getHospitalId() == null
                || (!principal.getHospitalId().equals(request.getRequestingHospital() != null ? request.getRequestingHospital().getId() : null)
                && !principal.getHospitalId().equals(request.getSupplyingHospital() != null ? request.getSupplyingHospital().getId() : null)))) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(supplyResponseService.getResponsesByRequest(requestId));
    }

    @GetMapping("/hospital/{hospitalId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'SUPPLYING_HOSPITAL')")
    public ResponseEntity<List<SupplyResponseDto>> getResponsesByHospital(@PathVariable Long hospitalId,
                                                                           @AuthenticationPrincipal UserPrincipal principal) {
        if (!isGlobalRole(principal) && (principal == null || principal.getHospitalId() == null
                || !principal.getHospitalId().equals(hospitalId))) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(supplyResponseService.getResponsesBySupplyingHospital(hospitalId));
    }

    private boolean isGlobalRole(UserPrincipal principal) {
        return principal != null && principal.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_CENTRAL_HUB".equals(authority.getAuthority()));
    }

    @PostMapping("/respond")
    @PreAuthorize("hasRole('SUPPLYING_HOSPITAL')")
    public ResponseEntity<SupplyResponseDto> respondToRequest(@RequestBody Map<String, Object> payload,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        Long requestId = Long.valueOf(payload.get("requestId").toString());
        if (principal == null || principal.getHospitalId() == null || principal.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_SUPPLYING_HOSPITAL".equals(authority.getAuthority()))) {
            return ResponseEntity.status(403).build();
        }
        Long supplyingHospitalId = principal.getHospitalId();
        String decision = payload.get("decision").toString();
        Integer offeredQuantity = payload.get("offeredQuantity") != null ? Integer.valueOf(payload.get("offeredQuantity").toString()) : null;
        String rejectionReason = payload.get("rejectionReason") != null ? payload.get("rejectionReason").toString() : null;

        return ResponseEntity.ok(supplyResponseService.respondToRequest(requestId, supplyingHospitalId, decision, offeredQuantity,
                rejectionReason, principal.getId(), principal.getEmail()));
    }
}
