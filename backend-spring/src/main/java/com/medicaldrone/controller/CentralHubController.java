package com.medicaldrone.controller;

import com.medicaldrone.model.CentralHub;
import com.medicaldrone.repository.CentralHubRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hubs")
public class CentralHubController {
    private final CentralHubRepository centralHubRepository;

    public CentralHubController(CentralHubRepository centralHubRepository) {
        this.centralHubRepository = centralHubRepository;
    }

    @GetMapping
    public ResponseEntity<List<CentralHub>> getAll() {
        return ResponseEntity.ok(centralHubRepository.findAll());
    }
}
