package com.medicaldrone.controller;

import com.medicaldrone.dto.PaymentDto;
import com.medicaldrone.payment.RazorpayPaymentGateway;
import com.medicaldrone.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.medicaldrone.security.UserPrincipal;
import com.medicaldrone.repository.MedicalRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;
    private final RazorpayPaymentGateway razorpayGateway;
    private final MedicalRequestRepository requestRepository;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService,
                             RazorpayPaymentGateway razorpayGateway,
                             MedicalRequestRepository requestRepository,
                             ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.razorpayGateway = razorpayGateway;
        this.requestRepository = requestRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getPaymentConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("gateway", razorpayGateway.getGatewayName());
        config.put("keyId", razorpayGateway.getKeyId());
        config.put("isConfigured", razorpayGateway.isConfigured());
        config.put("companyName", razorpayGateway.getCompanyName());
        config.put("currency", "INR");
        return ResponseEntity.ok(config);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB')")
    public ResponseEntity<List<PaymentDto>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/request/{requestId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL')")
    public ResponseEntity<?> getPaymentsByRequest(@PathVariable Long requestId, @AuthenticationPrincipal UserPrincipal principal) {
        var request = requestRepository.findById(requestId).orElse(null);
        if (request == null) return ResponseEntity.notFound().build();
        Long requesterId = request.getRequestingHospital() != null ? request.getRequestingHospital().getId()
                : request.getCenter() != null ? request.getCenter().getId() : null;
        if (!isGlobalRole(principal) && (requesterId == null || !requesterId.equals(principal.getHospitalId()))) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(paymentService.getPaymentsByRequest(requestId));
    }

    @GetMapping("/hospital/{hospitalId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL')")
    public ResponseEntity<?> getPaymentsByHospital(@PathVariable Long hospitalId, @AuthenticationPrincipal UserPrincipal principal) {
        if (!isGlobalRole(principal) && !hospitalId.equals(principal.getHospitalId())) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(paymentService.getPaymentsByHospital(hospitalId));
    }

    @PostMapping("/create-order")
    @PreAuthorize("hasAnyRole('ADMIN', 'REQUESTING_HOSPITAL')")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> payload, @AuthenticationPrincipal UserPrincipal principal) {
        Long requestId = Long.valueOf(payload.get("requestId").toString());
        var request = requestRepository.findById(requestId).orElse(null);
        if (request == null) return ResponseEntity.notFound().build();
        Long requesterId = request.getRequestingHospital() != null ? request.getRequestingHospital().getId()
                : request.getCenter() != null ? request.getCenter().getId() : null;
        if (!isGlobalRole(principal) && (requesterId == null || !requesterId.equals(principal.getHospitalId()))) return ResponseEntity.status(403).build();
        if (!razorpayGateway.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of("message", "Razorpay is not configured. Set test or live key credentials on the backend."));
        }
        try {
            return ResponseEntity.ok(paymentService.createPaymentOrder(requestId, "RAZORPAY", "RAZORPAY"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(502).body(Map.of("message", "Razorpay could not create the payment order."));
        }
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'REQUESTING_HOSPITAL')")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, Object> payload,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        String reference = payload.get("paymentReference").toString();
        var payment = paymentService.getByPaymentReference(reference);
        if (payment.isEmpty()) return ResponseEntity.notFound().build();
        if (!isGlobalRole(principal) && (principal.getHospitalId() == null
                || !principal.getHospitalId().equals(payment.get().getPayerHospitalId()))) return ResponseEntity.status(403).build();
        String orderId = payload.get("gatewayOrderId") != null ? payload.get("gatewayOrderId").toString() : null;
        String paymentId = payload.get("gatewayPaymentId") != null ? payload.get("gatewayPaymentId").toString() : null;
        String signature = payload.get("gatewaySignature") != null ? payload.get("gatewaySignature").toString() : null;
        try {
            return ResponseEntity.ok(paymentService.verifyAndCompletePayment(reference, orderId, paymentId, signature, "RAZORPAY"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> razorpayWebhook(@RequestBody String rawBody,
                                             @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        if (!razorpayGateway.verifyWebhookSignature(rawBody, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Razorpay webhook signature");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText();
            JsonNode payment = root.path("payload").path("payment").path("entity");
            String orderId = payment.path("order_id").asText(null);
            String paymentId = payment.path("id").asText(null);
            String currency = payment.path("currency").asText(null);
            long amount = payment.path("amount").asLong(-1);
            if (orderId == null || paymentId == null || amount < 0 || currency == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay webhook payment details are incomplete");
            }
            if ("payment.captured".equals(event)) {
                paymentService.completeCapturedWebhook(orderId, paymentId, amount, currency);
            } else if ("payment.failed".equals(event)) {
                String reason = payment.path("error_description").asText("Razorpay reported a failed payment");
                paymentService.failPaymentWebhook(orderId, paymentId, amount, currency, reason);
            }
            return ResponseEntity.ok(Map.of("received", true));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay webhook payload");
        }
    }

    private boolean isGlobalRole(UserPrincipal principal) {
        return principal != null && principal.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_CENTRAL_HUB".equals(authority.getAuthority()));
    }
}
