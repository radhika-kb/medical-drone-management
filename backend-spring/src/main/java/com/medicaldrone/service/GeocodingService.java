package com.medicaldrone.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.medicaldrone.dto.GeocodeResult;
import com.medicaldrone.model.DroneZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GeocodingService {
    private static final double MIN_LAT = 12.7;
    private static final double MAX_LAT = 13.3;
    private static final double MIN_LNG = 77.3;
    private static final double MAX_LNG = 78.0;

    private final RestClient restClient;
    private final ZoneService zoneService;
    private final String providerUrl;
    private final String userAgent;
    private final ConcurrentHashMap<String, List<GeocodeResult>> cache = new ConcurrentHashMap<>();
    private long lastRequestAt;

    public GeocodingService(RestClient.Builder builder, ZoneService zoneService,
                            @Value("${app.geocoding.provider-url}") String providerUrl,
                            @Value("${app.geocoding.user-agent}") String userAgent) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = builder.requestFactory(requestFactory).build();
        this.zoneService = zoneService;
        this.providerUrl = providerUrl;
        this.userAgent = userAgent;
    }

    public List<GeocodeResult> geocode(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank() || rawAddress.length() > 300) {
            throw new IllegalArgumentException("Enter a valid hospital address (up to 300 characters)");
        }
        String address = rawAddress.trim();
        String cacheKey = address.toLowerCase(Locale.ROOT);
        List<GeocodeResult> cached = cache.get(cacheKey);
        if (cached != null) return cached;

        URI uri = UriComponentsBuilder.fromUriString(providerUrl)
                .queryParam("q", address + ", Bengaluru, Karnataka, India")
                .queryParam("format", "jsonv2")
                .queryParam("limit", 5)
                .queryParam("countrycodes", "in")
                .queryParam("viewbox", "77.3,13.3,78.0,12.7")
                .queryParam("bounded", 1)
                .build().encode().toUri();

        JsonNode response;
        try {
            respectRateLimit();
            response = restClient.get().uri(uri)
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                    .retrieve().body(JsonNode.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Address lookup is temporarily unavailable. Please try again later.", e);
        }

        if (response == null || !response.isArray()) return List.of();
        List<GeocodeResult> results = new java.util.ArrayList<>();
        for (JsonNode item : response) {
            try {
                double lat = Double.parseDouble(item.path("lat").asText());
                double lng = Double.parseDouble(item.path("lon").asText());
                if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) continue;
                DroneZone zone = zoneService.resolveZoneForCoordinates(lat, lng);
                if (zone == null) continue;
                results.add(new GeocodeResult(item.path("display_name").asText(address), lat, lng,
                        zone.getId(), zone.getName()));
            } catch (NumberFormatException ignored) {
                // Ignore malformed results from the provider.
            }
        }
        List<GeocodeResult> result = List.copyOf(results);
        cache.put(cacheKey, result);
        return result;
    }

    private synchronized void respectRateLimit() {
        long waitMillis = 1_000 - (System.currentTimeMillis() - lastRequestAt);
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Address lookup was interrupted", e);
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }
}
