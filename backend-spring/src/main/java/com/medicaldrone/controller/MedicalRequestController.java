package com.medicaldrone.controller;

import com.medicaldrone.dto.MedicalRequestDto;
import com.medicaldrone.dto.MissionDto;
import com.medicaldrone.model.User;
import com.medicaldrone.repository.UserRepository;
import com.medicaldrone.security.UserPrincipal;
import com.medicaldrone.service.MedicalRequestService;
import com.medicaldrone.service.MissionService;
import com.medicaldrone.service.InventoryService;
import com.medicaldrone.service.SupplierMatchingService;
import com.medicaldrone.repository.SupplyResponseRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class MedicalRequestController {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRequestController.class);

    private final MedicalRequestService requestService;
    private final MissionService missionService;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final SupplierMatchingService supplierMatchingService;
    private final SupplyResponseRepository supplyResponseRepository;

    public MedicalRequestController(MedicalRequestService requestService,
                                    MissionService missionService,
                                    UserRepository userRepository,
                                    InventoryService inventoryService,
                                    SupplierMatchingService supplierMatchingService,
                                    SupplyResponseRepository supplyResponseRepository) {
        this.requestService = requestService;
        this.missionService = missionService;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.supplierMatchingService = supplierMatchingService;
        this.supplyResponseRepository = supplyResponseRepository;
    }

    @GetMapping({"/api/medical-requests", "/api/requests"})
    public ResponseEntity<List<MedicalRequestDto.Response>> listAll(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal != null && userPrincipal.getHospitalId() != null) {
            if (!isGlobalRole(userPrincipal)) {
                return ResponseEntity.ok(requestService.getRequestsByHospital(userPrincipal.getHospitalId()));
            }
        }
        if (userPrincipal == null || (!isGlobalRole(userPrincipal) && userPrincipal.getHospitalId() == null)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(requestService.getAllRequests());
    }

    @GetMapping({"/api/medical-requests/hospital/{hospitalId}", "/api/requests/hospital/{hospitalId}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL', 'SUPPLYING_HOSPITAL')")
    public ResponseEntity<List<MedicalRequestDto.Response>> getByHospital(@PathVariable Long hospitalId,
                                                                          @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || (!isGlobalRole(principal) && !hospitalId.equals(principal.getHospitalId()))) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(requestService.getRequestsByHospital(hospitalId));
    }

    @PostMapping({"/api/medical-requests", "/api/requests"})
    public ResponseEntity<?> create(@Valid @RequestBody MedicalRequestDto.Create dto,
                                    @AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(403).body(Map.of("message", "Sign in to create a medical request"));
        }
        boolean isAdmin = userPrincipal.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean isRequester = userPrincipal.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_REQUESTING_HOSPITAL".equals(authority.getAuthority()));
        if (!isAdmin && (!isRequester || userPrincipal.getHospitalId() == null)) {
            return ResponseEntity.status(403).body(Map.of("message", "A requesting medical center account is required"));
        }

        Long requestingHospitalId = dto.getRequestingHospitalId();
        if (requestingHospitalId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Choose the requesting hospital"));
        }
        if (!isAdmin && !requestingHospitalId.equals(userPrincipal.getHospitalId())) {
            return ResponseEntity.status(403).body(Map.of("message", "You can only create requests for your registered medical center"));
        }

        User user = userRepository.findById(userPrincipal.getId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "The signed-in account could not be found"));
        }
        if (dto.getMedicalItemId() == null || dto.getQuantity() == null || dto.getQuantity() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Choose a medical item and enter a positive quantity"));
        }
        if (dto.getSupplyingHospitalId() != null) {
            boolean eligibleSupplier = inventoryService.getHospitalsWithItem(dto.getMedicalItemId(), dto.getQuantity()).stream()
                    .anyMatch(inventory -> dto.getSupplyingHospitalId().equals(inventory.getHospitalId())
                            && !requestingHospitalId.equals(inventory.getHospitalId()));
            if (!eligibleSupplier) {
                return ResponseEntity.badRequest().body(Map.of("message", "The selected facility does not have enough eligible inventory for this request"));
            }
        }
        try {
            MedicalRequestDto.Response created = requestService.createRequest(dto, user);
            supplierMatchingService.startMatching(created.getId());
            return ResponseEntity.ok(requestService.getRequestById(created.getId()).orElse(created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", ex.getMessage() != null ? ex.getMessage() : "The request contains invalid data"
            ));
        } catch (DataIntegrityViolationException ex) {
            logger.error("Database constraint prevented medical request creation", ex);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "The database rejected this request: " + getRootCauseMessage(ex)
            ));
        } catch (Exception ex) {
            logger.error("Unexpected error while creating medical request", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "message", "The backend failed while saving this request: " + getRootCauseMessage(ex)
            ));
        }
    }

    private String getRootCauseMessage(Throwable error) {
        Throwable rootCause = error;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            return "no additional error details were provided";
        }
        String singleLineMessage = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return singleLineMessage.length() <= 240 ? singleLineMessage : singleLineMessage.substring(0, 240) + "…";
    }

    @GetMapping("/api/medical-requests/supplier/inbox")
    public ResponseEntity<?> supplierInbox(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getHospitalId() == null || principal.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_SUPPLYING_HOSPITAL".equals(authority.getAuthority()))) {
            return ResponseEntity.status(403).body(Map.of("message", "A registered medical center account is required"));
        }
        var inbox = supplyResponseRepository
                .findBySupplyingHospitalIdAndResponseOrderByCreatedAtAsc(principal.getHospitalId(), "PENDING")
                .stream().map(invitation -> requestService.toResponseDto(invitation.getRequest())).toList();
        return ResponseEntity.ok(inbox);
    }

    @PostMapping({"/api/medical-requests/{id}/rematch", "/api/requests/{id}/rematch"})
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL')")
    public ResponseEntity<?> rematch(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        var request = requestService.getRequestEntityById(id).orElse(null);
        if (request == null) return ResponseEntity.notFound().build();
        boolean global = isGlobalRole(principal);
        if (!global && (principal.getHospitalId() == null || request.getRequestingHospital() == null
                || !principal.getHospitalId().equals(request.getRequestingHospital().getId()))) {
            return ResponseEntity.status(403).build();
        }
        supplierMatchingService.rematch(id);
        return ResponseEntity.ok(requestService.getRequestById(id).orElseThrow());
    }

    @GetMapping({"/api/medical-requests/{id}", "/api/requests/{id}"})
    public ResponseEntity<?> getById(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        var request = requestService.getRequestById(id);
        if (request.isEmpty()) return ResponseEntity.notFound().build();
        var row = request.get();
        Long hospitalId = principal.getHospitalId();
        if (!isGlobalRole(principal) && (hospitalId == null || (!hospitalId.equals(row.getRequestingHospitalId())
                && !hospitalId.equals(row.getSupplyingHospitalId())))) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(row);
    }

    @PutMapping({"/api/medical-requests/{id}/status", "/api/requests/{id}/status"})
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "PENDING");
        return ResponseEntity.ok(requestService.updateStatus(id, status));
    }

    @PostMapping({"/api/medical-requests/{id}/review", "/api/requests/{id}/review",
            "/api/medical-requests/{id}/dispatch", "/api/requests/{id}/dispatch"})
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> prepareHubReview(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        try {
            MissionDto.Response missionResp = missionService.prepareHubReview(id, principal.getId(), principal.getEmail());
            return ResponseEntity.ok(Map.of(
                    "status", "CENTRAL_HUB_REVIEW",
                    "message", "AI recommendation is ready for Central Hub review. No drone has been dispatched.",
                    "mission", missionResp,
                    "decision_trail", missionResp.getDecisionTrail() != null ? missionResp.getDecisionTrail() : List.of()
            ));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "REVIEW_PREPARATION_FAILED",
                    "message", ex.getMessage()
            ));
        }
    }

    private boolean isGlobalRole(UserPrincipal principal) {
        return principal.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_CENTRAL_HUB".equals(authority.getAuthority()));
    }
}
