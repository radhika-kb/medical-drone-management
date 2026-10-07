package com.medicaldrone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_reference", nullable = false, unique = true)
    private String paymentReference;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "request_id", nullable = false)
    private MedicalRequest request;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payer_hospital_id", nullable = false)
    private MedicalCenter payerHospital;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(name = "payment_method")
    private String paymentMethod; // UPI, CREDIT_CARD, DEBIT_CARD, NET_BANKING

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus = "PENDING"; // PENDING, PROCESSING, SUCCESS, FAILED, REFUNDED

    @Column(name = "gateway_order_id")
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Payment() {}

    public Payment(String paymentReference, MedicalRequest request, MedicalCenter payerHospital, Double amount, String paymentMethod) {
        this.paymentReference = paymentReference;
        this.request = request;
        this.payerHospital = payerHospital;
        this.amount = amount;
        this.currency = "INR";
        this.paymentMethod = paymentMethod;
        this.paymentStatus = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public MedicalRequest getRequest() { return request; }
    public void setRequest(MedicalRequest request) { this.request = request; }

    public MedicalCenter getPayerHospital() { return payerHospital; }
    public void setPayerHospital(MedicalCenter payerHospital) { this.payerHospital = payerHospital; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

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

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
