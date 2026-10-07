package com.medicaldrone.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicaldrone.model.*;
import com.medicaldrone.payment.PaymentGatewayOrder;
import com.medicaldrone.payment.RazorpayPaymentGateway;
import com.medicaldrone.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Opt-in HTTP E2E for an isolated MySQL schema, real Redis, and a real AI service.
 * Set RUN_LIVE_STACK_E2E=true and E2E_DB_NAME/E2E_DB_PASSWORD to enable it.
 * Only the external Razorpay SDK boundary is replaced in this test context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "medical-drone.mission-simulation.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_STACK_E2E", matches = "true")
class LiveStackWorkflowE2ETest {
    private static final String TEST_PASSWORD = "E2E-Only-Password-9531";

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper mapper;
    @Autowired private DroneZoneRepository zoneRepository;
    @Autowired private MedicalCenterRepository centerRepository;
    @Autowired private MedicalItemRepository itemRepository;
    @Autowired private HospitalInventoryRepository inventoryRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;
    @Autowired private MedicalRequestRepository requestRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MissionRepository missionRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private AgentDecisionRepository agentDecisionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private WeatherObservationRepository weatherObservationRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @MockBean private RazorpayPaymentGateway razorpayGateway;

    @AfterAll
    static void dropDisposableDatabase() throws Exception {
        String database = System.getenv("E2E_DB_NAME");
        String password = System.getenv("E2E_DB_PASSWORD");
        if (database == null || !database.matches("medical_drone_e2e_[a-z0-9]+") || password == null) return;
        try (var connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true", "root", password);
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
        }
    }

    @DynamicPropertySource
    static void useIsolatedLiveServices(DynamicPropertyRegistry properties) {
        String database = Objects.requireNonNull(System.getenv("E2E_DB_NAME"), "E2E_DB_NAME is required");
        String password = Objects.requireNonNull(System.getenv("E2E_DB_PASSWORD"), "E2E_DB_PASSWORD is required");
        String aiUrl = Optional.ofNullable(System.getenv("E2E_AI_URL")).orElse("http://127.0.0.1:8000");
        properties.add("spring.profiles.active", () -> "mysql");
        properties.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:3306/" + database
                + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata&characterEncoding=UTF-8");
        properties.add("spring.datasource.username", () -> "root");
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        properties.add("spring.data.redis.host", () -> "127.0.0.1");
        properties.add("spring.data.redis.port", () -> "6379");
        properties.add("ai.service.url", () -> aiUrl);
        properties.add("ai.service.workflow-timeout-seconds", () -> "45");
        properties.add("app.jwt.secret", () -> "e2e-only-jwt-secret-not-for-any-deployment-2026");
        properties.add("razorpay.key-id", () -> "rzp_test_placeholder");
        properties.add("razorpay.key-secret", () -> "placeholder_secret");
        properties.add("logging.level.root", () -> "WARN");
        properties.add("logging.level.org.hibernate.SQL", () -> "OFF");
        properties.add("logging.level.com.medicaldrone", () -> "INFO");
    }

    @Test
    void requestToHumanApprovedDispatchUsesLiveMysqlRedisAiAndHttpControllers() throws Exception {
        String base = "http://localhost:" + port;
        String marker = "E2E-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        AtomicReference<StompSession> stomp = new AtomicReference<>();
        AtomicReference<JsonNode> readyRequestPayload = new AtomicReference<>();
        ExecutorService dispatchPool = Executors.newFixedThreadPool(2);

        try {
            StompSession websocket = connectWebSocket(base);
            stomp.set(websocket);
            CountDownLatch requestEvent = new CountDownLatch(1);
            CountDownLatch readyRequestEvent = new CountDownLatch(1);
            CountDownLatch paymentReadyEvent = new CountDownLatch(1);
            CountDownLatch hubReviewEvent = new CountDownLatch(1);
            CountDownLatch missionDispatchEvent = new CountDownLatch(1);
            websocket.subscribe("/topic/requests", jsonSubscription(requestEvent, "requestId", null));
            websocket.subscribe("/topic/requests", jsonSubscription(readyRequestEvent, "status", "READY_FOR_DISPATCH", readyRequestPayload));
            websocket.subscribe("/topic/payments", jsonSubscription(paymentReadyEvent, "paymentStatus", "SUCCESS"));
            websocket.subscribe("/topic/missions", jsonSubscription(hubReviewEvent, "status", "CENTRAL_HUB_REVIEW"));
            websocket.subscribe("/topic/missions", jsonSubscription(missionDispatchEvent, "status", "DISPATCHED"));
            assertEquals("PONG", redisTemplate.getConnectionFactory().getConnection().ping());

            String adminToken = login(base, "admin@meddrone.in", "admin123");
            String hubToken = login(base, "operator@meddrone.in", "operator123");
            DroneZone south = zoneRepository.findByName("SOUTH_ZONE").orElseThrow();
            MedicalCenter requester = centerRepository.save(new MedicalCenter(marker + " requester", "HOSPITAL",
                    marker + " test address A", 12.8200, 77.6400, south, marker));
            MedicalCenter supplier = centerRepository.save(new MedicalCenter(marker + " supplier", "BLOOD_BANK",
                    marker + " test address B", 12.8320, 77.6520, south, marker));

            String requesterEmail = marker.toLowerCase(Locale.ROOT) + ".requester@example.test";
            String supplierEmail = marker.toLowerCase(Locale.ROOT) + ".supplier@example.test";
            String requesterToken = register(base, marker + " Requester", requesterEmail, "ROLE_REQUESTING_HOSPITAL", requester.getId());
            String supplierToken = register(base, marker + " Supplier", supplierEmail, "ROLE_SUPPLYING_HOSPITAL", supplier.getId());
            MedicalItem item = itemRepository.findByCode("MED-BLD-OPOS").orElseThrow();
            HospitalInventory stock = inventoryRepository.save(new HospitalInventory(supplier, item, 10, 0,
                    LocalDate.now().plusDays(30)));
            addTestPricingRule(marker);

            ResponseEntity<JsonNode> requestResponse = post(base + "/api/requests", requesterToken, Map.of(
                    "requestingHospitalId", requester.getId(), "medicalItemId", item.getId(),
                    "medicalType", "BLOOD", "urgency", "HIGH", "weightKg", 1.0,
                    "quantity", 2, "notes", marker + " disposable E2E workflow"), JsonNode.class);
            assertEquals(HttpStatus.OK, requestResponse.getStatusCode(), body(requestResponse));
            long requestId = requestResponse.getBody().path("id").asLong();
            assertTrue(requestEvent.await(5, TimeUnit.SECONDS), "request WebSocket update was not received");
            assertEquals("WAITING_SUPPLIER_RESPONSE", requestRepository.findById(requestId).orElseThrow().getStatus());
            assertFalse(agentDecisionRepository.findByRequestIdOrderByCreatedAtAsc(requestId).isEmpty(),
                    "Priority and supply matching decisions should be persisted");

            ResponseEntity<JsonNode> unauthorizedAccept = post(base + "/api/supply-responses/respond", requesterToken,
                    Map.of("requestId", requestId, "decision", "ACCEPTED", "offeredQuantity", 2), JsonNode.class);
            assertEquals(HttpStatus.FORBIDDEN, unauthorizedAccept.getStatusCode());
            ResponseEntity<JsonNode> supplierInvitations = get(base + "/api/medical-requests/supplier/inbox",
                    supplierToken, JsonNode.class);
            assertEquals(HttpStatus.OK, supplierInvitations.getStatusCode());
            assertTrue(containsId(supplierInvitations.getBody(), requestId), "Supplier inbox should contain the new invitation");

            ResponseEntity<JsonNode> accepted = post(base + "/api/supply-responses/respond", supplierToken,
                    Map.of("requestId", requestId, "decision", "ACCEPTED", "offeredQuantity", 2), JsonNode.class);
            assertEquals(HttpStatus.OK, accepted.getStatusCode(), body(accepted));
            MedicalRequest acceptedRequest = requestRepository.findById(requestId).orElseThrow();
            assertEquals("SUPPLIER_ACCEPTED", acceptedRequest.getStatus());
            HospitalInventory reserved = inventoryRepository.findByHospitalIdAndMedicalItemId(supplier.getId(), item.getId()).orElseThrow();
            assertEquals(8, reserved.getAvailableQuantity());
            assertEquals(2, reserved.getReservedQuantity());
            assertNull(redisTemplate.opsForValue().get("lock:inventory:" + supplier.getId() + ":" + item.getId()),
                    "Redis reservation lock should be released after the inventory transaction");
            ResponseEntity<JsonNode> inventoryView = get(base + "/api/inventory/hospital/" + supplier.getId(), supplierToken, JsonNode.class);
            assertEquals(HttpStatus.OK, inventoryView.getStatusCode());
            assertEquals(2, findInventoryRow(inventoryView.getBody(), item.getId()).path("reservedQuantity").asInt());

            when(razorpayGateway.isConfigured()).thenReturn(true);
            when(razorpayGateway.getGatewayName()).thenReturn("E2E_MOCK");
            when(razorpayGateway.getKeyId()).thenReturn("e2e_mock_public_key");
            when(razorpayGateway.getCompanyName()).thenReturn("E2E Test Only");
            when(razorpayGateway.createOrder(anyDouble(), anyString(), anyString())).thenAnswer(invocation -> {
                double amount = invocation.getArgument(0);
                long paise = Math.round(amount * 100);
                return new PaymentGatewayOrder("order_" + marker, amount, paise, "INR", marker,
                        "e2e_mock_public_key", "created");
            });
            when(razorpayGateway.verifyPayment(any())).thenReturn(false, true);

            ResponseEntity<JsonNode> order = post(base + "/api/payments/create-order", requesterToken,
                    Map.of("requestId", requestId), JsonNode.class);
            assertEquals(HttpStatus.OK, order.getStatusCode(), body(order));
            String paymentReference = order.getBody().path("paymentReference").asText();
            String orderId = order.getBody().path("gatewayOrderId").asText();
            assertEquals("PENDING", paymentRepository.findByPaymentReference(paymentReference).orElseThrow().getPaymentStatus());

            ResponseEntity<JsonNode> prematureReview = post(base + "/api/requests/" + requestId + "/review", hubToken, Map.of(), JsonNode.class);
            assertFalse(prematureReview.getStatusCode().is2xxSuccessful(), "unverified frontend payment must not unlock review");
            ResponseEntity<JsonNode> invalidVerification = post(base + "/api/payments/verify", requesterToken,
                    Map.of("paymentReference", paymentReference, "gatewayOrderId", orderId,
                            "gatewayPaymentId", "e2e_payment_" + marker, "gatewaySignature", "invalid"), JsonNode.class);
            assertEquals(HttpStatus.BAD_REQUEST, invalidVerification.getStatusCode(), body(invalidVerification));
            assertEquals("PAYMENT_PENDING", requestRepository.findById(requestId).orElseThrow().getStatus());
            assertEquals("PENDING", paymentRepository.findByPaymentReference(paymentReference).orElseThrow().getPaymentStatus());

            Map<String, Object> validCallback = Map.of("paymentReference", paymentReference, "gatewayOrderId", orderId,
                    "gatewayPaymentId", "e2e_payment_" + marker, "gatewaySignature", "valid-test-signature");
            ResponseEntity<JsonNode> verified = post(base + "/api/payments/verify", requesterToken, validCallback, JsonNode.class);
            assertEquals(HttpStatus.OK, verified.getStatusCode(), body(verified));
            ResponseEntity<JsonNode> duplicateCallback = post(base + "/api/payments/verify", requesterToken, validCallback, JsonNode.class);
            assertEquals(HttpStatus.OK, duplicateCallback.getStatusCode());
            assertTrue(paymentReadyEvent.await(5, TimeUnit.SECONDS), "payment WebSocket success event was not received");
            assertEquals("SUCCESS", paymentRepository.findByPaymentReference(paymentReference).orElseThrow().getPaymentStatus());
            assertEquals("READY_FOR_DISPATCH", requestRepository.findById(requestId).orElseThrow().getStatus());
            assertTrue(readyRequestEvent.await(5, TimeUnit.SECONDS), "READY_FOR_DISPATCH WebSocket update was not received");
            assertEquals(requestId, readyRequestPayload.get().path("id").asLong());
            assertEquals(requestId, readyRequestPayload.get().path("requestId").asLong());
            assertEquals("READY_FOR_DISPATCH", readyRequestPayload.get().path("status").asText());
            verify(razorpayGateway, times(2)).verifyPayment(any());

            ResponseEntity<JsonNode> review = post(base + "/api/requests/" + requestId + "/review", hubToken, Map.of(), JsonNode.class);
            assertEquals(HttpStatus.OK, review.getStatusCode(), body(review));
            JsonNode missionDto = review.getBody().path("mission");
            long missionId = missionDto.path("id").asLong();
            assertEquals("CENTRAL_HUB_REVIEW", requestRepository.findById(requestId).orElseThrow().getStatus());
            assertEquals("CENTRAL_HUB_REVIEW", missionRepository.findById(missionId).orElseThrow().getStatus());
            assertTrue(routeRepository.findByMissionId(missionId).orElseThrow().getWaypoints().size() >= 2);
            assertTrue(routeRepository.findByMissionId(missionId).orElseThrow().getFinalEtaMinutes() > 0);
            assertTrue(missionDto.path("decisionTrail").size() > 0);
            assertTrue(weatherObservationRepository.findTopByOrderByObservedAtDesc().isPresent());
            assertTrue(hubReviewEvent.await(5, TimeUnit.SECONDS), "Central Hub review WebSocket event was not received");

            ResponseEntity<JsonNode> requesterDispatch = post(base + "/api/missions/" + missionId + "/hub-decision", requesterToken,
                    Map.of("action", "DISPATCH", "reason", marker), JsonNode.class);
            ResponseEntity<JsonNode> supplierDispatch = post(base + "/api/missions/" + missionId + "/hub-decision", supplierToken,
                    Map.of("action", "DISPATCH", "reason", marker), JsonNode.class);
            ResponseEntity<JsonNode> anonymousDispatch = post(base + "/api/missions/" + missionId + "/hub-decision", null,
                    Map.of("action", "DISPATCH", "reason", marker), JsonNode.class);
            assertEquals(HttpStatus.FORBIDDEN, requesterDispatch.getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN, supplierDispatch.getStatusCode());
            assertTrue(Set.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN).contains(anonymousDispatch.getStatusCode()));

            CountDownLatch start = new CountDownLatch(1);
            Callable<ResponseEntity<JsonNode>> dispatchCall = () -> {
                start.await(5, TimeUnit.SECONDS);
                return post(base + "/api/missions/" + missionId + "/hub-decision", hubToken,
                        Map.of("action", "DISPATCH", "reason", marker + " Central Hub approval"), JsonNode.class);
            };
            Future<ResponseEntity<JsonNode>> dispatchA = dispatchPool.submit(dispatchCall);
            Future<ResponseEntity<JsonNode>> dispatchB = dispatchPool.submit(dispatchCall);
            start.countDown();
            List<ResponseEntity<JsonNode>> dispatchResults = List.of(dispatchA.get(30, TimeUnit.SECONDS), dispatchB.get(30, TimeUnit.SECONDS));
            assertEquals(1, dispatchResults.stream().filter(result -> result.getStatusCode().is2xxSuccessful()).count());
            assertEquals(1, dispatchResults.stream().filter(result -> result.getStatusCode() == HttpStatus.CONFLICT).count());
            assertTrue(missionDispatchEvent.await(5, TimeUnit.SECONDS), "mission dispatch WebSocket event was not received");
            assertEquals("DISPATCHED", missionRepository.findById(missionId).orElseThrow().getStatus());
            assertEquals("DISPATCHED", requestRepository.findById(requestId).orElseThrow().getStatus());

            Set<String> actions = new HashSet<>();
            auditLogRepository.findAll().forEach(log -> actions.add(log.getAction()));
            assertTrue(actions.containsAll(Set.of("REQUEST_CREATED", "SUPPLIER_ACCEPTED", "INVENTORY_RESERVED",
                    "PAYMENT_SUCCESS", "DRONE_ASSIGNED", "MISSION_DISPATCHED")), "Missing audit action(s): " + actions);
            assertTrue(notificationRepository.findAll().stream().anyMatch(notification -> notification.getMission() != null
                    && missionId == notification.getMission().getId()), "Hub/dispatch notification was not persisted");
        } finally {
            dispatchPool.shutdownNow();
            if (stomp.get() != null && stomp.get().isConnected()) stomp.get().disconnect();
        }
    }

    private void addTestPricingRule(String marker) {
        PricingRule rule = new PricingRule();
        rule.setRuleCode(marker + "_BASE");
        rule.setRuleType("BASE");
        rule.setBaseFee(new BigDecimal("100.00"));
        rule.setPerKmRate(new BigDecimal("10.00"));
        rule.setPerKgRate(new BigDecimal("1.00"));
        rule.setCategoryFee(new BigDecimal("10.00"));
        rule.setUrgencyMultiplier(new BigDecimal("1.200"));
        rule.setPriority(1000);
        rule.setActive(true);
        pricingRuleRepository.save(rule);
    }

    private String login(String base, String email, String password) {
        ResponseEntity<JsonNode> response = post(base + "/api/auth/login", null,
                Map.of("email", email, "password", password), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), body(response));
        return response.getBody().path("accessToken").asText();
    }

    private String register(String base, String name, String email, String role, Long hospitalId) {
        ResponseEntity<JsonNode> response = post(base + "/api/auth/register", null,
                Map.of("name", name, "email", email, "password", TEST_PASSWORD, "role", role, "hospitalId", hospitalId), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), body(response));
        return response.getBody().path("accessToken").asText();
    }

    private <T> ResponseEntity<T> post(String url, String token, Object payload, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, headers), responseType);
    }

    private <T> ResponseEntity<T> get(String url, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private StompSession connectWebSocket(String base) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        client.start();
        String url = base.replace("http://", "ws://") + "/ws";
        return client.connectAsync(url, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
    }

    private StompFrameHandler jsonSubscription(CountDownLatch latch, String field, String expected) {
        return jsonSubscription(latch, field, expected, null);
    }

    private StompFrameHandler jsonSubscription(CountDownLatch latch, String field, String expected,
                                               AtomicReference<JsonNode> capturedPayload) {
        return new StompFrameHandler() {
            @Override public java.lang.reflect.Type getPayloadType(StompHeaders headers) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                JsonNode json = (JsonNode) payload;
                if (expected == null || expected.equals(json.path(field).asText())) {
                    if (capturedPayload != null) capturedPayload.set(json);
                    latch.countDown();
                }
            }
        };
    }

    private JsonNode findInventoryRow(JsonNode rows, Long medicalItemId) {
        for (JsonNode row : rows) if (row.path("medicalItemId").asLong() == medicalItemId) return row;
        fail("Inventory response did not contain item " + medicalItemId);
        return mapper.createObjectNode();
    }

    private boolean containsId(JsonNode rows, long id) {
        for (JsonNode row : rows) if (row.path("id").asLong() == id) return true;
        return false;
    }

    private String body(ResponseEntity<?> response) {
        return response.getBody() == null ? "<empty response>" : response.getBody().toString();
    }
}
