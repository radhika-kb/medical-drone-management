package com.medicaldrone.controller;

import com.medicaldrone.dto.MedicalCenterDto;
import com.medicaldrone.service.MedicalCenterService;
import com.medicaldrone.service.GeocodingService;
import com.medicaldrone.service.AuditService;
import com.medicaldrone.security.UserPrincipal;
import com.medicaldrone.dto.GeocodeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/medical-centers", "/api/hospitals"})
@CrossOrigin(origins = "*")
public class MedicalCenterController {

    private final MedicalCenterService centerService;
    private final GeocodingService geocodingService;
    private final AuditService auditService;

    public MedicalCenterController(MedicalCenterService centerService, GeocodingService geocodingService, AuditService auditService) {
        this.centerService = centerService;
        this.geocodingService = geocodingService;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<MedicalCenterDto>> getAllCenters(@RequestParam(required = false) Boolean verifiedOnly) {
        if (Boolean.TRUE.equals(verifiedOnly)) {
            return ResponseEntity.ok(centerService.getVerifiedHospitals());
        }
        return ResponseEntity.ok(centerService.getAllCenters());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicalCenterDto> getCenterById(@PathVariable Long id) {
        return centerService.getCenterById(id)
                .map(centerService::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MedicalCenterDto> createCenter(@RequestBody MedicalCenterDto dto) {
        return ResponseEntity.ok(centerService.createCenter(dto));
    }

    @PostMapping("/geocode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> geocodeAddress(@RequestBody GeocodeRequest request) {
        return ResponseEntity.ok(geocodingService.geocode(request.getAddress()));
    }

    @PutMapping("/{id}/location")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MedicalCenterDto> updateLocation(@PathVariable Long id, @RequestBody MedicalCenterDto dto) {
        return ResponseEntity.ok(centerService.updateLocation(id, dto));
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MedicalCenterDto> updateVerificationStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserPrincipal principal) {
        Object approved = payload.get("approved");
        String status = approved instanceof Boolean value
                ? (value ? "APPROVED" : "REJECTED")
                : payload.getOrDefault("status", "APPROVED").toString();
        MedicalCenterDto updated = centerService.updateVerificationStatus(id, status);
        if ("APPROVED".equalsIgnoreCase(status)) {
            auditService.record(principal.getId(), principal.getEmail(), "ADMIN_APPROVED_HOSPITAL", "hospitals", id,
                    "hospital=" + updated.getName());
        }
        return ResponseEntity.ok(updated);
    }
}
