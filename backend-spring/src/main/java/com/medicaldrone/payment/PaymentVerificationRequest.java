package com.medicaldrone.payment;

public class PaymentVerificationRequest {
    private String paymentReference;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String gatewaySignature;
    private Long expectedAmountInPaise;
    private String expectedCurrency;

    public PaymentVerificationRequest() {}

    public PaymentVerificationRequest(String paymentReference, String gatewayOrderId, String gatewayPaymentId, String gatewaySignature) {
        this.paymentReference = paymentReference;
        this.gatewayOrderId = gatewayOrderId;
        this.gatewayPaymentId = gatewayPaymentId;
        this.gatewaySignature = gatewaySignature;
    }

    public PaymentVerificationRequest(String paymentReference, String gatewayOrderId, String gatewayPaymentId,
                                     String gatewaySignature, Long expectedAmountInPaise, String expectedCurrency) {
        this(paymentReference, gatewayOrderId, gatewayPaymentId, gatewaySignature);
        this.expectedAmountInPaise = expectedAmountInPaise;
        this.expectedCurrency = expectedCurrency;
    }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public String getGatewayOrderId() { return gatewayOrderId; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }

    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }

    public String getGatewaySignature() { return gatewaySignature; }
    public void setGatewaySignature(String gatewaySignature) { this.gatewaySignature = gatewaySignature; }

    public Long getExpectedAmountInPaise() { return expectedAmountInPaise; }
    public void setExpectedAmountInPaise(Long expectedAmountInPaise) { this.expectedAmountInPaise = expectedAmountInPaise; }

    public String getExpectedCurrency() { return expectedCurrency; }
    public void setExpectedCurrency(String expectedCurrency) { this.expectedCurrency = expectedCurrency; }
}
