package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "supply_responses")
public class SupplyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "request_id", nullable = false)
    private MedicalRequest request;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplying_hospital_id", nullable = false)
    private MedicalCenter supplyingHospital;

    @Column(name = "offered_quantity", nullable = false)
    private Integer offeredQuantity = 0;

    @Column(nullable = false)
    private String response; // ACCEPTED, REJECTED, PENDING

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public SupplyResponse() {}

    public SupplyResponse(MedicalRequest request, MedicalCenter supplyingHospital, Integer offeredQuantity, String response, String rejectionReason) {
        this.request = request;
        this.supplyingHospital = supplyingHospital;
        this.offeredQuantity = offeredQuantity;
        this.response = response;
        this.rejectionReason = rejectionReason;
        this.respondedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MedicalRequest getRequest() { return request; }
    public void setRequest(MedicalRequest request) { this.request = request; }

    public MedicalCenter getSupplyingHospital() { return supplyingHospital; }
    public void setSupplyingHospital(MedicalCenter supplyingHospital) { this.supplyingHospital = supplyingHospital; }

    public Integer getOfferedQuantity() { return offeredQuantity; }
    public void setOfferedQuantity(Integer offeredQuantity) { this.offeredQuantity = offeredQuantity; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
