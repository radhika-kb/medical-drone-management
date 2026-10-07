package com.medicaldrone.payment;

public class PaymentGatewayOrder {
    private String orderId;
    private double amount;
    private long amountInPaise;
    private String currency;
    private String receipt;
    private String keyId;
    private String status;

    public PaymentGatewayOrder() {}

    public PaymentGatewayOrder(String orderId, double amount, long amountInPaise, String currency, String receipt, String keyId, String status) {
        this.orderId = orderId;
        this.amount = amount;
        this.amountInPaise = amountInPaise;
        this.currency = currency;
        this.receipt = receipt;
        this.keyId = keyId;
        this.status = status;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public long getAmountInPaise() { return amountInPaise; }
    public void setAmountInPaise(long amountInPaise) { this.amountInPaise = amountInPaise; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getReceipt() { return receipt; }
    public void setReceipt(String receipt) { this.receipt = receipt; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
