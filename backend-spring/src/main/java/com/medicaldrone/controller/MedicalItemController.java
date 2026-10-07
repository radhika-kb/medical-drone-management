package com.medicaldrone.controller;

import com.medicaldrone.dto.MedicalItemDto;
import com.medicaldrone.service.MedicalItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medical-items")
@CrossOrigin(origins = "*")
public class MedicalItemController {

    private final MedicalItemService medicalItemService;

    public MedicalItemController(MedicalItemService medicalItemService) {
        this.medicalItemService = medicalItemService;
    }

    @GetMapping
    public ResponseEntity<List<MedicalItemDto>> getAllItems(@RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return ResponseEntity.ok(medicalItemService.getItemsByCategory(category));
        }
        return ResponseEntity.ok(medicalItemService.getAllActiveItems());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicalItemDto> getItemById(@PathVariable Long id) {
        return ResponseEntity.ok(medicalItemService.getItemById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MedicalItemDto> createItem(@RequestBody MedicalItemDto dto) {
        return ResponseEntity.ok(medicalItemService.createItem(dto));
    }
}
