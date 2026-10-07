package com.medicaldrone.controller;

import com.medicaldrone.dto.InventoryDto;
import com.medicaldrone.security.UserPrincipal;
import com.medicaldrone.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/hospital/{hospitalId}")
    public ResponseEntity<?> getInventoryByHospital(@PathVariable Long hospitalId,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || (!hasRole(principal, "ROLE_ADMIN") && !hospitalId.equals(principal.getHospitalId()))) {
            return ResponseEntity.status(403).body(Map.of("message", "You can only view inventory for your registered facility"));
        }
        return ResponseEntity.ok(inventoryService.getInventoryByHospital(hospitalId));
    }

    @GetMapping("/item/{medicalItemId}")
    public ResponseEntity<List<InventoryDto>> getHospitalsWithItem(
            @PathVariable Long medicalItemId,
            @RequestParam(required = false, defaultValue = "1") Integer minQuantity) {
        return ResponseEntity.ok(inventoryService.getHospitalsWithItem(medicalItemId, minQuantity));
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateStock(@RequestBody Map<String, Object> payload,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || (!hasRole(principal, "ROLE_ADMIN") && !hasRole(principal, "ROLE_SUPPLYING_HOSPITAL"))) {
            return ResponseEntity.status(403).body(Map.of("message", "A supplier facility account is required to update stock"));
        }
        Long hospitalId = Long.valueOf(payload.get("hospitalId").toString());
        if (!hasRole(principal, "ROLE_ADMIN") && !hospitalId.equals(principal.getHospitalId())) {
            return ResponseEntity.status(403).body(Map.of("message", "You can only update inventory for your registered facility"));
        }
        Long medicalItemId = Long.valueOf(payload.get("medicalItemId").toString());
        Integer availableQty = payload.get("availableQuantity") != null ? Integer.valueOf(payload.get("availableQuantity").toString()) : null;
        Integer reservedQty = payload.get("reservedQuantity") != null ? Integer.valueOf(payload.get("reservedQuantity").toString()) : null;
        LocalDate expiryDate = payload.get("expiryDate") != null && !payload.get("expiryDate").toString().isBlank()
                ? LocalDate.parse(payload.get("expiryDate").toString()) : null;

        if ((availableQty != null && availableQty < 0) || (reservedQty != null && reservedQty < 0)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Inventory quantities cannot be negative"));
        }

        return ResponseEntity.ok(inventoryService.updateStock(hospitalId, medicalItemId, availableQty, reservedQty, expiryDate));
    }

    private boolean hasRole(UserPrincipal principal, String role) {
        return principal.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
