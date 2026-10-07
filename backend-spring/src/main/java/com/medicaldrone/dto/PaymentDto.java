package com.medicaldrone.dto;

import java.time.LocalDateTime;

public class PaymentDto {
    private Long id;
    private String paymentReference;
    private Long requestId;
    private String requestCode;
    private Long payerHospitalId;
    private String payerHospitalName;
    private Double amount;
    private Long amountInPaise;
    private String currency;
    private String paymentMethod;
    private String paymentStatus;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String razorpayKeyId;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public PaymentDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getRequestCode() { return requestCode; }
    public void setRequestCode(String requestCode) { this.requestCode = requestCode; }

    public Long getPayerHospitalId() { return payerHospitalId; }
    public void setPayerHospitalId(Long payerHospitalId) { this.payerHospitalId = payerHospitalId; }

    public String getPayerHospitalName() { return payerHospitalName; }
    public void setPayerHospitalName(String payerHospitalName) { this.payerHospitalName = payerHospitalName; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Long getAmountInPaise() { return amountInPaise; }
    public void setAmountInPaise(Long amountInPaise) { this.amountInPaise = amountInPaise; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getGatewayOrderId() { return gatewayOrderId; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }

    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }

    public String getRazorpayKeyId() { return razorpayKeyId; }
    public void setRazorpayKeyId(String razorpayKeyId) { this.razorpayKeyId = razorpayKeyId; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
