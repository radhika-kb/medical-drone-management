package com.medicaldrone.controller;

import com.medicaldrone.dto.ZoneDto;
import com.medicaldrone.service.ZoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/zones")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @GetMapping
    public ResponseEntity<List<ZoneDto>> getAllZones() {
        return ResponseEntity.ok(zoneService.getAllZones());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getZoneById(@PathVariable Long id) {
        return zoneService.getZoneById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/resolve")
    public ResponseEntity<?> resolveZone(@RequestParam Double lat, @RequestParam Double lng) {
        var zone = zoneService.resolveZoneForCoordinates(lat, lng);
        if (zone == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(java.util.Map.of("id", zone.getId(), "name", zone.getName()));
    }
}
