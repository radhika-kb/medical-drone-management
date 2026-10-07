package com.medicaldrone.payment;

/**
 * Payment Gateway Contract (SOLID: Interface Segregation & Open/Closed Principle)
 * Allows plugging in Razorpay, Stripe, Mock sandbox or custom payment providers seamlessly.
 */
public interface PaymentGateway {

    /**
     * Unique identifier/name of the gateway (e.g. "RAZORPAY", "MOCK")
     */
    String getGatewayName();

    /**
     * Creates an order at the payment gateway
     * @param amountInRupees Amount in standard Indian Rupees
     * @param receipt Internal receipt / reference code
     * @param notes Optional notes / metadata
     * @return PaymentGatewayOrder details including gateway orderId and client keyId
     */
    PaymentGatewayOrder createOrder(double amountInRupees, String receipt, String notes);

    /**
     * Validates cryptographic signature or webhook proof of successful transaction
     * @param request Verification details (orderId, paymentId, signature)
     * @return true if valid and authentic, false otherwise
     */
    boolean verifyPayment(PaymentVerificationRequest request);

    /**
     * Checks if this gateway is properly configured with live/test API keys
     */
    boolean isConfigured();
}
