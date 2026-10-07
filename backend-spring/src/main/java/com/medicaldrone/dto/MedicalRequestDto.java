package com.medicaldrone.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class MedicalRequestDto {

    public static class Create {
        private Long centerId; // requesting hospital id
        private Long requestingHospitalId;
        private Long supplyingHospitalId;
        private Long medicalItemId;
        private String medicalType = "BLOOD"; // BLOOD, ORGANS, VACCINES, MEDICINE, ANTIVENOM, DEFIBRILLATOR
        @NotNull
        private String urgency = "HIGH"; // CRITICAL, HIGH, MEDIUM, LOW
        @NotNull
        private Double weightKg = 1.0;
        private Integer quantity = 1;
        private LocalDateTime requiredBy;
        private String notes;
        private Double pickupLat;
        private Double pickupLng;
        private Double destLat;
        private Double destLng;

        public Long getCenterId() { return requestingHospitalId != null ? requestingHospitalId : centerId; }
        public void setCenterId(Long centerId) { this.centerId = centerId; this.requestingHospitalId = centerId; }
        public Long getRequestingHospitalId() { return requestingHospitalId != null ? requestingHospitalId : centerId; }
        public void setRequestingHospitalId(Long requestingHospitalId) { this.requestingHospitalId = requestingHospitalId; this.centerId = requestingHospitalId; }
        public Long getSupplyingHospitalId() { return supplyingHospitalId; }
        public void setSupplyingHospitalId(Long supplyingHospitalId) { this.supplyingHospitalId = supplyingHospitalId; }
        public Long getMedicalItemId() { return medicalItemId; }
        public void setMedicalItemId(Long medicalItemId) { this.medicalItemId = medicalItemId; }
        public String getMedicalType() { return medicalType; }
        public void setMedicalType(String medicalType) { this.medicalType = medicalType; }
        public String getUrgency() { return urgency; }
        public void setUrgency(String urgency) { this.urgency = urgency; }
        public Double getWeightKg() { return weightKg; }
        public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public LocalDateTime getRequiredBy() { return requiredBy; }
        public void setRequiredBy(LocalDateTime requiredBy) { this.requiredBy = requiredBy; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public Double getPickupLat() { return pickupLat; }
        public void setPickupLat(Double pickupLat) { this.pickupLat = pickupLat; }
        public Double getPickupLng() { return pickupLng; }
        public void setPickupLng(Double pickupLng) { this.pickupLng = pickupLng; }
        public Double getDestLat() { return destLat; }
        public void setDestLat(Double destLat) { this.destLat = destLat; }
        public Double getDestLng() { return destLng; }
        public void setDestLng(Double destLng) { this.destLng = destLng; }
    }

    public static class Response {
        private Long id;
        private String requestCode;
        private String medicalType;
        private Long medicalItemId;
        private String medicalItemName;
        private String urgency;
        private Double weightKg;
        private Integer quantity;
        private Long centerId;
        private String centerName;
        private Long requestingHospitalId;
        private String requestingHospitalName;
        private Long supplyingHospitalId;
        private String supplyingHospitalName;
        private LocalDateTime requiredBy;
        private String notes;
        private Double pickupLat;
        private Double pickupLng;
        private Double destLat;
        private Double destLng;
        private Long zoneId;
        private String zoneName;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getRequestCode() { return requestCode; }
        public void setRequestCode(String requestCode) { this.requestCode = requestCode; }
        public String getMedicalType() { return medicalType; }
        public void setMedicalType(String medicalType) { this.medicalType = medicalType; }
        public Long getMedicalItemId() { return medicalItemId; }
        public void setMedicalItemId(Long medicalItemId) { this.medicalItemId = medicalItemId; }
        public String getMedicalItemName() { return medicalItemName; }
        public void setMedicalItemName(String medicalItemName) { this.medicalItemName = medicalItemName; }
        public String getUrgency() { return urgency; }
        public void setUrgency(String urgency) { this.urgency = urgency; }
        public Double getWeightKg() { return weightKg; }
        public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Long getCenterId() { return centerId; }
        public void setCenterId(Long centerId) { this.centerId = centerId; }
        public String getCenterName() { return centerName; }
        public void setCenterName(String centerName) { this.centerName = centerName; }
        public Long getRequestingHospitalId() { return requestingHospitalId; }
        public void setRequestingHospitalId(Long requestingHospitalId) { this.requestingHospitalId = requestingHospitalId; }
        public String getRequestingHospitalName() { return requestingHospitalName; }
        public void setRequestingHospitalName(String requestingHospitalName) { this.requestingHospitalName = requestingHospitalName; }
        public Long getSupplyingHospitalId() { return supplyingHospitalId; }
        public void setSupplyingHospitalId(Long supplyingHospitalId) { this.supplyingHospitalId = supplyingHospitalId; }
        public String getSupplyingHospitalName() { return supplyingHospitalName; }
        public void setSupplyingHospitalName(String supplyingHospitalName) { this.supplyingHospitalName = supplyingHospitalName; }
        public LocalDateTime getRequiredBy() { return requiredBy; }
        public void setRequiredBy(LocalDateTime requiredBy) { this.requiredBy = requiredBy; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public Double getPickupLat() { return pickupLat; }
        public void setPickupLat(Double pickupLat) { this.pickupLat = pickupLat; }
        public Double getPickupLng() { return pickupLng; }
        public void setPickupLng(Double pickupLng) { this.pickupLng = pickupLng; }
        public Double getDestLat() { return destLat; }
        public void setDestLat(Double destLat) { this.destLat = destLat; }
        public Double getDestLng() { return destLng; }
        public void setDestLng(Double destLng) { this.destLng = destLng; }
        public Long getZoneId() { return zoneId; }
        public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
        public String getZoneName() { return zoneName; }
        public void setZoneName(String zoneName) { this.zoneName = zoneName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
