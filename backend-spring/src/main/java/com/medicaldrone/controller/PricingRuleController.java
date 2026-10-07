package com.medicaldrone.controller;

import com.medicaldrone.dto.PricingRuleDto;
import com.medicaldrone.security.UserPrincipal;
import com.medicaldrone.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pricing-rules")
public class PricingRuleController {

    private final PricingService pricingService;

    public PricingRuleController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PricingRuleDto> getRules() {
        return pricingService.getRules();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PricingRuleDto> createRule(
            @Valid @RequestBody PricingRuleDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(pricingService.createRule(dto, principal.getId(), principal.getUsername()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PricingRuleDto> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody PricingRuleDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(pricingService.updateRule(id, dto, principal.getId(), principal.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateRule(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        pricingService.deactivateRule(id, principal.getId(), principal.getUsername());
        return ResponseEntity.noContent().build();
    }
}
