package com.medicaldrone.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock Sandbox Payment Gateway
 * Implements PaymentGateway for offline testing and continuous integration without live credentials.
 */
@Component("mockPaymentGateway")
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public String getGatewayName() {
        return "MOCK_SANDBOX";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public PaymentGatewayOrder createOrder(double amountInRupees, String receipt, String notes) {
        String orderId = "order_mock_" + UUID.randomUUID().toString().substring(0, 10);
        long amountInPaise = Math.round(amountInRupees * 100);
        log.info("Mock Gateway: Created simulated order {} for ₹{}", orderId, amountInRupees);
        return new PaymentGatewayOrder(orderId, amountInRupees, amountInPaise, "INR", receipt, "mock_key_test", "created");
    }

    @Override
    public boolean verifyPayment(PaymentVerificationRequest request) {
        log.info("Mock Gateway: Simulated successful payment verification for ref: {}", request.getPaymentReference());
        return true;
    }
}
