package com.medicaldrone.controller;

import com.medicaldrone.dto.MissionDto;
import com.medicaldrone.service.MissionService;
import com.medicaldrone.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/missions")
@CrossOrigin(origins = "*")
public class MissionController {

    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL', 'SUPPLYING_HOSPITAL')")
    public ResponseEntity<List<MissionDto.Response>> getAll(@AuthenticationPrincipal UserPrincipal principal) {
        List<MissionDto.Response> missions = missionService.getAllMissions();
        if (isGlobalRole(principal)) return ResponseEntity.ok(missions);
        Long hospitalId = principal.getHospitalId();
        return ResponseEntity.ok(missions.stream()
                .filter(m -> hospitalId != null && (hospitalId.equals(m.getRequestingHospitalId()) || hospitalId.equals(m.getSupplyingHospitalId())))
                .toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL', 'SUPPLYING_HOSPITAL')")
    public ResponseEntity<?> getById(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        var mission = missionService.getMissionById(id);
        if (mission.isEmpty()) return ResponseEntity.notFound().build();
        if (!isGlobalRole(principal) && !belongsToHospital(mission.get(), principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(mission.get());
    }

    @GetMapping("/{id}/tracking")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL', 'SUPPLYING_HOSPITAL')")
    public ResponseEntity<?> getTrackingHistory(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        var mission = missionService.getMissionById(id);
        if (mission.isEmpty()) return ResponseEntity.notFound().build();
        if (!isGlobalRole(principal) && !belongsToHospital(mission.get(), principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(missionService.getMissionTrackingHistory(id));
    }

    @PostMapping("/{id}/confirm-delivery")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL')")
    public ResponseEntity<?> confirmDelivery(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        var mission = missionService.getMissionById(id);
        if (mission.isEmpty()) return ResponseEntity.notFound().build();
        if (!isGlobalRole(principal) && (principal.getHospitalId() == null
                || !principal.getHospitalId().equals(mission.get().getRequestingHospitalId()))) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(missionService.confirmDelivery(id, principal == null ? null : principal.getId(),
                principal == null ? null : principal.getEmail()));
    }

    @PostMapping("/{id}/hub-decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> hubDecision(@PathVariable Long id, @RequestBody Map<String, String> body,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        try {
            return ResponseEntity.ok(missionService.applyHubDecision(id, body.get("action"), body.get("reason"), principal.getId(), principal.getEmail()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> reassign(@PathVariable Long id, @RequestBody Map<String, String> body,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return operationalAction(() -> missionService.reassignMission(id, body.get("reason"), principal.getId(), principal.getEmail()));
    }

    @PostMapping("/{id}/delay")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> delay(@PathVariable Long id, @RequestBody Map<String, String> body,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return operationalAction(() -> missionService.delayMission(id, body.get("reason"), principal.getId(), principal.getEmail()));
    }

    @PostMapping("/{id}/abort")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<?> abort(@PathVariable Long id, @RequestBody Map<String, String> body,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return operationalAction(() -> missionService.abortMission(id, body.get("reason"), principal.getId(), principal.getEmail()));
    }

    private ResponseEntity<?> operationalAction(java.util.function.Supplier<MissionDto.Response> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
        }
    }

    private boolean isGlobalRole(UserPrincipal principal) {
        return principal != null && principal.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_CENTRAL_HUB".equals(authority.getAuthority()));
    }

    private boolean belongsToHospital(MissionDto.Response mission, UserPrincipal principal) {
        Long hospitalId = principal == null ? null : principal.getHospitalId();
        return hospitalId != null && (hospitalId.equals(mission.getRequestingHospitalId()) || hospitalId.equals(mission.getSupplyingHospitalId()));
    }
}
