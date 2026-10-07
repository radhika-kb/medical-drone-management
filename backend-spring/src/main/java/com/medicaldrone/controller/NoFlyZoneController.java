package com.medicaldrone.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/no-fly-zones")
public class NoFlyZoneController {

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllNoFlyZones() {
        // Historical circle rows are not authoritative aviation geometry. Keep
        // the table intact, but never expose those records as current airspace.
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkCoordinate(
            @RequestParam Double lat,
            @RequestParam Double lng) {
        Map<String, Object> result = new HashMap<>();
        result.put("violated", null);
        result.put("available", false);
        result.put("message", "No current DigitalSky airspace assessment is available.");
        return ResponseEntity.ok(result);
    }
}
