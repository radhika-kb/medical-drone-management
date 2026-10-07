package com.medicaldrone.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class InventoryDto {
    private Long id;
    private Long hospitalId;
    private String hospitalName;
    private Long medicalItemId;
    private String medicalItemCode;
    private String medicalItemName;
    private String medicalItemCategory;
    private String unit;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private LocalDate expiryDate;
    private String status;
    private LocalDateTime updatedAt;

    public InventoryDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }

    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }

    public Long getMedicalItemId() { return medicalItemId; }
    public void setMedicalItemId(Long medicalItemId) { this.medicalItemId = medicalItemId; }

    public String getMedicalItemCode() { return medicalItemCode; }
    public void setMedicalItemCode(String medicalItemCode) { this.medicalItemCode = medicalItemCode; }

    public String getMedicalItemName() { return medicalItemName; }
    public void setMedicalItemName(String medicalItemName) { this.medicalItemName = medicalItemName; }

    public String getMedicalItemCategory() { return medicalItemCategory; }
    public void setMedicalItemCategory(String medicalItemCategory) { this.medicalItemCategory = medicalItemCategory; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }

    public Integer getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
