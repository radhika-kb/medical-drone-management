package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hospital_inventory")
public class HospitalInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hospital_id", nullable = false)
    private MedicalCenter hospital;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medical_item_id", nullable = false)
    private MedicalItem medicalItem;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private String status = "AVAILABLE"; // AVAILABLE, LOW_STOCK, EXPIRED, RESERVED

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public HospitalInventory() {}

    public HospitalInventory(MedicalCenter hospital, MedicalItem medicalItem, Integer availableQuantity, Integer reservedQuantity, LocalDate expiryDate) {
        this.hospital = hospital;
        this.medicalItem = medicalItem;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.expiryDate = expiryDate;
        this.status = "AVAILABLE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MedicalCenter getHospital() { return hospital; }
    public void setHospital(MedicalCenter hospital) { this.hospital = hospital; }

    public MedicalItem getMedicalItem() { return medicalItem; }
    public void setMedicalItem(MedicalItem medicalItem) { this.medicalItem = medicalItem; }

    public Integer getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }

    public Integer getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
