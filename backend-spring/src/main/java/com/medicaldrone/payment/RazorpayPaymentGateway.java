package com.medicaldrone.payment;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

/**
 * Razorpay Payment Gateway Implementation
 * Adheres to Single Responsibility (SRP) and Open/Closed Principle (OCP).
 */
@Component("razorpayPaymentGateway")
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    @Value("${razorpay.key-id:rzp_test_placeholder}")
    private String keyId;

    @Value("${razorpay.key-secret:placeholder_secret}")
    private String keySecret;

    @Value("${razorpay.currency:INR}")
    private String currency;

    @Value("${razorpay.company-name:Bengaluru Medical Drone Network}")
    private String companyName;

    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    @Override
    public String getGatewayName() {
        return "RAZORPAY";
    }

    @Override
    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && !keyId.contains("placeholder")
                && keySecret != null && !keySecret.isBlank() && !keySecret.contains("placeholder");
    }

    @Override
    public PaymentGatewayOrder createOrder(double amountInRupees, String receipt, String notes) {
        // Razorpay accepts amounts in paise (1 INR = 100 paise)
        long amountInPaise = BigDecimal.valueOf(amountInRupees)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        if (!isConfigured()) {
            throw new IllegalStateException("Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET before creating a payment order.");
        }
        try {
                RazorpayClient client = new RazorpayClient(keyId, keySecret);
                JSONObject orderRequest = new JSONObject();
                orderRequest.put("amount", amountInPaise);
                orderRequest.put("currency", currency);
                orderRequest.put("receipt", receipt);
                if (notes != null && !notes.isBlank()) {
                    JSONObject notesJson = new JSONObject();
                    notesJson.put("purpose", notes);
                    orderRequest.put("notes", notesJson);
                }

                Order razorpayOrder = client.orders.create(orderRequest);
                String orderId = razorpayOrder.get("id");
                String status = razorpayOrder.get("status");

                log.info("Successfully created Razorpay Order: {} for receipt: {}, amount: ₹{}", orderId, receipt, amountInRupees);
                return new PaymentGatewayOrder(orderId, amountInRupees, amountInPaise, currency, receipt, keyId, status);

        } catch (Exception e) {
            log.error("Error creating Razorpay order: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Razorpay order", e);
        }
    }

    @Override
    public boolean verifyPayment(PaymentVerificationRequest request) {
        if (!isConfigured() || request.getGatewayOrderId() == null || request.getGatewayPaymentId() == null
                || request.getGatewaySignature() == null || request.getExpectedAmountInPaise() == null) return false;
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.getGatewayOrderId());
            attributes.put("razorpay_payment_id", request.getGatewayPaymentId());
            attributes.put("razorpay_signature", request.getGatewaySignature());

            if (!Utils.verifyPaymentSignature(attributes, keySecret)) return false;

            Payment payment = client.payments.fetch(request.getGatewayPaymentId());
            return request.getGatewayOrderId().equals(payment.get("order_id"))
                    && "captured".equalsIgnoreCase(Objects.toString(payment.get("status"), ""))
                    && request.getExpectedAmountInPaise().longValue() == Long.parseLong(payment.get("amount").toString())
                    && request.getExpectedCurrency().equalsIgnoreCase(Objects.toString(payment.get("currency"), ""));
        } catch (Exception e) {
            log.error("Razorpay signature verification failed: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank() || rawBody == null || signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            log.error("Razorpay webhook signature verification failed", e);
            return false;
        }
    }

    public String getKeyId() {
        return keyId;
    }

    public String getCompanyName() {
        return companyName;
    }
}
