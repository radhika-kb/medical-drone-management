package com.medicaldrone.controller;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AirspaceInformationControllerTest {
    @Test
    void exposesAaiClassificationAndDoesNotInventUnpublishedGeometry() {
        List<Map<String, Object>> rows = new AirspaceInformationController().list();
        Map<String, Object> vobl = rows.stream().filter(r -> r.get("name").toString().contains("Kempegowda"))
                .findFirst().orElseThrow();
        assertEquals("OTHER", vobl.get("classification"));
        assertEquals("ATS Class D", vobl.get("classificationDetail"));
        assertEquals("NOT_RENDERED_RESTRICTED_AREAS_EXCLUDED", vobl.get("geometryStatus"));
        assertFalse(vobl.containsKey("geometry"));

        Map<String, Object> hal = rows.stream().filter(r -> r.get("name").toString().contains("HAL"))
                .findFirst().orElseThrow();
        assertEquals("NOT_PUBLISHED_IN_AD_2_17", hal.get("geometryStatus"));
        assertFalse(hal.containsKey("geometry"));
    }
}
