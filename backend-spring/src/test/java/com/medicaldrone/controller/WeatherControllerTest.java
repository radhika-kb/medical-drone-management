package com.medicaldrone.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WeatherControllerTest {
    @Test
    void returnsLiveOpenMeteoObservationAndModelFieldsFromAiService() throws Exception {
        String body = """
                {"flight_condition":"CAUTION","reason":"Live Open-Meteo observation",
                 "observations":[{"source":"Open-Meteo","observed_at":"2026-10-06T10:00",
                  "temperature_c":29.1,"wind_speed_kmh":14.0,"visibility_km":8.0,
                  "flight_condition":"CAUTION","model_prediction":"CAUTION"}]}
                """;
        var response = invoke(body);
        assertEquals("LIVE", response.get("status"));
        Map<?, ?> data = (Map<?, ?>) response.get("data");
        assertEquals("Open-Meteo", ((Map<?, ?>)((java.util.List<?>)data.get("observations")).get(0)).get("source"));
        assertEquals("CAUTION", ((Map<?, ?>)((java.util.List<?>)data.get("observations")).get(0)).get("model_prediction"));
    }

    @Test
    void emptyWeatherPayloadBecomesUnavailable() throws Exception {
        assertEquals("UNAVAILABLE", invoke("{\"observations\":[],\"flight_condition\":\"UNSAFE\"}").get("status"));
    }

    private Map<?, ?> invoke(String responseBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/ai/weather", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            return (Map<?, ?>) new WeatherController("http://127.0.0.1:" + server.getAddress().getPort())
                    .current(12.9716, 77.5946).getBody();
        } finally { server.stop(0); }
    }
}
