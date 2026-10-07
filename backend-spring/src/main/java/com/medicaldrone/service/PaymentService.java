package com.medicaldrone.service;

import com.medicaldrone.dto.PaymentDto;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.Payment;
import com.medicaldrone.payment.PaymentGateway;
import com.medicaldrone.payment.PaymentGatewayOrder;
import com.medicaldrone.payment.PaymentGatewayResolver;
import com.medicaldrone.payment.PaymentVerificationRequest;
import com.medicaldrone.repository.MedicalRequestRepository;
import com.medicaldrone.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Payment Service orchestrating financial transactions and status updates.
 * Adheres to SOLID: Decoupled from concrete payment providers via PaymentGateway interface.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final MedicalRequestRepository medicalRequestRepository;
    private final PricingService pricingService;
    private final PaymentGatewayResolver paymentGatewayResolver;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository,
                          MedicalRequestRepository medicalRequestRepository,
                          PricingService pricingService,
                          PaymentGatewayResolver paymentGatewayResolver,
                          SimpMessagingTemplate messagingTemplate,
                          AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.medicalRequestRepository = medicalRequestRepository;
        this.pricingService = pricingService;
        this.paymentGatewayResolver = paymentGatewayResolver;
        this.messagingTemplate = messagingTemplate;
        this.auditService = auditService;
    }

    public List<PaymentDto> getPaymentsByRequest(Long requestId) {
        return paymentRepository.findByRequestId(requestId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<PaymentDto> getPaymentsByHospital(Long hospitalId) {
        return paymentRepository.findByPayerHospitalId(hospitalId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<PaymentDto> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Optional<PaymentDto> getByPaymentReference(String reference) {
        return paymentRepository.findByPaymentReference(reference).map(this::toDto);
    }

    @Transactional
    public PaymentDto createPaymentOrder(Long requestId, String paymentMethod, String preferredGateway) {
        MedicalRequest request = medicalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Medical request not found: " + requestId));

        if (!"SUPPLIER_ACCEPTED".equals(request.getStatus()) && !"PAYMENT_FAILED".equals(request.getStatus())
                && !"PAYMENT_PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("A supplier must accept the request before payment can be started");
        }
        if (request.getSupplyingHospital() == null || request.getMedicalItem() == null) {
            throw new IllegalStateException("The accepted request is missing its supplier or medical item");
        }
        if ("PAYMENT_PENDING".equals(request.getStatus())) {
            Payment pending = paymentRepository.findFirstByRequestIdAndPaymentStatusOrderByCreatedAtDesc(requestId, "PENDING")
                    .orElseThrow(() -> new IllegalStateException("Payment is pending; refresh payment status before retrying"));
            return toOrderDto(pending);
        }
        if ("PAYMENT_FAILED".equals(request.getStatus())) {
            Payment previousAttempt = paymentRepository.findFirstByRequestIdAndPaymentStatusOrderByCreatedAtDesc(requestId, "FAILED")
                    .orElse(null);
            if (previousAttempt != null) {
                previousAttempt.setPaymentStatus("PENDING");
                paymentRepository.save(previousAttempt);
                request.setStatus("PAYMENT_PENDING");
                request.setUpdatedAt(LocalDateTime.now());
                medicalRequestRepository.save(request);
                return toOrderDto(previousAttempt);
            }
        }

        MedicalCenter payer = request.getRequestingHospital();
        if (payer == null) {
            payer = request.getCenter();
        }

        double amount = pricingService.calculatePrice(request, distanceKm(request.getPickupLat(), request.getPickupLng(),
                request.getDestLat(), request.getDestLng()));
        String ref = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PaymentGateway gateway = paymentGatewayResolver.getPrimaryGateway();
        PaymentGatewayOrder gatewayOrder = gateway.createOrder(
                amount,
                ref,
                "Medical Delivery Request " + request.getRequestCode()
        );

        Payment payment = new Payment(ref, request, payer, amount, paymentMethod != null ? paymentMethod : "RAZORPAY");
        payment.setGatewayOrderId(gatewayOrder.getOrderId());
        payment.setPaymentStatus("PENDING");

        Payment saved = paymentRepository.save(payment);

        request.setStatus("PAYMENT_PENDING");
        request.setUpdatedAt(LocalDateTime.now());
        medicalRequestRepository.save(request);

        PaymentDto dto = toOrderDto(saved);
        dto.setRazorpayKeyId(gatewayOrder.getKeyId());
        dto.setAmountInPaise(gatewayOrder.getAmountInPaise());

        messagingTemplate.convertAndSend("/topic/payments", dto);
        return dto;
    }

    @Transactional
    public PaymentDto verifyAndCompletePayment(String paymentReference,
                                              String gatewayOrderId,
                                              String gatewayPaymentId,
                                              String gatewaySignature,
                                              String gatewayName) {
        Payment payment = paymentRepository.findByPaymentReferenceForUpdate(paymentReference)
                .orElseThrow(() -> new RuntimeException("Payment reference not found: " + paymentReference));

        if ("SUCCESS".equalsIgnoreCase(payment.getPaymentStatus())) {
            if (gatewayOrderId != null && !gatewayOrderId.equals(payment.getGatewayOrderId())
                    || gatewayPaymentId != null && !gatewayPaymentId.equals(payment.getGatewayPaymentId())) {
                throw new IllegalStateException("This payment reference has already been completed with different gateway details");
            }
            return toDto(payment);
        }
        if (!List.of("PENDING", "FAILED").contains(payment.getPaymentStatus().toUpperCase())) {
            throw new IllegalStateException("This payment is not awaiting verification");
        }
        if (gatewayOrderId == null || !gatewayOrderId.equals(payment.getGatewayOrderId())
                || gatewayPaymentId == null || gatewaySignature == null) {
            throw new IllegalArgumentException("Razorpay order, payment, and signature fields are required");
        }

        PaymentGateway gateway = paymentGatewayResolver.getPrimaryGateway();
        PaymentVerificationRequest verifyReq = new PaymentVerificationRequest(
                paymentReference,
                gatewayOrderId,
                gatewayPaymentId,
                gatewaySignature,
                BigDecimal.valueOf(payment.getAmount()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue(),
                payment.getCurrency()
        );

        boolean isValid = gateway.verifyPayment(verifyReq);
        if (!isValid) {
            log.warn("Payment signature verification failed for reference: {}", paymentReference);
            throw new IllegalArgumentException("Razorpay could not verify this captured payment. The request remains unpaid.");
        }
        return markSuccessful(payment, gatewayPaymentId);
    }

    @Transactional
    public PaymentDto completeCapturedWebhook(String gatewayOrderId, String gatewayPaymentId, long amountInPaise, String currency) {
        Payment payment = paymentRepository.findByGatewayOrderIdForUpdate(gatewayOrderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment matches the Razorpay order"));
        long expectedAmount = BigDecimal.valueOf(payment.getAmount()).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).longValue();
        if (amountInPaise != expectedAmount || !payment.getCurrency().equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("Webhook payment amount or currency does not match the stored order");
        }
        if ("SUCCESS".equalsIgnoreCase(payment.getPaymentStatus())) return toDto(payment);
        if (!List.of("PENDING", "FAILED").contains(payment.getPaymentStatus().toUpperCase())) {
            throw new IllegalStateException("The stored payment is not pending");
        }
        return markSuccessful(payment, gatewayPaymentId);
    }

    @Transactional
    public PaymentDto failPaymentWebhook(String gatewayOrderId, String gatewayPaymentId, long amountInPaise,
                                         String currency, String failureReason) {
        Payment payment = paymentRepository.findByGatewayOrderIdForUpdate(gatewayOrderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment matches the Razorpay order"));
        long expectedAmount = BigDecimal.valueOf(payment.getAmount()).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).longValue();
        if (amountInPaise != expectedAmount || !payment.getCurrency().equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("Webhook payment amount or currency does not match the stored order");
        }
        if ("SUCCESS".equalsIgnoreCase(payment.getPaymentStatus()) || "FAILED".equalsIgnoreCase(payment.getPaymentStatus())) {
            return toDto(payment);
        }
        payment.setGatewayPaymentId(gatewayPaymentId);
        return processPaymentFailure(payment.getPaymentReference(), failureReason);
    }

    @Transactional
    public PaymentDto processPaymentFailure(String paymentReference, String failureReason) {
        Payment payment = paymentRepository.findByPaymentReferenceForUpdate(paymentReference)
                .orElseThrow(() -> new RuntimeException("Payment reference not found: " + paymentReference));

        if ("SUCCESS".equalsIgnoreCase(payment.getPaymentStatus())) return toDto(payment);
        if (!"PENDING".equalsIgnoreCase(payment.getPaymentStatus())) return toDto(payment);
        payment.setPaymentStatus("FAILED");
        Payment saved = paymentRepository.save(payment);

        MedicalRequest request = payment.getRequest();
        if ("PAYMENT_PENDING".equals(request.getStatus())) request.setStatus("PAYMENT_FAILED");
        request.setUpdatedAt(LocalDateTime.now());
        medicalRequestRepository.save(request);

        PaymentDto dto = toDto(saved);
        messagingTemplate.convertAndSend("/topic/payments", dto);
        return dto;
    }

    private PaymentDto markSuccessful(Payment payment, String gatewayPaymentId) {
        payment.setPaymentStatus("SUCCESS");
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setPaidAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        auditService.record(null, "PAYMENT_GATEWAY", "PAYMENT_SUCCESS", "payments", saved.getId(),
                "reference=" + saved.getPaymentReference() + "; gatewayPaymentId=" + gatewayPaymentId);

        MedicalRequest request = payment.getRequest();
        if (!List.of("PAYMENT_PENDING", "PAYMENT_FAILED").contains(request.getStatus())) {
            throw new IllegalStateException("The request is no longer awaiting payment");
        }
        request.setStatus("READY_FOR_DISPATCH");
        request.setUpdatedAt(LocalDateTime.now());
        medicalRequestRepository.save(request);

        PaymentDto dto = toDto(saved);
        messagingTemplate.convertAndSend("/topic/payments", dto);
        Map<String, Object> requestUpdate = Map.of(
                "id", request.getId(),
                "requestId", request.getId(),
                "status", request.getStatus());
        messagingTemplate.convertAndSend("/topic/requests/" + request.getId(), requestUpdate);
        messagingTemplate.convertAndSend("/topic/requests", requestUpdate);
        return dto;
    }

    private PaymentDto toOrderDto(Payment payment) {
        PaymentDto dto = toDto(payment);
        dto.setRazorpayKeyId(paymentGatewayResolver.getPrimaryGateway() instanceof com.medicaldrone.payment.RazorpayPaymentGateway razorpay
                ? razorpay.getKeyId() : null);
        return dto;
    }

    private double distanceKm(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            throw new IllegalStateException("Both supplier and requester locations must be confirmed before pricing");
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public PaymentDto toDto(Payment p) {
        PaymentDto dto = new PaymentDto();
        dto.setId(p.getId());
        dto.setPaymentReference(p.getPaymentReference());
        dto.setRequestId(p.getRequest().getId());
        dto.setRequestCode(p.getRequest().getRequestCode());
        if (p.getPayerHospital() != null) {
            dto.setPayerHospitalId(p.getPayerHospital().getId());
            dto.setPayerHospitalName(p.getPayerHospital().getName());
        }
        dto.setAmount(p.getAmount());
        dto.setAmountInPaise(Math.round(p.getAmount() * 100));
        dto.setCurrency(p.getCurrency());
        dto.setPaymentMethod(p.getPaymentMethod());
        dto.setPaymentStatus(p.getPaymentStatus());
        dto.setGatewayOrderId(p.getGatewayOrderId());
        dto.setGatewayPaymentId(p.getGatewayPaymentId());
        dto.setPaidAt(p.getPaidAt());
        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }
}
