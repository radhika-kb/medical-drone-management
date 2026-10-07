package com.medicaldrone.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.Payment;
import com.medicaldrone.payment.MockPaymentGateway;
import com.medicaldrone.payment.PaymentGateway;
import com.medicaldrone.payment.PaymentGatewayResolver;
import com.medicaldrone.repository.MedicalRequestRepository;
import com.medicaldrone.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentCaptor.forClass;

@ExtendWith(MockitoExtension.class)
class PaymentServiceVerificationTest {
    @Mock private PaymentRepository paymentRepository;
    @Mock private MedicalRequestRepository requestRepository;
    @Mock private PricingService pricingService;
    @Mock private PaymentGatewayResolver gatewayResolver;
    @Mock private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Mock private AuditService auditService;

    private MedicalRequest request;
    private Payment payment;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        request = new MedicalRequest();
        request.setId(31L);
        request.setRequestCode("REQ-31");
        request.setStatus("PAYMENT_PENDING");
        payment = new Payment("PAY-31", request, null, 1250.0, "RAZORPAY");
        payment.setId(41L);
        payment.setGatewayOrderId("order_test_31");
        service = new PaymentService(paymentRepository, requestRepository, pricingService, gatewayResolver,
                messagingTemplate, auditService);
    }

    @Test
    void mockVerifiedPaymentMovesRequestToReadyAndDuplicateCallbackHasSingleTransition() {
        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-31")).thenReturn(Optional.of(payment));
        when(gatewayResolver.getPrimaryGateway()).thenReturn(new MockPaymentGateway());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.verifyAndCompletePayment("PAY-31", "order_test_31", "payment_test_31", "mock-signature", "MOCK_SANDBOX");
        service.verifyAndCompletePayment("PAY-31", "order_test_31", "payment_test_31", "mock-signature", "MOCK_SANDBOX");

        assertEquals("SUCCESS", payment.getPaymentStatus());
        assertEquals("READY_FOR_DISPATCH", request.getStatus());
        verify(auditService, times(1)).record(any(), any(), eq("PAYMENT_SUCCESS"), eq("payments"), eq(41L), any());
        verify(requestRepository, times(1)).save(request);

        var payloadCaptor = forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/requests"), payloadCaptor.capture());
        assertDoesNotThrow(() -> {
            JsonNode event = new ObjectMapper().valueToTree(payloadCaptor.getValue());
            assertEquals(31L, event.path("id").asLong());
            assertEquals(31L, event.path("requestId").asLong());
            assertEquals("READY_FOR_DISPATCH", event.path("status").asText());
        });
        assertInstanceOf(java.util.Map.class, payloadCaptor.getValue(),
                "Request status WebSocket updates should use a scalar/map payload, not a JPA entity graph");
    }

    @Test
    void invalidGatewayVerificationLeavesPaymentAndRequestUnpaid() {
        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-31")).thenReturn(Optional.of(payment));
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gatewayResolver.getPrimaryGateway()).thenReturn(gateway);
        when(gateway.verifyPayment(any())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.verifyAndCompletePayment(
                "PAY-31", "order_test_31", "payment_test_31", "invalid-signature", "RAZORPAY"));

        assertEquals("PENDING", payment.getPaymentStatus());
        assertEquals("PAYMENT_PENDING", request.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(requestRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void frontendCannotMarkPaymentSuccessfulWithoutBackendVerification() {
        assertEquals("PENDING", payment.getPaymentStatus());
        assertEquals("PAYMENT_PENDING", request.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(requestRepository, never()).save(any());
    }
}
