package com.medicaldrone.service;

import com.medicaldrone.dto.InventoryDto;
import com.medicaldrone.model.HospitalInventory;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.model.MedicalItem;
import com.medicaldrone.repository.HospitalInventoryRepository;
import com.medicaldrone.repository.MedicalCenterRepository;
import com.medicaldrone.repository.MedicalItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final HospitalInventoryRepository inventoryRepository;
    private final MedicalCenterRepository medicalCenterRepository;
    private final MedicalItemRepository medicalItemRepository;
    private final RedisLockService redisLockService;

    public InventoryService(HospitalInventoryRepository inventoryRepository,
                            MedicalCenterRepository medicalCenterRepository,
                            MedicalItemRepository medicalItemRepository,
                            RedisLockService redisLockService) {
        this.inventoryRepository = inventoryRepository;
        this.medicalCenterRepository = medicalCenterRepository;
        this.medicalItemRepository = medicalItemRepository;
        this.redisLockService = redisLockService;
    }

    public List<InventoryDto> getInventoryByHospital(Long hospitalId) {
        return inventoryRepository.findByHospitalId(hospitalId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<InventoryDto> getHospitalsWithItem(Long medicalItemId, Integer minQuantity) {
        int minQty = minQuantity != null ? minQuantity : 1;
        return inventoryRepository.findAvailableByItemAndQuantity(medicalItemId, minQty).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public InventoryDto updateStock(Long hospitalId, Long medicalItemId, Integer availableQty, Integer reservedQty, LocalDate expiryDate) {
        HospitalInventory inv = inventoryRepository.findByHospitalIdAndMedicalItemIdForUpdate(hospitalId, medicalItemId)
                .orElseGet(() -> {
                    MedicalCenter hospital = medicalCenterRepository.findById(hospitalId)
                            .orElseThrow(() -> new RuntimeException("Hospital not found: " + hospitalId));
                    MedicalItem item = medicalItemRepository.findById(medicalItemId)
                            .orElseThrow(() -> new RuntimeException("Medical item not found: " + medicalItemId));
                    return new HospitalInventory(hospital, item, 0, 0, null);
                });

        MedicalCenter hospital = inv.getHospital();
        if (!"APPROVED".equalsIgnoreCase(hospital.getVerificationStatus())
                || !"ACTIVE".equalsIgnoreCase(hospital.getOperationalStatus())
                || !"ACTIVE".equalsIgnoreCase(hospital.getStatus())) {
            throw new IllegalStateException("Only an approved, active facility can offer inventory to the network");
        }

        if (availableQty != null) {
            inv.setAvailableQuantity(availableQty);
        }
        if (reservedQty != null) {
            inv.setReservedQuantity(reservedQty);
        }
        if (expiryDate != null) {
            inv.setExpiryDate(expiryDate);
        }
        String category = inv.getMedicalItem().getCategory();
        boolean expiryTracked = java.util.Set.of("BLOOD", "VACCINE", "ANTIVENOM", "EMERGENCY_MEDICINE").contains(category);
        if (expiryTracked && inv.getAvailableQuantity() > 0 && inv.getExpiryDate() == null) {
            throw new IllegalArgumentException("An expiry date is required for available " + category + " inventory");
        }
        if (inv.getAvailableQuantity() > 0 && inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Expired stock cannot be offered as available inventory");
        }
        inv.setUpdatedAt(LocalDateTime.now());
        inv.setStatus(inv.getAvailableQuantity() <= 5 ? "LOW_STOCK" : "AVAILABLE");
        
        HospitalInventory saved = inventoryRepository.save(inv);
        return toDto(saved);
    }

    @Transactional
    public boolean reserveStock(Long hospitalId, Long medicalItemId, Integer quantity) {
        requirePositiveQuantity(quantity);
        String lockKey = "lock:inventory:" + hospitalId + ":" + medicalItemId;
        String lockVal = java.util.UUID.randomUUID().toString();
        boolean acquired = redisLockService.acquireLock(lockKey, lockVal, 5);
        if (!acquired) {
            throw new IllegalStateException("Could not acquire lock for inventory reservation. Please retry.");
        }

        try {
            HospitalInventory inv = inventoryRepository.findByHospitalIdAndMedicalItemIdForUpdate(hospitalId, medicalItemId)
                    .orElseThrow(() -> new RuntimeException("Item not found in hospital inventory"));

            if (inv.getAvailableQuantity() < quantity) {
                return false;
            }

            inv.setAvailableQuantity(inv.getAvailableQuantity() - quantity);
            inv.setReservedQuantity(inv.getReservedQuantity() + quantity);
            inv.setUpdatedAt(LocalDateTime.now());
            inventoryRepository.save(inv);
            return true;
        } finally {
            redisLockService.releaseLock(lockKey, lockVal);
        }
    }

    @Transactional
    public void releaseReservedStock(Long hospitalId, Long medicalItemId, Integer quantity) {
        requirePositiveQuantity(quantity);
        inventoryRepository.findByHospitalIdAndMedicalItemIdForUpdate(hospitalId, medicalItemId).ifPresent(inv -> {
            int released = Math.min(quantity, inv.getReservedQuantity());
            inv.setReservedQuantity(inv.getReservedQuantity() - released);
            inv.setAvailableQuantity(inv.getAvailableQuantity() + released);
            inv.setUpdatedAt(LocalDateTime.now());
            inventoryRepository.save(inv);
        });
    }

    @Transactional
    public void finalizeDeduction(Long hospitalId, Long medicalItemId, Integer quantity) {
        requirePositiveQuantity(quantity);
        inventoryRepository.findByHospitalIdAndMedicalItemIdForUpdate(hospitalId, medicalItemId).ifPresent(inv -> {
            inv.setReservedQuantity(Math.max(0, inv.getReservedQuantity() - quantity));
            inv.setUpdatedAt(LocalDateTime.now());
            inventoryRepository.save(inv);
        });
    }

    public boolean hasReservedStock(Long hospitalId, Long medicalItemId, Integer quantity) {
        if (quantity == null || quantity <= 0) return false;
        return inventoryRepository.findByHospitalIdAndMedicalItemId(hospitalId, medicalItemId)
                .map(inventory -> inventory.getReservedQuantity() != null
                        && inventory.getReservedQuantity() >= quantity)
                .orElse(false);
    }

    @Transactional
    public boolean hasReservedStockForUpdate(Long hospitalId, Long medicalItemId, Integer quantity) {
        if (quantity == null || quantity <= 0) return false;
        return inventoryRepository.findByHospitalIdAndMedicalItemIdForUpdate(hospitalId, medicalItemId)
                .map(inventory -> inventory.getReservedQuantity() != null
                        && inventory.getReservedQuantity() >= quantity)
                .orElse(false);
    }

    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Inventory quantity must be positive");
        }
    }

    public InventoryDto toDto(HospitalInventory inv) {
        InventoryDto dto = new InventoryDto();
        dto.setId(inv.getId());
        dto.setHospitalId(inv.getHospital().getId());
        dto.setHospitalName(inv.getHospital().getName());
        dto.setMedicalItemId(inv.getMedicalItem().getId());
        dto.setMedicalItemCode(inv.getMedicalItem().getCode());
        dto.setMedicalItemName(inv.getMedicalItem().getName());
        dto.setMedicalItemCategory(inv.getMedicalItem().getCategory());
        dto.setUnit(inv.getMedicalItem().getUnit());
        dto.setAvailableQuantity(inv.getAvailableQuantity());
        dto.setReservedQuantity(inv.getReservedQuantity());
        dto.setExpiryDate(inv.getExpiryDate());
        dto.setStatus(inv.getStatus());
        dto.setUpdatedAt(inv.getUpdatedAt());
        return dto;
    }
}
