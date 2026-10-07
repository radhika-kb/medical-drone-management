package com.medicaldrone.payment;

import org.springframework.stereotype.Service;

/**
 * Strategy/Factory resolver for Payment Gateways (SOLID: Open/Closed & Dependency Inversion)
 */
@Service
public class PaymentGatewayResolver {

    private final RazorpayPaymentGateway razorpayPaymentGateway;

    public PaymentGatewayResolver(RazorpayPaymentGateway razorpayPaymentGateway) {
        this.razorpayPaymentGateway = razorpayPaymentGateway;
    }

    /**
     * Resolves the primary gateway (defaults to Razorpay if available/configured, otherwise Sandbox)
     */
    public PaymentGateway getPrimaryGateway() {
        return razorpayPaymentGateway;
    }

    /**
     * Production payment processing uses Razorpay only. A client cannot select a simulator.
     */
    public PaymentGateway getGateway(String name) {
        return getPrimaryGateway();
    }
}
