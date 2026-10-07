package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_requests")
public class MedicalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_code", nullable = false, unique = true)
    private String requestCode;

    @Column(name = "medical_type", nullable = false)
    private String medicalType = "BLOOD"; // BLOOD, ORGANS, VACCINES, MEDICINE, ANTIVENOM, DEFIBRILLATOR

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medical_item_id")
    private MedicalItem medicalItem;

    @Column(nullable = false)
    private String urgency = "HIGH"; // CRITICAL, HIGH, MEDIUM, LOW

    @Column(name = "weight_kg", nullable = false)
    private Double weightKg = 1.0;

    @Column(nullable = false)
    private Integer quantity = 1;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requesting_hospital_id")
    private MedicalCenter requestingHospital;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplying_hospital_id")
    private MedicalCenter supplyingHospital;

    // Kept populated for databases created from the older schema, where this
    // legacy column was mandatory and represented the requesting hospital.
    @Column(name = "center_id")
    private Long centerId;

    @Column(name = "required_by")
    private LocalDateTime requiredBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "pickup_lat")
    private Double pickupLat;

    @Column(name = "pickup_lng")
    private Double pickupLng;

    @Column(name = "dest_lat", nullable = false)
    private Double destLat;

    @Column(name = "dest_lng", nullable = false)
    private Double destLng;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id")
    private DroneZone zone;

    @Column(nullable = false)
    private String status = "PENDING"; 
    // DRAFT, PENDING, MATCHING_SUPPLIER, WAITING_SUPPLIER_RESPONSE, SUPPLIER_ACCEPTED, SUPPLIER_REJECTED, 
    // NO_SUPPLIER_AVAILABLE, ALLOCATING_RESOURCE, ROUTE_PLANNED, SAFETY_CHECK, PAYMENT_PENDING, 
    // READY_FOR_DISPATCH, DISPATCHED, IN_TRANSIT, DELIVERED, DELIVERY_CONFIRMED, CANCELLED, FAILED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MedicalRequest() {}

    // Backward compatibility helper: getCenter returns requestingHospital
    public MedicalCenter getCenter() {
        return requestingHospital;
    }

    public void setCenter(MedicalCenter center) {
        this.requestingHospital = center;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestCode() { return requestCode; }
    public void setRequestCode(String requestCode) { this.requestCode = requestCode; }

    public String getMedicalType() { return medicalType; }
    public void setMedicalType(String medicalType) { this.medicalType = medicalType; }

    public MedicalItem getMedicalItem() { return medicalItem; }
    public void setMedicalItem(MedicalItem medicalItem) { this.medicalItem = medicalItem; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public MedicalCenter getRequestingHospital() { return requestingHospital; }
    public void setRequestingHospital(MedicalCenter requestingHospital) { this.requestingHospital = requestingHospital; }

    public MedicalCenter getSupplyingHospital() { return supplyingHospital; }
    public void setSupplyingHospital(MedicalCenter supplyingHospital) { this.supplyingHospital = supplyingHospital; }

    public Long getCenterId() { return centerId; }
    public void setCenterId(Long centerId) { this.centerId = centerId; }

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

    public DroneZone getZone() { return zone; }
    public void setZone(DroneZone zone) { this.zone = zone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
