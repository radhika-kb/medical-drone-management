package com.medicaldrone.dto;

import java.time.LocalDateTime;

public class SupplyResponseDto {
    private Long id;
    private Long requestId;
    private String requestCode;
    private Long supplyingHospitalId;
    private String supplyingHospitalName;
    private Integer offeredQuantity;
    private String response; // ACCEPTED, REJECTED
    private String rejectionReason;
    private LocalDateTime respondedAt;

    public SupplyResponseDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getRequestCode() { return requestCode; }
    public void setRequestCode(String requestCode) { this.requestCode = requestCode; }

    public Long getSupplyingHospitalId() { return supplyingHospitalId; }
    public void setSupplyingHospitalId(Long supplyingHospitalId) { this.supplyingHospitalId = supplyingHospitalId; }

    public String getSupplyingHospitalName() { return supplyingHospitalName; }
    public void setSupplyingHospitalName(String supplyingHospitalName) { this.supplyingHospitalName = supplyingHospitalName; }

    public Integer getOfferedQuantity() { return offeredQuantity; }
    public void setOfferedQuantity(Integer offeredQuantity) { this.offeredQuantity = offeredQuantity; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
}
