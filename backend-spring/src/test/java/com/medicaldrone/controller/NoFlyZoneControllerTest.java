package com.medicaldrone.controller;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NoFlyZoneControllerTest {
    @Test
    void legacyCirclesAreNotReturnedAsVerifiedAirspace() {
        NoFlyZoneController controller = new NoFlyZoneController();
        assertTrue(controller.getAllNoFlyZones().getBody().isEmpty());
    }

    @Test
    void pointCheckReportsUnavailableRatherThanClear() {
        NoFlyZoneController controller = new NoFlyZoneController();
        var result = controller.checkCoordinate(12.9716, 77.5946).getBody();
        assertEquals(false, result.get("available"));
        assertNull(result.get("violated"));
        assertFalse(result.get("message").toString().contains("clear"));
    }
}
