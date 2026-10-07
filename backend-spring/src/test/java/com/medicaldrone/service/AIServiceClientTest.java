package com.medicaldrone.service;

import com.medicaldrone.dto.AIDecisionDto;
import com.medicaldrone.model.MedicalRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AIServiceClientTest {

    @Test
    void consumesAValidWorkflowRecommendation() throws Exception {
        try (TestServer server = new TestServer(200, "{\"status\":\"APPROVED\",\"weather\":{\"flight_condition\":\"SAFE\"}}", 0)) {
            AIServiceClient client = client(server, 3);
            AIDecisionDto.ResponsePayload response = client.getAIDecision(request(), java.util.List.of());
            assertEquals("APPROVED", response.getStatus());
            assertEquals("SAFE", response.getWeather().get("flight_condition"));
        }
    }

    @Test
    void turnsWorkflowTimeoutIntoSafeUnavailableFailure() throws Exception {
        try (TestServer server = new TestServer(200, "{\"status\":\"APPROVED\"}", 1500)) {
            AIServiceClient client = client(server, 1);
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> client.getAIDecision(request(), java.util.List.of()));
            assertTrue(error.getMessage().contains("unavailable"));
        }
    }

    @Test
    void reportsConnectionFailureWithoutInventingRecommendation() throws Exception {
        TestServer server = new TestServer(200, "{}", 0);
        String url = server.url();
        server.close();
        AIServiceClient client = new AIServiceClient(url, 1);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.getAIDecision(request(), java.util.List.of()));
        assertTrue(error.getMessage().contains("unavailable"));
    }

    @Test
    void reportsMalformedWorkflowResponse() throws Exception {
        try (TestServer server = new TestServer(200, "not-json", 0)) {
            AIServiceClient client = client(server, 3);
            assertThrows(IllegalStateException.class,
                    () -> client.getAIDecision(request(), java.util.List.of()));
        }
    }

    @Test
    void turnsMonitoringServiceFailureIntoCriticalUnavailableSignal() throws Exception {
        TestServer server = new TestServer(200, "{}", 0);
        String url = server.url();
        server.close();
        AIServiceClient client = new AIServiceClient(url, 1);

        var result = client.checkMissionMonitoring(19L, 2, 1, 10, 20, 2);

        assertEquals(false, result.get("monitoring_available"));
        assertEquals(true, result.get("is_anomaly"));
        assertEquals("CRITICAL", result.get("severity"));
        assertEquals("MONITORING_UNAVAILABLE", result.get("anomaly_type"));
    }

    private AIServiceClient client(TestServer server, long timeoutSeconds) {
        return new AIServiceClient(server.url(), timeoutSeconds);
    }

    private MedicalRequest request() {
        MedicalRequest request = new MedicalRequest();
        request.setId(19L);
        request.setRequestCode("REQ-TEST-19");
        request.setPickupLat(12.9);
        request.setPickupLng(77.5);
        request.setDestLat(12.91);
        request.setDestLng(77.52);
        return request;
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();

        TestServer(int status, String body, long delayMs) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/internal/ai/workflow", exchange -> {
                try {
                    if (delayMs > 0) Thread.sleep(delayMs);
                    byte[] response = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, response.length);
                    exchange.getResponseBody().write(response);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.createContext("/internal/ai/monitor", exchange -> {
                try {
                    if (delayMs > 0) Thread.sleep(delayMs);
                    byte[] response = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, response.length);
                    exchange.getResponseBody().write(response);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

        @Override public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
